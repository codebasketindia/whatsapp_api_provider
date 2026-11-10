package in.codebasket.cbwa.registration;

import in.codebasket.cbwa.common.constants.PassStatus;
import in.codebasket.cbwa.common.constants.PaymentStatus;
import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.job.RegistrationPaymentJob;
import in.codebasket.cbwa.common.repository.RegistrationRepository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import in.codebasket.cbwa.common.utils.HashUtil;
import in.codebasket.cbwa.common.constants.BillingMode;
import in.codebasket.cbwa.common.constants.SenderType;
import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.repository.ScheduledMessageRepository;
import in.codebasket.cbwa.common.entity.PhoneNumber;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.common.constants.WabaOwnerType;
import in.codebasket.cbwa.common.entity.WhatsappAccount;
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
import java.util.List;

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

import in.codebasket.cbwa.common.repository.RegistrationItemRepository;
import in.codebasket.cbwa.common.service.registration.BookingService;
import in.codebasket.cbwa.common.entity.RegistrationItem;
import in.codebasket.cbwa.common.entity.Registration;

class BookingIT extends AbstractIntegrationTest {

    static final WireMockServer RZP = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String KEY_ID = "rzp_test_BOOKING1";
    static final String KEY_SECRET = "booking-key-secret";

    static {
        RZP.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cbwa.razorpay.base-url", () -> "http://localhost:" + RZP.port());
    }

    @Autowired RegistrationRepository registrationRepository;
    @Autowired RegistrationItemRepository itemRepository;
    @Autowired BookingService booking;

    Partner partner;
    PhoneNumber sender;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        RZP.resetAll();
        RZP.stubFor(get(urlPathEqualTo("/v1/orders")).willReturn(json("{\"items\":[]}")));
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_B1\",\"status\":\"created\"}")));
        partner = testData.partner(new BigDecimal("50"));
        WhatsappAccount waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        sender = testData.sender(waba, partner.getId(), SenderType.PLATFORM_SENDER);
        token = testData.accessToken(partner);
        send(put("/api/partner/payment-account"), """
                {"keyId":"%s","keySecret":"%s","enabled":true}""".formatted(KEY_ID, KEY_SECRET)).andExpect(status().isOk());
    }

    @Test
    void bookingWithPackagesPaymentPassesInvoiceAndOneTimeRedemption() throws Exception {
        String form = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/registration-forms"), """
                {"title":"Ziyarat Passes for HAJJ 2027","slug":"ziyarat-2027","senderId":%d,"passPrefix":"ZP",
                 "termsText":"1. Passes are non-transferable.","privacyText":"We keep your name and mobile only for this booking.",
                 "packages":[
                   {"code":"REG","name":"Harmain Ziyarat Show","mrp":49,"salePrice":49,"mandatory":true,"icon":"landmark"},
                   {"code":"SMM","name":"Special Makkah & Madina Ziyarat","mrp":5000,"salePrice":3000,"icon":"moon-star"},
                   {"code":"AMM","name":"Additional Makkah & Madina Ziyarat","mrp":5000,"salePrice":3000},
                   {"code":"ACZ","name":"Additional Cities Ziyarat","mrp":5000,"salePrice":3000,"passTitle":"Cities Ziyarat Pass"}]}"""
                .formatted(sender.getId())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.packages.length()").value(4)).andReturn().getResponse().getContentAsString();
        var pk = TestData.json(form).path("data").path("packages");
        long smm = pk.get(1).path("id").asLong(), acz = pk.get(3).path("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/register/ziyarat-2027"))
                .andExpect(jsonPath("$.data.packages.length()").value(4))
                .andExpect(jsonPath("$.data.packages[0].mandatory").value(true))
                .andExpect(jsonPath("$.data.packages[1].mrp").value("5000.00"))
                .andExpect(jsonPath("$.data.packages[1].salePrice").value("3000.00"))
                .andExpect(jsonPath("$.data.hasTerms").value(true));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/register/ziyarat-2027/legal/privacy"))
                .andExpect(jsonPath("$.data.title").value("Privacy Policy"));

        // A package id from elsewhere is refused; the price is always computed on the server.
        register("Imran Shaikh", "9811122233", "999999").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("packageIds"));
        String res = register("Imran Shaikh", "9811122233", smm + "," + acz).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value("6049.00"))
                .andExpect(jsonPath("$.data.payment.amount").value(604900))
                .andExpect(jsonPath("$.data.booking.items.length()").value(3))
                .andExpect(jsonPath("$.data.booking.mrpTotal").value("10049.00"))
                .andExpect(jsonPath("$.data.booking.invoiceUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String uid = TestData.json(res).path("data").path("uid").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/bookings/" + uid + "/invoice.pdf"))
                .andExpect(status().isNotFound());

        String verified = verify(uid, "order_B1", "pay_B1", sign("order_B1|pay_B1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.booking.invoiceNo").value("INV-ZP-000001"))
                .andExpect(jsonPath("$.data.booking.items[0].passNo").value("ZP-000001-REG"))
                .andExpect(jsonPath("$.data.booking.items[2].passNo").value("ZP-000001-ACZ"))
                .andExpect(jsonPath("$.data.booking.items[1].passUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        var b = TestData.json(verified).path("data").path("booking");
        byte[] pdf = fetch(b.path("invoiceUrl").asText()).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        byte[] passes = fetch(b.path("passesPdfUrl").asText()).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(passes, 0, 5)).isEqualTo("%PDF-");
        byte[] jpg = fetch(b.path("items").get(2).path("passUrl").asText()).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpg)).getWidth()).isEqualTo(1600);

        Registration reg = registrationRepository.findByUid(uid).orElseThrow();
        List<RegistrationItem> items = itemRepository.findByRegistrationIdOrderByIdAsc(reg.getId());
        String qr = booking.qrUrl(items.get(1), reg);
        assertThat(qr).contains("/v/" + items.get(1).getId() + ".");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/passes/check").param("token", qr))
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.name").value("I**** S*****"))
                .andExpect(jsonPath("$.data.mobile").doesNotExist());
        String tampered = qr.substring(0, qr.length() - 2) + (qr.endsWith("AA") ? "BB" : "AA");
        tokenCall("/api/partner/passes/verify", tampered, token).andExpect(jsonPath("$.data.status").value("INVALID"));
        tokenCall("/api/partner/passes/verify", qr, token).andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.mobile").value("+919811122233"));
        Partner other = testData.partner(new BigDecimal("5"));
        tokenCall("/api/partner/passes/redeem", qr, testData.accessToken(other)).andExpect(jsonPath("$.data.status").value("INVALID"));
        tokenCall("/api/partner/passes/redeem", qr, token).andExpect(jsonPath("$.data.status").value("VALID"));
        tokenCall("/api/partner/passes/redeem", qr, token).andExpect(jsonPath("$.data.status").value("ALREADY_REDEEMED"))
                .andExpect(jsonPath("$.data.redeemedAt").isNotEmpty());

        // Paid: the same number cannot book again; the other passes are untouched.
        register("Imran Shaikh", "9811122233", "").andExpect(status().isConflict());
        assertThat(itemRepository.findById(items.get(0).getId()).orElseThrow().getRedeemedAt()).isNull();
    }

    private ResultActions register(String name, String mobile, String packageIds) throws Exception {
        testData.resetCaches();
        return mockMvc.perform(multipart("/api/public/register/ziyarat-2027").param("name", name).param("mobile", mobile)
                .param("consent", "true").param("packageIds", packageIds));
    }

    private ResultActions verify(String uid, String order, String pay, String sig) throws Exception {
        testData.resetCaches();
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/public/register/ziyarat-2027/verify-payment")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"registrationUid":"%s","razorpayOrderId":"%s","razorpayPaymentId":"%s","razorpaySignature":"%s"}""".formatted(uid, order, pay, sig)));
    }

    private ResultActions fetch(String url) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
    }

    private ResultActions tokenCall(String url, String qr, String bearer) throws Exception {
        testData.resetCaches();
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", TestData.bearer(bearer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + qr + "\"}"));
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String json) throws Exception {
        return mockMvc.perform(b.header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String sign(String data) {
        return HashUtil.hmacSha256Hex(KEY_SECRET.getBytes(StandardCharsets.UTF_8), data.getBytes(StandardCharsets.UTF_8));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
