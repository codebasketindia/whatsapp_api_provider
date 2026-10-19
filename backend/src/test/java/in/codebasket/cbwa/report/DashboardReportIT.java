package in.codebasket.cbwa.report;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardReportIT extends AbstractIntegrationTest {

    @Autowired
    WalletService walletService;

    record Seed(Partner a, Partner b, PhoneNumber senderA, PhoneNumber senderB, WhatsappAccount waba) {
    }

    /**
     * Partner A: 3 marketing delivered/read (charged 1.00 each), 1 utility failed (hold refunded),
     * 1 service sent (charged 0.50 then refunded after finalisation by admin), 1 queued (held 1.00).
     * Partner B: 1 marketing delivered (charged 2.00).
     */
    private Seed seed() {
        Partner a = testData.partner(new BigDecimal("100"));
        Partner b = testData.partner(new BigDecimal("50"));
        WhatsappAccount waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        PhoneNumber sa = testData.sender(waba, a.getId(), SenderType.PLATFORM_SENDER);
        PhoneNumber sb = testData.sender(waba, b.getId(), SenderType.PLATFORM_SENDER);
        jdbc.update("update phone_numbers set wa_number = '919800000001', quality_rating = 'YELLOW' where id = ?", sb.getId());

        charged(a, sa, "MARKETING", "DELIVERED", "1.00");
        charged(a, sa, "MARKETING", "READ", "1.00");
        charged(a, sa, "MARKETING", "DELIVERED", "1.00");
        String failed = message(a, sa, "UTILITY", "FAILED", "0.80", "REFUNDED");
        walletService.hold(a.getId(), new BigDecimal("0.80"), WalletRefType.MESSAGE, failed, null);
        walletService.refundHold(a.getId(), new BigDecimal("0.80"), WalletRefType.MESSAGE, failed, null);
        String service = message(a, sa, "SERVICE", "SENT", "0.50", "REFUNDED");
        walletService.hold(a.getId(), new BigDecimal("0.50"), WalletRefType.MESSAGE, service, null);
        walletService.finalizeHold(a.getId(), new BigDecimal("0.50"), WalletRefType.MESSAGE, service, null);
        walletService.refund(a.getId(), new BigDecimal("0.50"), WalletRefType.MESSAGE, service, null);
        String queued = message(a, sa, "MARKETING", "QUEUED", "1.00", "HELD");
        walletService.hold(a.getId(), new BigDecimal("1.00"), WalletRefType.MESSAGE, queued, null);

        charged(b, sb, "MARKETING", "DELIVERED", "2.00");
        // inbound message must never be counted
        jdbc.update("""
                insert into messages (uid, partner_id, phone_number_id, direction, wa_id, country_code, type, status, price,
                  charge_status, source, created_at, updated_at, version)
                values (?, ?, ?, 'INBOUND', '919811111111', 'IN', 'TEXT', 'RECEIVED', 0, 'NONE', 'DASHBOARD', utc_timestamp(6), utc_timestamp(6), 0)""",
                Ulids.next(), a.getId(), sa.getId());
        return new Seed(a, b, sa, sb, waba);
    }

    private void charged(Partner p, PhoneNumber s, String category, String status, String price) {
        String uid = message(p, s, category, status, price, "CHARGED");
        walletService.hold(p.getId(), new BigDecimal(price), WalletRefType.MESSAGE, uid, null);
        walletService.finalizeHold(p.getId(), new BigDecimal(price), WalletRefType.MESSAGE, uid, null);
    }

    private String message(Partner p, PhoneNumber s, String category, String status, String price, String chargeStatus) {
        String uid = Ulids.next();
        jdbc.update("""
                insert into messages (uid, partner_id, phone_number_id, direction, wa_id, country_code, type, category, status, price,
                  charge_status, source, created_at, updated_at, version)
                values (?, ?, ?, 'OUTBOUND', '919812345678', 'IN', 'TEMPLATE', ?, ?, ?, ?, 'DASHBOARD', utc_timestamp(6), utc_timestamp(6), 0)""",
                uid, p.getId(), s.getId(), category, status, new BigDecimal(price), chargeStatus);
        return uid;
    }

    @Test
    void adminDashboardMatchesMessagesAndLedger() throws Exception {
        Seed s = seed();
        User admin = testData.superAdmin();
        JsonNode d = fetch(admin, "/api/admin/dashboard").path("data");
        assertThat(d.at("/partners/total").asLong()).isEqualTo(2);
        assertThat(d.at("/today/total").asLong()).isEqualTo(7);
        assertThat(d.at("/today/sent").asLong()).isEqualTo(5);      // DELIVERED x3 + READ + SENT(B delivered counted) -> 4 delivered/read + 1 sent
        assertThat(d.at("/today/delivered").asLong()).isEqualTo(4);
        assertThat(d.at("/today/read").asLong()).isEqualTo(1);
        assertThat(d.at("/today/failed").asLong()).isEqualTo(1);
        // revenue = finalized debits (1+1+1+0.5+2) - finalized refund 0.5 = 5.00
        assertThat(new BigDecimal(d.at("/revenue/today").asText())).isEqualByComparingTo("5.00");
        assertThat(new BigDecimal(d.at("/revenue/month").asText())).isEqualByComparingTo("5.00");
        // wallets: A 100 - 3 - 0.5 + 0.5 - 1 (held) = 96 ; B 48
        assertThat(new BigDecimal(d.at("/walletTotal").asText())).isEqualByComparingTo("144");
        assertThat(new BigDecimal(d.at("/walletHeld").asText())).isEqualByComparingTo("1");
        BigDecimal ledger = jdbc.queryForObject("select sum(balance) from wallets", BigDecimal.class);
        assertThat(new BigDecimal(d.at("/walletTotal").asText())).isEqualByComparingTo(ledger);
        assertThat(d.at("/senderAlerts").size()).isEqualTo(1);
        assertThat(d.at("/senderAlerts/0/senderId").asLong()).isEqualTo(s.senderB().getId());

        JsonNode chart = fetch(admin, "/api/admin/dashboard/chart?from=" + LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(6)).path("data");
        assertThat(chart.size()).isEqualTo(7);
        assertThat(chart.get(6).path("total").asLong()).isEqualTo(7);
        assertThat(chart.get(0).path("total").asLong()).isZero();
        JsonNode rev = fetch(admin, "/api/admin/dashboard/chart?metric=revenue").path("data");
        assertThat(rev.size()).isEqualTo(30);
        assertThat(new BigDecimal(rev.get(29).path("revenue").asText())).isEqualByComparingTo("5.00");
    }

    @Test
    void partnerDashboardAndUsageAreScopedToPartner() throws Exception {
        Seed s = seed();
        JsonNode d = fetch(s.a(), "/api/partner/dashboard").path("data");
        assertThat(new BigDecimal(d.at("/wallet/balance").asText())).isEqualByComparingTo("96");
        assertThat(new BigDecimal(d.at("/wallet/held").asText())).isEqualByComparingTo("1");
        assertThat(d.at("/today/total").asLong()).isEqualTo(6);
        assertThat(d.at("/today/delivered").asLong()).isEqualTo(3);
        assertThat(new BigDecimal(d.at("/today/spend").asText())).isEqualByComparingTo("3.00");
        // 3 delivered / (4 sent + 1 failed) = 60%
        assertThat(new BigDecimal(d.at("/deliveryRate7d").asText())).isEqualByComparingTo("60.00");
        assertThat(d.at("/senders").size()).isEqualTo(1);
        assertThat(d.at("/lowBalance").asBoolean()).isTrue(); // default threshold 500

        JsonNode usage = fetch(s.a(), "/api/partner/reports/usage?groupBy=category").path("data");
        assertThat(usage.size()).isEqualTo(3);
        JsonNode mk = usage.get(0);
        assertThat(mk.path("key").asText()).isEqualTo("MARKETING");
        assertThat(mk.path("delivered").asLong()).isEqualTo(3);
        assertThat(new BigDecimal(mk.path("charged").asText())).isEqualByComparingTo("3.00");

        JsonNode usageB = fetch(s.b(), "/api/partner/reports/usage?groupBy=sender").path("data");
        assertThat(usageB.size()).isEqualTo(1);
        assertThat(new BigDecimal(usageB.get(0).path("charged").asText())).isEqualByComparingTo("2.00");

        JsonNode days = fetch(s.a(), "/api/partner/dashboard/chart").path("data");
        assertThat(days.size()).isEqualTo(30);
        assertThat(new BigDecimal(days.get(29).path("spend").asText())).isEqualByComparingTo("3.00");

        mvc(s.a(), "/api/admin/dashboard").andExpect(status().isForbidden());
    }

    @Test
    void adminReportsAndExports() throws Exception {
        seed();
        User admin = testData.superAdmin();
        JsonNode partners = fetch(admin, "/api/admin/reports/partners?size=1").path("data");
        assertThat(partners.path("totalElements").asLong()).isEqualTo(2);
        assertThat(partners.path("content").size()).isEqualTo(1);
        JsonNode all = fetch(admin, "/api/admin/reports/partners").path("data").path("content");
        BigDecimal charged = BigDecimal.ZERO;
        long messages = 0;
        for (JsonNode r : all) {
            charged = charged.add(new BigDecimal(r.path("charged").asText()));
            messages += r.path("messages").asLong();
        }
        assertThat(charged).isEqualByComparingTo("5.00");
        assertThat(messages).isEqualTo(7);

        JsonNode cats = fetch(admin, "/api/admin/reports/categories").path("data");
        assertThat(cats.get(0).path("category").asText()).isEqualTo("MARKETING");
        assertThat(cats.get(0).path("count").asLong()).isEqualTo(5);
        assertThat(new BigDecimal(cats.get(0).path("charged").asText())).isEqualByComparingTo("5.00");

        JsonNode countries = fetch(admin, "/api/admin/reports/countries").path("data");
        assertThat(countries.get(0).path("country").asText()).isEqualTo("IN");
        assertThat(countries.get(0).path("count").asLong()).isEqualTo(7);

        JsonNode rev = fetch(admin, "/api/admin/reports/revenue?groupBy=month").path("data");
        BigDecimal msgCharges = BigDecimal.ZERO;
        for (JsonNode r : rev) {
            msgCharges = msgCharges.add(new BigDecimal(r.path("messageCharges").asText()));
        }
        assertThat(msgCharges).isEqualByComparingTo("5.00");

        MvcResult xlsx = mvc(admin, "/api/admin/reports/partners?format=xlsx").andExpect(status().isOk()).andReturn();
        assertThat(xlsx.getResponse().getContentType()).contains("spreadsheetml");
        byte[] x = xlsx.getResponse().getContentAsByteArray();
        assertThat(new String(x, 0, 2)).isEqualTo("PK");

        MvcResult pdf = mvc(admin, "/api/admin/reports/revenue?format=pdf").andExpect(status().isOk()).andReturn();
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");

        MvcResult csv = mvc(testData.partner(BigDecimal.ZERO), "/api/partner/reports/usage?format=csv").andExpect(status().isOk()).andReturn();
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");

        mvc(admin, "/api/admin/reports/partners?format=doc").andExpect(status().isBadRequest());
    }

    @Test
    void reconciliationComparesMetaCostWithOurCharges() throws Exception {
        Seed s = seed();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        jdbc.update("""
                insert into meta_pricing_analytics (whatsapp_account_id, date, phone_number, country, pricing_category, pricing_type,
                  volume, cost, currency, synced_at) values (?, ?, '919876543210', 'IN', 'MARKETING', 'REGULAR', 4, 3.20, 'INR', ?)""",
                s.waba().getId(), today, LocalDateTime.now(ZoneOffset.UTC));
        JsonNode rows = fetch(testData.superAdmin(), "/api/admin/reports/reconciliation?wabaId=" + s.waba().getId()).path("data");
        JsonNode marketingA = null;
        for (JsonNode r : rows) {
            if (r.path("sender").asText().equals("919876543210") && r.path("category").asText().equals("MARKETING")) {
                marketingA = r;
            }
        }
        assertThat(marketingA).isNotNull();
        assertThat(marketingA.path("metaVolume").asLong()).isEqualTo(4);
        assertThat(new BigDecimal(marketingA.path("metaCost").asText())).isEqualByComparingTo("3.20");
        assertThat(marketingA.path("ourCount").asLong()).isEqualTo(3);
        assertThat(new BigDecimal(marketingA.path("ourCharge").asText())).isEqualByComparingTo("3.00");
        assertThat(new BigDecimal(marketingA.path("profit").asText())).isEqualByComparingTo("-0.20");
    }

    private JsonNode fetch(User u, String url) throws Exception {
        return TestData.json(mockMvc.perform(get(url).header("Authorization", TestData.bearer(testData.accessToken(u))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode fetch(Partner p, String url) throws Exception {
        return TestData.json(mvc(p, url).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions mvc(User u, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", TestData.bearer(testData.accessToken(u))));
    }

    private org.springframework.test.web.servlet.ResultActions mvc(Partner p, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", TestData.bearer(testData.accessToken(p))));
    }
}
