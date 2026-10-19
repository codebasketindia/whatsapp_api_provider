package in.codebasket.cbwa.job;

import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daily housekeeping (04:00 IST): expired refresh tokens / OTPs, old webhook logs, old read
 * notifications, and message content retention (payload/preview cleared, metadata kept).
 * Deletes run in batches of 1000 so no long table locks are taken.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJob {

    private static final int BATCH = 1000;

    private final JdbcTemplate jdbc;
    private final SettingsService settings;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Kolkata")
    public void scheduled() {
        run();
    }

    public Map<String, Integer> run() {
        Instant now = Instant.now();
        int webhookDays = settings.getInt(SettingKeys.RETENTION, "webhook_log_days", 90);
        int contentDays = settings.getInt(SettingKeys.RETENTION, "message_content_days", 180);
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("refreshTokens", batched("DELETE FROM refresh_tokens WHERE expires_at < ? LIMIT " + BATCH, now));
        out.put("otps", batched("DELETE FROM user_otps WHERE expires_at < ? LIMIT " + BATCH, now));
        Instant webhookBefore = now.minus(Duration.ofDays(webhookDays));
        out.put("webhookInbound", batched("DELETE FROM webhook_inbound_logs WHERE received_at < ? AND status IN ('PROCESSED','IGNORED') LIMIT "
                + BATCH, webhookBefore));
        out.put("webhookDeliveries", batched("DELETE FROM webhook_delivery_logs WHERE created_at < ? AND status IN ('SUCCESS','DEAD') LIMIT "
                + BATCH, webhookBefore));
        out.put("notifications", batched("DELETE FROM notifications WHERE created_at < ? AND is_read = TRUE LIMIT " + BATCH,
                now.minus(Duration.ofDays(90))));
        out.put("messageContent", batched("UPDATE messages SET payload = NULL, preview = NULL WHERE created_at < ?"
                + " AND (payload IS NOT NULL OR preview IS NOT NULL) LIMIT " + BATCH, now.minus(Duration.ofDays(contentDays))));
        log.info("Cleanup finished: {}", out);
        return out;
    }

    private int batched(String sql, Instant before) {
        Timestamp ts = Timestamp.valueOf(LocalDateTime.ofInstant(before, ZoneOffset.UTC));
        int total = 0;
        while (true) {
            int n = jdbc.update(sql, ts);
            total += n;
            if (n < BATCH) {
                return total;
            }
        }
    }
}
