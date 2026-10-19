package in.codebasket.cbwa.webhook;

import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores raw Meta webhook payloads (idempotent by SHA-256 of the body - Meta sends no event id)
 * and processes them asynchronously. Each log row is processed in one transaction while it is
 * row-locked (FOR UPDATE SKIP LOCKED), so concurrent processors never handle it twice. Failures
 * are retried by the scheduler up to 5 times.
 */
@Slf4j
@Service
public class WebhookInboundProcessor {

    public static final String SOURCE_META = "META";
    private static final int MAX_ATTEMPTS = 5;

    private final WebhookInboundLogRepository logRepository;
    private final MetaWebhookHandler handler;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final boolean asyncEnabled;

    public WebhookInboundProcessor(WebhookInboundLogRepository logRepository, MetaWebhookHandler handler, JdbcTemplate jdbc,
                                   PlatformTransactionManager txManager, @Value("${cbwa.webhook.async:true}") boolean asyncEnabled) {
        this.logRepository = logRepository;
        this.handler = handler;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.asyncEnabled = asyncEnabled;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    /** Returns the new log id, or null when this exact payload was already received. */
    public Long record(byte[] raw) {
        String eventId = HashUtil.sha256Hex(raw);
        if (logRepository.findByEventId(eventId).isPresent()) {
            return null;
        }
        String body = new String(raw, StandardCharsets.UTF_8);
        JsonNode payload = JsonUtil.readTree(body);
        WebhookInboundLog l = new WebhookInboundLog();
        l.setSource(SOURCE_META);
        l.setEventId(eventId);
        l.setSignatureValid(true);
        l.setObjectType(trim(payload.path("object").asText(null), 64));
        l.setWabaId(trim(payload.path("entry").path(0).path("id").asText(null), 32));
        Set<String> fields = new LinkedHashSet<>();
        payload.path("entry").forEach(e -> e.path("changes").forEach(c -> fields.add(c.path("field").asText())));
        l.setFields(trim(String.join(",", fields), 255));
        l.setPayload(body);
        l.setStatus(InboundLogStatus.RECEIVED);
        l.setAttempts(0);
        l.setReceivedAt(Instant.now());
        try {
            return logRepository.saveAndFlush(l).getId();
        } catch (DataIntegrityViolationException e) {
            return null; // concurrent duplicate delivery
        }
    }

    @Async
    public void processAsync(Long logId) {
        process(logId);
    }

    /** Processes one log row; safe to call concurrently and repeatedly. */
    public void process(Long logId) {
        try {
            tx.executeWithoutResult(s -> {
                List<Long> locked = jdbc.queryForList(
                        "SELECT id FROM webhook_inbound_logs WHERE id = ? AND status IN ('RECEIVED','FAILED') FOR UPDATE SKIP LOCKED",
                        Long.class, logId);
                if (locked.isEmpty()) {
                    return;
                }
                WebhookInboundLog l = logRepository.findById(logId).orElseThrow();
                boolean handled = handler.handle(JsonUtil.readTree(l.getPayload()));
                l.setStatus(handled ? InboundLogStatus.PROCESSED : InboundLogStatus.IGNORED);
                l.setAttempts(l.getAttempts() + 1);
                l.setProcessedAt(Instant.now());
                l.setError(null);
                logRepository.save(l);
            });
        } catch (RuntimeException e) {
            log.warn("Webhook log {} processing failed: {}", logId, e.toString());
            tx.executeWithoutResult(s -> logRepository.findById(logId).ifPresent(l -> {
                l.setAttempts(l.getAttempts() + 1);
                l.setStatus(InboundLogStatus.FAILED);
                l.setError(trim(e.toString(), 1000));
                logRepository.save(l);
            }));
        }
    }

    /** Picks up rows not processed asynchronously (crash, restart) and retries failures. */
    @Scheduled(fixedDelay = 2000, initialDelay = 10_000)
    public void processPending() {
        List<WebhookInboundLog> pending = logRepository.findTop100ByStatusInAndAttemptsLessThanOrderByIdAsc(
                List.of(InboundLogStatus.RECEIVED, InboundLogStatus.FAILED), MAX_ATTEMPTS);
        List<Long> ids = new ArrayList<>();
        Instant notBefore = Instant.now().minusSeconds(5);
        for (WebhookInboundLog l : pending) {
            // give the async processor a moment; retry failures with a growing delay
            Instant last = l.getStatus() == InboundLogStatus.FAILED ? l.getReceivedAt().plusSeconds(30L * l.getAttempts() * l.getAttempts())
                    : l.getReceivedAt();
            if (!asyncEnabled || (l.getStatus() == InboundLogStatus.RECEIVED ? last.isBefore(notBefore) : last.isBefore(Instant.now()))) {
                ids.add(l.getId());
            }
        }
        ids.forEach(this::process);
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.substring(0, Math.min(max, s.length()));
    }
}
