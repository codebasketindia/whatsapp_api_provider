package in.codebasket.cbwa.registration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.scheduling.ScheduledMessageRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaidRegistrationIT extends AbstractIntegrationTest {

    static final WireMockServer RZP = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String KEY_ID = "rzp_test_PARTNER123";
    static final String KEY_SECRET = "partner-key-secret";
    static final String HOOK_SECRET = "partner-hook-secret";

    static {
        RZP.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cbwa.razorpay.base-url", () -> "http://localhost:" + RZP.port());
    }

    @Autowired RegistrationRepository registrationRepository;
    @Autowired ScheduledMessageRepository scheduledRepository;
    @Autowired RegistrationPaymentJob job;

    Partner partner;
    PhoneNumber sender;
    String token;

    @BeforeEach
    void setUp() {
        RZP.resetAll();
        RZP.stubFor(get(urlPathEqualTo("/v1/orders")).willReturn(json("{\"items\":[]}")));
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_P1\",\"status\":\"created\"}")));
        partner = testData.partner(new BigDecimal("50"));
        WhatsappAccount waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        sender = testData.sender(waba, partner.getId(), SenderType.PLATFORM_SENDER);
        token = testData.accessToken(partner);
    }

    @Test
    void passIsCreatedOnlyAfterVerifiedPayment() throws Exception {
        // A fee needs a connected Razorpay account.
        form(499).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("feeAmount"));
        connectRazorpay();
        form(499).andExpect(status().isCreated()).andExpect(jsonPath("$.data.feeAmount").value("499.00"));

        String res = register("Imran Shaikh", "9811122233").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.passStatus").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.data.passNo").doesNotExist())
                .andExpect(jsonPath("$.data.payment.orderId").value("order_P1"))
                .andExpect(jsonPath("$.data.payment.keyId").value(KEY_ID))
                .andExpect(jsonPath("$.data.payment.amount").value(49900))
                .andReturn().getResponse().getContentAsString();
        String uid = TestData.json(res).path("data").path("uid").asText();
        RZP.verify(postRequestedFor(urlEqualTo("/v1/orders")).withRequestBody(equalToJson("{\"amount\":49900,\"currency\":\"INR\"}", true, true)));
        Registration r = registrationRepository.findByUid(uid).orElseThrow();
        assertThat(r.getPassFileId()).isNull();
        assertThat(scheduledRepository.count()).isZero();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/passes/" + uid + ".jpg"))
                .andExpect(status().isNotFound());

        // Retrying before paying is allowed (same registration, new order); a forged signature is not.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_P2\",\"status\":\"created\"}")));
        register("Imran Shaikh", "9811122233").andExpect(status().isCreated()).andExpect(jsonPath("$.data.uid").value(uid))
                .andExpect(jsonPath("$.data.payment.orderId").value("order_P2"));
        verify(uid, "order_P2", "pay_X", "deadbeef").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_SIGNATURE_INVALID"));
        verify(uid, "order_P1", "pay_X", sign("order_P1|pay_X", KEY_SECRET)).andExpect(status().isBadRequest()); // old order

        verify(uid, "order_P2", "pay_OK", sign("order_P2|pay_OK", KEY_SECRET)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.passNo").value("PASS-000001"))
                .andExpect(jsonPath("$.data.passUrl").isNotEmpty());
        r = registrationRepository.findByUid(uid).orElseThrow();
        assertThat(r.getRazorpayPaymentId()).isEqualTo("pay_OK");
        assertThat(r.getPassFileId()).isNotNull();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                registrationRepository.findByUid(uid).orElseThrow().getPassStatus() == PassStatus.WAITING_FOR_CHAT);

        // Paid now: the same number cannot register again, and a repeated verify changes nothing.
        register("Imran Shaikh", "9811122233").andExpect(status().isConflict());
        verify(uid, "order_P2", "pay_OK", sign("order_P2|pay_OK", KEY_SECRET)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passNo").value("PASS-000001"));
    }

    @Test
    void webhookAndJobConfirmPaymentsWhenTheBrowserNeverReturns() throws Exception {
        connectRazorpay();
        form(100).andExpect(status().isCreated());
        String a = uid(register("Ayesha Khan", "9876501234"));

        String body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_W1","order_id":"order_P1","method":"upi","status":"captured"}}}}""";
        webhook(body, "bad").andExpect(status().isBadRequest());
        webhook(body, sign(body, HOOK_SECRET)).andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("PROCESSED"));
        webhook(body, sign(body, HOOK_SECRET)).andExpect(jsonPath("$.data.result").value("DUPLICATE"));
        Registration ra = registrationRepository.findByUid(a).orElseThrow();
        assertThat(ra.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ra.getPaymentMethod()).isEqualTo("upi");
        assertThat(ra.getPassNo()).isEqualTo("PASS-000001");

        // Second person: no webhook; the job asks Razorpay and finds a captured payment.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_J1\",\"status\":\"created\"}")));
        String b = uid(register("Sita Devi", "9876501235"));
        jdbc.update("update registrations set updated_at = date_sub(updated_at, interval 5 minute) where uid = ?", b);
        RZP.stubFor(get(urlPathEqualTo("/v1/orders/order_J1/payments")).willReturn(json("""
                {"items":[{"id":"pay_J1","order_id":"order_J1","amount":10000,"currency":"INR","status":"captured","method":"card"}]}""")));
        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(registrationRepository.findByUid(b).orElseThrow().getPassNo()).isEqualTo("PASS-000002");
    }

    // ---- helpers

    private void connectRazorpay() throws Exception {
        send(put("/api/partner/payment-account"), """
                {"keyId":"%s","keySecret":"%s","webhookSecret":"%s","enabled":true}""".formatted(KEY_ID, KEY_SECRET, HOOK_SECRET))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.keySecretSet").value(true))
                .andExpect(jsonPath("$.data.webhookUrl").value(org.hamcrest.Matchers.endsWith("/api/webhooks/razorpay/partners/" + partner.getUid())))
                .andExpect(jsonPath("$.data.keySecret").doesNotExist());
    }

    private ResultActions form(int fee) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/registration-forms"), """
                {"title":"Paid Ziyarat","slug":"paid-ziyarat-%d","senderId":%d,"feeAmount":%d,"feeLabel":"Ziyarat show entry"}"""
                .formatted(System.nanoTime() % 100000, sender.getId(), fee));
    }

    private ResultActions register(String name, String mobile) throws Exception {
        testData.resetCaches();
        String slug = jdbc.queryForObject("select slug from registration_forms order by id desc limit 1", String.class);
        return mockMvc.perform(multipart("/api/public/register/" + slug).param("name", name).param("mobile", mobile).param("consent", "true"));
    }

    private ResultActions verify(String uid, String order, String pay, String sig) throws Exception {
        testData.resetCaches();
        String slug = jdbc.queryForObject("select slug from registration_forms order by id desc limit 1", String.class);
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/public/register/" + slug + "/verify-payment")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"registrationUid":"%s","razorpayOrderId":"%s","razorpayPaymentId":"%s","razorpaySignature":"%s"}""".formatted(uid, order, pay, sig)));
    }

    private ResultActions webhook(String body, String sig) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/razorpay/partners/" + partner.getUid())
                .contentType(MediaType.APPLICATION_JSON).content(body).header("X-Razorpay-Signature", sig).header("X-Razorpay-Event-Id", "evt_" + body.hashCode()));
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String json) throws Exception {
        return mockMvc.perform(b.header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String uid(ResultActions r) throws Exception {
        return TestData.json(r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data").path("uid").asText();
    }

    private static String sign(String data, String secret) {
        return HashUtil.hmacSha256Hex(secret.getBytes(StandardCharsets.UTF_8), data.getBytes(StandardCharsets.UTF_8));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
