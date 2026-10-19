package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * DB-backed queue worker (F7). Claims PENDING rows with FOR UPDATE SKIP LOCKED (safe with several
 * app instances), throttles per sender (phone_numbers.max_mps, token bucket) and dispatches each
 * message on a virtual thread, at most cbwa.queue.worker-threads at a time.
 */
@Slf4j
@Component
public class MessageQueueWorker {

    private static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final MessageDispatcher dispatcher;
    private final MessageQueueItemRepository queueRepository;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final Semaphore permits;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final String workerId;
    private volatile Instant lastStaleReset = Instant.EPOCH;

    public MessageQueueWorker(JdbcTemplate jdbc, MessageDispatcher dispatcher, MessageQueueItemRepository queueRepository,
                              PlatformTransactionManager txManager, AppProperties props) {
        this.jdbc = jdbc;
        this.dispatcher = dispatcher;
        this.queueRepository = queueRepository;
        this.tx = new TransactionTemplate(txManager);
        this.batchSize = Math.max(1, props.getQueue().getBatchSize());
        this.permits = new Semaphore(Math.max(1, props.getQueue().getWorkerThreads()));
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "worker";
        }
        this.workerId = (host + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private record Claim(Long id, Long phoneNumberId, int maxMps) {
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void tick() {
        try {
            int n;
            do {
                n = runOnce();
            } while (n >= batchSize);
        } catch (RuntimeException e) {
            log.error("Queue worker iteration failed", e);
        }
    }

    /** One claim + dispatch round. Returns the number of rows claimed. Public for tests. */
    public int runOnce() {
        Instant now = Instant.now();
        if (lastStaleReset.isBefore(now.minusSeconds(60))) {
            lastStaleReset = now;
            Integer reset = tx.execute(s -> queueRepository.resetStale(now.minus(STALE_AFTER), now));
            if (reset != null && reset > 0) {
                log.warn("Reset {} stale queue rows to PENDING", reset);
            }
        }
        List<Claim> claimed = tx.execute(s -> claim(now));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }
        List<Long> deferred = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (Claim c : claimed) {
            if (!bucket(c).tryConsume(1)) {
                deferred.add(c.id());
                continue;
            }
            futures.add(executor.submit(() -> {
                try {
                    permits.acquire();
                    try {
                        dispatcher.dispatch(c.id());
                    } finally {
                        permits.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    log.error("Dispatch of queue row {} failed", c.id(), e);
                }
            }));
        }
        if (!deferred.isEmpty()) {
            tx.executeWithoutResult(s -> defer(deferred));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Dispatch task failed", e);
            }
        }
        return claimed.size();
    }

    private List<Claim> claim(Instant now) {
        List<Claim> rows = jdbc.query("""
                        SELECT q.id, q.phone_number_id, p.max_mps FROM message_queue q
                        JOIN phone_numbers p ON p.id = q.phone_number_id
                        WHERE q.status = 'PENDING' AND q.next_attempt_at <= ?
                        ORDER BY q.priority, q.id LIMIT ? FOR UPDATE OF q SKIP LOCKED""",
                (rs, i) -> new Claim(rs.getLong(1), rs.getLong(2), rs.getInt(3)), Timestamp.from(now), batchSize);
        if (rows.isEmpty()) {
            return rows;
        }
        String ids = String.join(",", rows.stream().map(c -> c.id().toString()).toList());
        jdbc.update("UPDATE message_queue SET status = 'PROCESSING', locked_by = ?, locked_at = ?, updated_at = ? WHERE id IN (" + ids + ")",
                workerId, Timestamp.from(now), Timestamp.from(now));
        return rows;
    }

    private void defer(List<Long> ids) {
        Instant next = Instant.now().plusSeconds(1);
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        jdbc.update("UPDATE message_queue SET status = 'PENDING', locked_by = NULL, locked_at = NULL, next_attempt_at = ?, updated_at = ? WHERE id IN (" + in + ")",
                Timestamp.from(next), Timestamp.from(Instant.now()));
    }

    private Bucket bucket(Claim c) {
        int mps = Math.max(1, c.maxMps());
        return buckets.computeIfAbsent(c.phoneNumberId() + ":" + mps, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(mps).refillGreedy(mps, Duration.ofSeconds(1)).build()).build());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
