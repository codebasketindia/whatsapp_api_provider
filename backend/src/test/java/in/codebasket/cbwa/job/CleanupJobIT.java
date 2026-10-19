package in.codebasket.cbwa.job;

import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.waba.WabaOwnerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupJobIT extends AbstractIntegrationTest {

    @Autowired
    CleanupJob job;

    @Test
    void removesExpiredAndOldRowsAndKeepsRecentOnes() {
        User admin = testData.superAdmin();
        jdbc.update("insert into refresh_tokens (user_id, token_hash, family_id, issued_at, expires_at) values (?, ?, ?, "
                + "date_sub(utc_timestamp(6), interval 10 day), date_sub(utc_timestamp(6), interval 1 day))", admin.getId(), "a".repeat(64), Ulids.next());
        jdbc.update("insert into refresh_tokens (user_id, token_hash, family_id, issued_at, expires_at) values (?, ?, ?, "
                + "utc_timestamp(6), date_add(utc_timestamp(6), interval 1 day))", admin.getId(), "b".repeat(64), Ulids.next());
        jdbc.update("insert into notifications (audience, type, severity, title, is_read, created_at) values "
                + "('ADMIN','T','INFO','old read', true, date_sub(utc_timestamp(6), interval 100 day)),"
                + "('ADMIN','T','INFO','old unread', false, date_sub(utc_timestamp(6), interval 100 day))");
        jdbc.update("insert into webhook_inbound_logs (source, event_id, signature_valid, status, attempts, received_at) values "
                + "('META','e1',true,'PROCESSED',1,date_sub(utc_timestamp(6), interval 120 day)),"
                + "('META','e2',true,'FAILED',5,date_sub(utc_timestamp(6), interval 120 day)),"
                + "('META','e3',true,'PROCESSED',1,utc_timestamp(6))");
        Partner p = testData.partner(BigDecimal.ZERO);
        PhoneNumber s = testData.sender(testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM), p.getId(), SenderType.PLATFORM_SENDER);
        jdbc.update("""
                insert into messages (uid, partner_id, phone_number_id, direction, wa_id, type, status, price, charge_status, source,
                  payload, preview, created_at, updated_at, version)
                values (?, ?, ?, 'OUTBOUND', '919812345678', 'TEXT', 'DELIVERED', 0, 'NONE', 'DASHBOARD', '{"a":1}', 'hello',
                  date_sub(utc_timestamp(6), interval 200 day), utc_timestamp(6), 0)""", Ulids.next(), p.getId(), s.getId());

        Map<String, Integer> result = job.run();
        assertThat(result.get("refreshTokens")).isEqualTo(1);
        assertThat(result.get("notifications")).isEqualTo(1);
        assertThat(result.get("webhookInbound")).isEqualTo(1);
        assertThat(result.get("messageContent")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from webhook_inbound_logs", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from messages where payload is null and preview is null", Integer.class)).isEqualTo(1);
    }
}
