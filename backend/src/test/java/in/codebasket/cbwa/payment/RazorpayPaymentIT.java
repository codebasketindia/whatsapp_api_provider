package in.codebasket.cbwa.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.wallet.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RazorpayPaymentIT extends AbstractIntegrationTest {

    static final WireMockServer RZP = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        RZP.start();
    }

    @DynamicPropertySource
    static void razorpay(DynamicPropertyRegistry registry) {
        registry.add("cbwa.razorpay.base-url", () -> "http://localhost:" + RZP.port());
    }

    static final String KEY_SECRET = "rzp_test_secret_123";
    static final String WEBHOOK_SECRET = "whsec_456";

    @Autowired
    SettingsService settings;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    PaymentService paymentService;

    @BeforeEach
    void configure() {
        RZP.resetAll();
        settings.set("PAYMENT", "active_gateway", "RAZORPAY");
        settings.set("PAYMENT", "razorpay_key_id", "rzp_test_key");
        settings.set("PAYMENT", "razorpay_key_secret", KEY_SECRET);
        settings.set("PAYMENT", "razorpay_webhook_secret", WEBHOOK_SECRET);
        settings.set("GST", "enabled", "false");
    }

    @AfterEach
    void reset() {
        settings.set("PAYMENT", "active_gateway", "NONE");
        settings.set("GST", "enabled", "false");
    }

    private void stubOrder(String orderId, long paise) {
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + orderId + "\",\"entity\":\"order\",\"amount\":" + paise + ",\"currency\":\"INR\",\"status\":\"created\"}")));
    }

    private static String payment(String id, String orderId, long paise, String status) {
        return "{\"id\":\"" + id + "\",\"entity\":\"payment\",\"amount\":" + paise + ",\"currency\":\"INR\",\"status\":\"" + status
                + "\",\"order_id\":\"" + orderId + "\",\"method\":\"upi\",\"vpa\":\"someone@okaxis\",\"created_at\":1792440000}";
    }

    private String createOrder(String token, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/payments/orders")
                        .header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void webhook(String body, String eventId, String signature, int expectStatus) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/payments/razorpay")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Razorpay-Event-Id", eventId).header("X-Razorpay-Signature", signature))
                .andExpect(status().is(expectStatus));
    }

    @Test
    void checkoutVerifyAndRepeatedWebhooksCreditWalletExactlyOnce() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        stubOrder("order_A1", 50000);
        String order = createOrder(token, "{\"amount\":\"500\"}");
        assertThat(TestData.json(order).at("/data/amountPaise").asLong()).isEqualTo(50000);
        assertThat(TestData.json(order).at("/data/keyId").asText()).isEqualTo("rzp_test_key");

        RZP.stubFor(get(urlEqualTo("/v1/payments/pay_A1")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(payment("pay_A1", "order_A1", 50000, "captured"))));

        // wrong signature
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/payments/verify")
                        .header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gatewayOrderId\":\"order_A1\",\"gatewayPaymentId\":\"pay_A1\",\"signature\":\"deadbeef\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_SIGNATURE_INVALID"));
        assertThat(walletRepository.findByPartnerId(p.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0");

        String sig = HashUtil.hmacSha256Hex(KEY_SECRET, "order_A1|pay_A1");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/payments/verify")
                            .header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"gatewayOrderId\":\"order_A1\",\"gatewayPaymentId\":\"pay_A1\",\"signature\":\"" + sig + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"))
                    .andExpect(jsonPath("$.data.verifiedVia").value("CHECKOUT"));
        }

        String event = "{\"entity\":\"event\",\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + payment("pay_A1", "order_A1", 50000, "captured") + "}}}";
        String evSig = HashUtil.hmacSha256Hex(WEBHOOK_SECRET, event);
        webhook(event, "evt_1", evSig, 200);
        webhook(event, "evt_1", evSig, 200); // duplicate delivery
        webhook(event, "evt_2", evSig, 200); // same payment, different event id
        webhook(event, "evt_3", "badsignature", 400);

        assertThat(walletRepository.findByPartnerId(p.getId()).orElseThrow().getBalance()).isEqualByComparingTo("500");
        assertThat(jdbc.queryForObject("select count(*) from wallet_transactions where reference_type = 'PAYMENT_ORDER'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from webhook_inbound_logs where source = 'RAZORPAY'", Long.class)).isEqualTo(2);
        String raw = jdbc.queryForObject("select raw_payload from payment_orders limit 1", String.class);
        assertThat(raw).doesNotContain("someone@okaxis");
    }

    @Test
    void webhookAloneCreditsPackWithBonusAndGstInvoice() throws Exception {
        settings.set("GST", "enabled", "true");
        Long packId = jdbc.queryForObject("select id from credit_packs where name = 'Starter'", Long.class);
        jdbc.update("update credit_packs set is_active = true where id = ?", packId);
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        stubOrder("order_B1", 118000);
        String order = createOrder(token, "{\"creditPackId\":" + packId + "}");
        assertThat(TestData.json(order).at("/data/totalAmount").asText()).isEqualTo("1180.0000");
        assertThat(TestData.json(order).at("/data/creditAmount").asText()).isEqualTo("1100.0000");

        String event = "{\"entity\":\"event\",\"event\":\"order.paid\",\"payload\":{\"payment\":{\"entity\":"
                + payment("pay_B1", "order_B1", 118000, "captured") + "}}}";
        webhook(event, "evt_b1", HashUtil.hmacSha256Hex(WEBHOOK_SECRET, event), 200);
        assertThat(walletRepository.findByPartnerId(p.getId()).orElseThrow().getBalance()).isEqualByComparingTo("1100");
        assertThat(jdbc.queryForObject("select total_amount from invoices limit 1", BigDecimal.class)).isEqualByComparingTo("1180");
        assertThat(jdbc.queryForObject("select taxable_amount from invoices limit 1", BigDecimal.class)).isEqualByComparingTo("1000");
        jdbc.update("update credit_packs set is_active = false where id = ?", packId);
    }

    @Test
    void reconciliationSettlesStuckOrderAndExpiresOldUnpaidOrder() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        stubOrder("order_C1", 20000);
        createOrder(token, "{\"amount\":\"200\"}");
        stubOrder("order_C2", 30000);
        createOrder(token, "{\"amount\":\"300\"}");
        jdbc.update("update payment_orders set created_at = date_sub(utc_timestamp(6), interval 20 minute) where gateway_order_id = 'order_C1'");
        jdbc.update("update payment_orders set created_at = date_sub(utc_timestamp(6), interval 2 day) where gateway_order_id = 'order_C2'");
        RZP.stubFor(get(urlPathEqualTo("/v1/orders/order_C1/payments")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"entity\":\"collection\",\"count\":1,\"items\":[" + payment("pay_C1", "order_C1", 20000, "captured") + "]}")));
        RZP.stubFor(get(urlPathEqualTo("/v1/orders/order_C2/payments")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"entity\":\"collection\",\"count\":0,\"items\":[]}")));

        var summary = paymentService.reconcilePending();
        assertThat(summary.checked()).isEqualTo(2);
        assertThat(summary.paid()).isEqualTo(1);
        assertThat(summary.expired()).isEqualTo(1);
        assertThat(walletRepository.findByPartnerId(p.getId()).orElseThrow().getBalance()).isEqualByComparingTo("200");
        assertThat(jdbc.queryForObject("select verified_via from payment_orders where gateway_order_id = 'order_C1'", String.class))
                .isEqualTo("RECONCILE");
        // running again does not credit twice
        paymentService.reconcilePending();
        assertThat(walletRepository.findByPartnerId(p.getId()).orElseThrow().getBalance()).isEqualByComparingTo("200");
    }

    @Test
    void amountOutOfRangeAndGatewayNotConfigured() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/payments/orders")
                        .header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"10\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_OUT_OF_RANGE"));
        settings.set("PAYMENT", "active_gateway", "NONE");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/payments/orders")
                        .header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"500\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_NOT_CONFIGURED"));
    }
}
