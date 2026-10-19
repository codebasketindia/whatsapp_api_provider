package in.codebasket.cbwa.partnerhook;

import in.codebasket.cbwa.partner.PartnerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Turns PartnerEvents into delivery rows (after the publishing transaction commits) and delivers
 * due rows every 5 seconds with retries (1m, 5m, 15m, 1h, 6h, then DEAD).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerWebhookDispatcher {

    private static final int BATCH = 100;
    /** A claimed row is hidden from other dispatch runs for this long while it is being delivered. */
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final PartnerWebhookService service;
    private final WebhookDeliveryLogRepository logRepository;
    private final TransactionTemplate tx;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPartnerEvent(PartnerEvent event) {
        try {
            service.enqueue(event);
        } catch (RuntimeException e) {
            log.warn("Could not queue partner webhook event {} for partner {}: {}", event.type(), event.partnerId(), e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void scheduled() {
        dispatchDue();
    }

    /** Claims due rows (SKIP LOCKED + lease) then delivers each one in its own transaction. */
    public int dispatchDue() {
        List<Long> ids = tx.execute(s -> {
            Instant now = Instant.now();
            List<Long> due = logRepository.lockDueIds(now, BATCH);
            if (!due.isEmpty()) {
                logRepository.lease(due, now.plus(LEASE));
            }
            return due;
        });
        if (ids == null) {
            return 0;
        }
        for (Long id : ids) {
            try {
                service.deliver(id);
            } catch (RuntimeException e) {
                log.warn("Partner webhook delivery {} failed unexpectedly: {}", id, e.getMessage());
            }
        }
        return ids.size();
    }
}
