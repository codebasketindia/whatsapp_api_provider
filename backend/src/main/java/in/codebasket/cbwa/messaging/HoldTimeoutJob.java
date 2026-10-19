package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Settles holds that never received a final status (CHARGING.hold_timeout_hours, default 72):
 * messages that reached Meta (wamid + SENT or later) are charged, everything else is refunded and
 * removed from the queue so it can never be sent without a hold.
 */
@Slf4j
@Component
public class HoldTimeoutJob {

    private final MessageRepository messageRepository;
    private final MessageQueueItemRepository queueRepository;
    private final ChargingService chargingService;
    private final MessageStatusService statusService;
    private final SettingsService settings;
    private final TransactionTemplate tx;

    public HoldTimeoutJob(MessageRepository messageRepository, MessageQueueItemRepository queueRepository,
                          ChargingService chargingService, MessageStatusService statusService, SettingsService settings,
                          PlatformTransactionManager txManager) {
        this.messageRepository = messageRepository;
        this.queueRepository = queueRepository;
        this.chargingService = chargingService;
        this.statusService = statusService;
        this.settings = settings;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60_000L)
    public void run() {
        int hours = Math.max(1, settings.getInt(SettingKeys.CHARGING, SettingKeys.HOLD_TIMEOUT_HOURS, 72));
        Instant before = Instant.now().minus(Duration.ofHours(hours));
        int settled = 0;
        while (true) {
            List<Long> ids = messageRepository.findIdsByChargeStatusBefore(ChargeStatus.HELD, before, PageRequest.of(0, 200));
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                try {
                    tx.executeWithoutResult(s -> settle(id));
                    settled++;
                } catch (RuntimeException e) {
                    log.error("Could not settle hold for message {}", id, e);
                    return;
                }
            }
        }
        if (settled > 0) {
            log.info("Hold timeout job settled {} message holds", settled);
        }
    }

    void settle(Long messageId) {
        Message m = messageRepository.lockById(messageId).orElseThrow();
        if (m.getChargeStatus() != ChargeStatus.HELD) {
            return;
        }
        MessageStatus old = m.getStatus();
        if (m.getWamid() != null && ChargingService.rank(m.getStatus()) >= ChargingService.rank(MessageStatus.SENT)) {
            chargingService.finalizeCharge(m);
        } else {
            queueRepository.findByMessageId(m.getId()).ifPresent(q -> {
                if (q.getStatus() == QueueStatus.PENDING || q.getStatus() == QueueStatus.PROCESSING) {
                    q.setStatus(QueueStatus.DEAD);
                    q.setLastError("Hold timed out");
                    queueRepository.save(q);
                }
            });
            if (m.getStatus() == MessageStatus.QUEUED) {
                m.setStatus(MessageStatus.FAILED);
                m.setFailedAt(Instant.now());
                m.setErrorMessage("No delivery confirmation received - refunded");
            }
            chargingService.refundHold(m, "Refund - no delivery confirmation received");
        }
        messageRepository.save(m);
        statusService.publish(m, old);
    }
}
