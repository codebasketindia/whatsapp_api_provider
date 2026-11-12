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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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
                 "termsText":"1. Passes are non-transferable.","privacyText":"We keep your name and mobile only for this booking.","refundText":"Rs 49 fee is not refundable.",
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
                .andExpect(jsonPath("$.data.hasTerms").value(true))
                .andExpect(jsonPath("$.data.hasRefund").value(true));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/register/ziyarat-2027/legal/privacy"))
                .andExpect(jsonPath("$.data.title").value("Privacy Policy"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/register/ziyarat-2027/legal/refund"))
                .andExpect(jsonPath("$.data.title").value("Refund Policy"))
                .andExpect(jsonPath("$.data.text").value("Rs 49 fee is not refundable."));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/register/ziyarat-2027/legal/other"))
                .andExpect(status().isNotFound());

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

        // Paid: the same number comes back without choosing a new pass - nothing to add; the other passes are untouched.
        register("Imran Shaikh", "9811122233", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("packageIds"));
        assertThat(itemRepository.findById(items.get(0).getId()).orElseThrow().getRedeemedAt()).isNull();
    }

    @Test
    void comboOfferFreePassAndFeeOnlyLine() throws Exception {
        String form = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/registration-forms"), """
                {"title":"Ziyarat Passes for HAJJ 2027","slug":"ziyarat-2027","senderId":%d,"passPrefix":"HZ",
                 "heroImageUrl":"/ziyarat/hajj-2027-banner.jpg","pageHeading":"Apna Ziyarat Pass Chuniye","ctaLabel":"Register Karein",
                 "comboTitle":"Teeno Paid Pass","comboCodes":"smm, amm,ACZ","comboMrp":20000,"comboPrice":9000,
                 "packages":[
                   {"code":"REG","name":"Registration / Person","mrp":49,"salePrice":49,"mandatory":true,"issuePass":false},
                   {"code":"HZS","name":"Haramain Ziyarat Show Pass","mrp":0,"salePrice":0,"passStyle":"FORM_DESIGN","imageUrl":"/ziyarat/haramain-show.jpg"},
                   {"code":"SMM","name":"Khaas","mrp":6000,"salePrice":3000},
                   {"code":"AMM","name":"Mazeed","mrp":6000,"salePrice":3000},
                   {"code":"ACZ","name":"Taif","mrp":8000,"salePrice":4000}]}""".formatted(sender.getId()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.comboCodes").value("SMM,AMM,ACZ"))
                .andReturn().getResponse().getContentAsString();
        var pk = TestData.json(form).path("data").path("packages");
        String all = pk.get(1).path("id").asText() + "," + pk.get(2).path("id").asText() + "," + pk.get(3).path("id").asText() + "," + pk.get(4).path("id").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/register/ziyarat-2027"))
                .andExpect(jsonPath("$.data.combo.price").value("9000.00"))
                .andExpect(jsonPath("$.data.pageHeading").value("Apna Ziyarat Pass Chuniye"))
                .andExpect(jsonPath("$.data.packages[1].imageUrl").value("/ziyarat/haramain-show.jpg"));
        String res = register("Ayesha Khan", "9876501234", all).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value("9049.00"))
                .andExpect(jsonPath("$.data.payment.amount").value(904900))
                .andExpect(jsonPath("$.data.booking.discountAmount").value("1000.00"))
                .andReturn().getResponse().getContentAsString();
        String uid = TestData.json(res).path("data").path("uid").asText();
        verify(uid, "order_B1", "pay_C1", sign("order_B1|pay_C1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.total").value("9049.00"))
                .andExpect(jsonPath("$.data.booking.items[0].passUrl").doesNotExist())   // registration fee: no pass
                .andExpect(jsonPath("$.data.booking.items[1].passNo").value("HZ-000001-HZS"))
                .andExpect(jsonPath("$.data.booking.items.length()").value(5));
        // Only the free pass + fee: Rs 49, no combo.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_B2\",\"status\":\"created\"}")));
        register("Imran Shaikh", "9811122233", pk.get(1).path("id").asText()).andExpect(jsonPath("$.data.amount").value("49.00"));
    }

    @Test
    void paymentCannotBeCheated() throws Exception {
        String form = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/registration-forms"), """
                {"title":"Ziyarat Passes for HAJJ 2027","slug":"ziyarat-2027","senderId":%d,"passPrefix":"HZ",
                 "comboTitle":"Teeno Paid Pass","comboCodes":"SMM,AMM,ACZ","comboPrice":9000,
                 "packages":[
                   {"code":"REG","name":"Registration / Person","mrp":49,"salePrice":49,"mandatory":true,"issuePass":false},
                   {"code":"HZS","name":"Haramain Ziyarat Show Pass","mrp":0,"salePrice":0},
                   {"code":"SMM","name":"Khaas","mrp":6000,"salePrice":3000},
                   {"code":"AMM","name":"Mazeed","mrp":6000,"salePrice":3000},
                   {"code":"ACZ","name":"Taif","mrp":8000,"salePrice":4000}]}""".formatted(sender.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var pk = TestData.json(form).path("data").path("packages");
        String free = pk.get(1).path("id").asText();
        String all = free + "," + pk.get(2).path("id").asText() + "," + pk.get(3).path("id").asText() + "," + pk.get(4).path("id").asText();

        // 1) Prices come from the server only: amount / price fields sent by the browser are ignored.
        testData.resetCaches();
        mockMvc.perform(multipart("/api/public/register/ziyarat-2027").param("name", "Tamper Test").param("mobile", "9800000001")
                        .param("consent", "true").param("packageIds", all).param("amount", "1").param("total", "1")
                        .param("salePrice", "0").param("comboPrice", "1"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.amount").value("9049.00"))
                .andExpect(jsonPath("$.data.payment.amount").value(904900));
        // 2) A package id that is not on this link is refused.
        register("Tamper Test", "9800000002", "999999").andExpect(status().isBadRequest());

        // 3) Cheap order first, then the same number picks every pass: the cheap order can no longer pay for them.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_S1\",\"status\":\"created\"}")));
        String cheap = register("Cheat Test", "9800000003", free).andExpect(jsonPath("$.data.amount").value("49.00"))
                .andReturn().getResponse().getContentAsString();
        String uid = TestData.json(cheap).path("data").path("uid").asText();
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_S2\",\"status\":\"created\"}")));
        register("Cheat Test", "9800000003", all).andExpect(jsonPath("$.data.amount").value("9049.00"));
        verify(uid, "order_S1", "pay_S1", sign("order_S1|pay_S1")).andExpect(status().is4xxClientError());
        assertThat(registrationRepository.findByUid(uid).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        // 4) Razorpay says only Rs 49 was paid on the Rs 9,049 order: refused. The full amount: accepted.
        RZP.stubFor(get(urlEqualTo("/v1/orders/order_S2/payments")).willReturn(json("""
                {"items":[{"id":"pay_S2","order_id":"order_S2","amount":4900,"currency":"INR","status":"captured"}]}""")));
        verify(uid, "order_S2", "pay_S2", sign("order_S2|pay_S2")).andExpect(status().is4xxClientError());
        assertThat(registrationRepository.findByUid(uid).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        RZP.stubFor(get(urlEqualTo("/v1/orders/order_S2/payments")).willReturn(json("""
                {"items":[{"id":"pay_S3","order_id":"order_S2","amount":904900,"currency":"INR","status":"captured"}]}""")));
        verify(uid, "order_S2", "pay_S3", sign("order_S2|pay_S3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.total").value("9049.00"));
        // 5) A forged signature never pays.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_S4\",\"status\":\"created\"}")));
        String other = register("Forge Test", "9800000004", all).andReturn().getResponse().getContentAsString();
        verify(TestData.json(other).path("data").path("uid").asText(), "order_S4", "pay_S4", "deadbeef").andExpect(status().is4xxClientError());
    }

    @Test
    void sameNumberAddsPassesToItsBookingUntilTheComboIsOwned() throws Exception {
        String form = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/registration-forms"), """
                {"title":"Ziyarat Passes for HAJJ 2027","slug":"ziyarat-2027","senderId":%d,"passPrefix":"HZ",
                 "comboTitle":"Teeno Paid Pass","comboCodes":"SMM,AMM,ACZ","comboPrice":9000,
                 "packages":[
                   {"code":"REG","name":"Registration / Person","mrp":49,"salePrice":49,"mandatory":true,"issuePass":false},
                   {"code":"HZS","name":"Haramain Ziyarat Show Pass","mrp":0,"salePrice":0,
                    "itinerary":[{"round":"Round 1","place":"Masjid Quba","time":"8:00 AM"},{"place":"Masjid Qiblatain"},{"place":"  "}]},
                   {"code":"SMM","name":"Khaas","mrp":6000,"salePrice":3000},
                   {"code":"AMM","name":"Mazeed","mrp":6000,"salePrice":3000},
                   {"code":"ACZ","name":"Taif","mrp":8000,"salePrice":4000}]}""".formatted(sender.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.packages[1].itinerary.length()").value(2))
                .andExpect(jsonPath("$.data.packages[1].itinerary[1].round").value("Round 1"))
                .andReturn().getResponse().getContentAsString();
        var pk = TestData.json(form).path("data").path("packages");
        String hzs = pk.get(1).path("id").asText(), smm = pk.get(2).path("id").asText(), amm = pk.get(3).path("id").asText(),
                acz = pk.get(4).path("id").asText();
        fetch("/api/public/register/ziyarat-2027").andExpect(jsonPath("$.data.packages[1].itinerary[0].place").value("Masjid Quba"));

        // First booking: free pass + Rs 49 fee.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_A1\",\"status\":\"created\"}")));
        String first = register("Shahid Husen", "9226926292", hzs).andExpect(jsonPath("$.data.amount").value("49.00"))
                .andReturn().getResponse().getContentAsString();
        String uid = TestData.json(first).path("data").path("uid").asText();
        verify(uid, "order_A1", "pay_A1", sign("order_A1|pay_A1")).andExpect(status().isOk());

        // Same number adds Khaas: only Rs 3,000 (no fee again), same booking, second invoice.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_A2\",\"status\":\"created\"}")));
        // (the free pass it already has is still ticked on the page: skipped, not refused)
        register("Shahid Husen", "9226926292", hzs + "," + smm).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.uid").value(uid)).andExpect(jsonPath("$.data.amount").value("3000.00"))
                .andExpect(jsonPath("$.data.payment.amount").value(300000));
        // A cheap old order or a short payment cannot complete it.
        verify(uid, "order_A1", "pay_X", sign("order_A1|pay_X")).andExpect(jsonPath("$.data.booking.items.length()").value(2));
        RZP.stubFor(get(urlEqualTo("/v1/orders/order_A2/payments")).willReturn(json("""
                {"items":[{"id":"pay_A2","order_id":"order_A2","amount":4900,"currency":"INR","status":"captured"}]}""")));
        verify(uid, "order_A2", "pay_A2", sign("order_A2|pay_A2")).andExpect(status().is4xxClientError());
        RZP.stubFor(get(urlEqualTo("/v1/orders/order_A2/payments")).willReturn(json("""
                {"items":[{"id":"pay_A3","order_id":"order_A2","amount":300000,"currency":"INR","status":"captured"}]}""")));
        verify(uid, "order_A2", "pay_A3", sign("order_A2|pay_A3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passNo").value("HZ-000001"))
                .andExpect(jsonPath("$.data.booking.items.length()").value(3))
                .andExpect(jsonPath("$.data.booking.items[2].passNo").value("HZ-000001-SMM"))
                .andExpect(jsonPath("$.data.booking.total").value("3049.00"))
                .andExpect(jsonPath("$.data.booking.invoices.length()").value(2))
                .andExpect(jsonPath("$.data.booking.invoices[1].invoiceNo").value("INV-HZ-000001-2"));
        Registration reg = registrationRepository.findByUid(uid).orElseThrow();
        assertThat(registrationRepository.count()).isEqualTo(1);
        fetch("/api/public/bookings/" + reg.getUid() + "/invoice-2.pdf").andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        fetch("/api/public/bookings/" + reg.getUid() + "/invoice-3.pdf").andExpect(status().isNotFound());

        // Choosing a pass it already has is refused and names it.
        register("Shahid Husen", "9226926292", smm + "," + amm).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='ownedPackageIds')].message").value(smm));
        // Mazeed + Taif later: not the whole combo in one purchase, so no combo discount (Rs 7,000).
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_A4\",\"status\":\"created\"}")));
        register("Shahid Husen", "9226926292", amm + "," + acz).andExpect(jsonPath("$.data.amount").value("7000.00"));
        verify(uid, "order_A4", "pay_A4", sign("order_A4|pay_A4")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.items.length()").value(5));
        // The whole combo is owned now: "already registered".
        register("Shahid Husen", "9226926292", hzs).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("mobile"));

        // Another number: free first, then all three paid passes in one add-on get the combo price.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_B1\",\"status\":\"created\"}")));
        String b = TestData.json(register("Hafiz Ansari", "8007855095", hzs).andReturn().getResponse().getContentAsString()).path("data").path("uid").asText();
        verify(b, "order_B1", "pay_B1", sign("order_B1|pay_B1")).andExpect(status().isOk());
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_B2\",\"status\":\"created\"}")));
        register("Hafiz Ansari", "8007855095", smm + "," + amm + "," + acz).andExpect(jsonPath("$.data.amount").value("9000.00"));
        // A newer attempt replaces the unpaid one: the replaced order cannot pay any more.
        RZP.stubFor(post(urlEqualTo("/v1/orders")).willReturn(json("{\"id\":\"order_B3\",\"status\":\"created\"}")));
        register("Hafiz Ansari", "8007855095", smm).andExpect(jsonPath("$.data.amount").value("3000.00"));
        verify(b, "order_B2", "pay_B2", sign("order_B2|pay_B2")).andExpect(status().is4xxClientError());
        verify(b, "order_B3", "pay_B3", sign("order_B3|pay_B3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.items.length()").value(3));
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
