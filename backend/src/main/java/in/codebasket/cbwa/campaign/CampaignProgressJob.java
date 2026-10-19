package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Keeps campaign_recipients and campaign counters in sync with the messages they produced.
 * Counters are cumulative: sent = sent + delivered + read, delivered = delivered + read.
 * A RUNNING campaign completes when nothing is PENDING and every message reached a final state
 * (DELIVERED / READ / FAILED), or 72 hours after the last message was queued.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignProgressJob {

    static final Duration COMPLETE_AFTER = Duration.ofHours(72);

    private final JdbcTemplate jdbc;
    private final CampaignRepository campaignRepository;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public void tick() {
        List<Long> ids = jdbc.queryForList("""
                select id from campaigns where status in ('RUNNING','PAUSED')
                   or (status in ('COMPLETED','CANCELLED') and completed_at > ?)
                """, Long.class, Timestamp.from(Instant.now().minus(Duration.ofHours(24))));
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(s -> refresh(id));
            } catch (RuntimeException e) {
                log.warn("Campaign {} progress refresh failed: {}", id, e.getMessage());
            }
        }
    }

    @Transactional
    public void refresh(Long campaignId) {
        jdbc.update("""
                update campaign_recipients r join messages m on m.id = r.message_id
                set r.status = case m.status when 'SENT' then 'SENT' when 'DELIVERED' then 'DELIVERED' when 'READ' then 'READ'
                                             when 'FAILED' then 'FAILED' else 'QUEUED' end,
                    r.error_message = case when m.status = 'FAILED' then left(coalesce(m.error_message, m.error_title, 'Failed'), 255)
                                           else r.error_message end,
                    r.updated_at = utc_timestamp(6)
                where r.campaign_id = ? and r.message_id is not null and r.status not in ('READ', 'FAILED')
                  and r.status <> (case m.status when 'SENT' then 'SENT' when 'DELIVERED' then 'DELIVERED' when 'READ' then 'READ'
                                                 when 'FAILED' then 'FAILED' else 'QUEUED' end)
                """, campaignId);

        Campaign c = campaignRepository.lockById(campaignId).orElse(null);
        if (c == null) {
            return;
        }
        Map<String, Long> byStatus = new java.util.HashMap<>();
        jdbc.query("select status, count(*) from campaign_recipients where campaign_id = ? group by status",
                rs -> {
                    byStatus.put(rs.getString(1), rs.getLong(2));
                }, campaignId);
        long pending = byStatus.getOrDefault("PENDING", 0L);
        long queued = byStatus.getOrDefault("QUEUED", 0L);
        long sent = byStatus.getOrDefault("SENT", 0L);
        long delivered = byStatus.getOrDefault("DELIVERED", 0L);
        long read = byStatus.getOrDefault("READ", 0L);
        long failed = byStatus.getOrDefault("FAILED", 0L);
        long skipped = byStatus.getOrDefault("SKIPPED", 0L);

        Map<String, Object> agg = jdbc.queryForMap("""
                select coalesce(sum(case when charge_status in ('HELD','CHARGED') then price else 0 end), 0) as charged,
                       coalesce(sum(case when charge_status = 'REFUNDED' then price else 0 end), 0) as refunded,
                       coalesce(sum(case when status in ('QUEUED','ACCEPTED','SENT') then 1 else 0 end), 0) as open_msgs,
                       max(created_at) as last_queued
                from messages where campaign_id = ?
                """, campaignId);
        c.setQueuedCount((int) queued);
        c.setSentCount((int) (sent + delivered + read));
        c.setDeliveredCount((int) (delivered + read));
        c.setReadCount((int) read);
        c.setFailedCount((int) failed);
        c.setSkippedCount((int) Math.max(0, skipped - c.getInvalidCount()));
        c.setChargedAmount(MoneyUtil.scale((BigDecimal) agg.get("charged")));
        c.setRefundedAmount(MoneyUtil.scale((BigDecimal) agg.get("refunded")));

        if (c.getStatus() == CampaignStatus.RUNNING && pending == 0) {
            long open = ((Number) agg.get("open_msgs")).longValue();
            Object lastObj = agg.get("last_queued");
            Instant last = lastObj instanceof Timestamp ts ? ts.toInstant()
                    : lastObj instanceof java.time.LocalDateTime ldt ? ldt.toInstant(java.time.ZoneOffset.UTC) : null;
            boolean stale = last == null || last.isBefore(Instant.now().minus(COMPLETE_AFTER));
            if (open == 0 || stale) {
                c.setStatus(CampaignStatus.COMPLETED);
                c.setCompletedAt(Instant.now());
                notifications.notifyPartner(c.getPartnerId(), "CAMPAIGN_COMPLETED", NotificationService.SUCCESS,
                        "Campaign completed: " + c.getName(),
                        c.getDeliveredCount() + " delivered, " + c.getFailedCount() + " failed out of " + c.getValidCount() + " recipients.",
                        "/partner/campaigns/" + c.getId(), null);
            }
        }
        campaignRepository.save(c);
    }
}
