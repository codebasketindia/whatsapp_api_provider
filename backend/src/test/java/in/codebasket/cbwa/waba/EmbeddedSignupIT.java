package in.codebasket.cbwa.waba;

import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.meta.MetaTestBase;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.PartnerPhoneNumberRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderStatus;
import in.codebasket.cbwa.support.TestData;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmbeddedSignupIT extends MetaTestBase {

    private static final String WABA = "1111222233";
    private static final String PHONE = "4444555566";

    @Autowired
    PhoneNumberRepository phoneNumberRepository;
    @Autowired
    WhatsappAccountRepository wabaRepository;
    @Autowired
    PartnerPhoneNumberRepository assignmentRepository;
    @Autowired
    PartnerRepository partnerRepository;

    private void stubHappyPath() {
        META.stubFor(WireMock.get(urlPathEqualTo(V + "/oauth/access_token"))
                .withQueryParam("code", equalTo("good-code"))
                .willReturn(okJson("{\"access_token\":\"BUSINESS_TOKEN\",\"token_type\":\"bearer\"}")));
        META.stubFor(WireMock.get(urlPathEqualTo(V + "/" + WABA))
                .willReturn(okJson("{\"id\":\"" + WABA + "\",\"name\":\"Acme WABA\",\"currency\":\"INR\","
                        + "\"business_verification_status\":\"verified\",\"account_review_status\":\"APPROVED\"}")));
        META.stubFor(WireMock.get(urlPathEqualTo(V + "/" + WABA + "/phone_numbers"))
                .willReturn(okJson("{\"data\":[{\"id\":\"" + PHONE + "\",\"display_phone_number\":\"+91 98765 11111\","
                        + "\"verified_name\":\"Acme\",\"quality_rating\":\"GREEN\",\"name_status\":\"APPROVED\","
                        + "\"messaging_limit_tier\":\"TIER_1K\",\"status\":\"PENDING\",\"throughput\":{\"level\":\"STANDARD\"}}]}")));
        META.stubFor(WireMock.post(urlPathEqualTo(V + "/" + WABA + "/subscribed_apps"))
                .willReturn(okJson("{\"success\":true}")));
        META.stubFor(WireMock.post(urlPathEqualTo(V + "/" + PHONE + "/register"))
                .willReturn(okJson("{\"success\":true}")));
    }

    private String body(String code) {
        return "{\"code\":\"" + code + "\",\"wabaId\":\"" + WABA + "\",\"phoneNumberId\":\"" + PHONE
                + "\",\"businessId\":\"77777\",\"mode\":\"NEW\",\"pin\":\"123456\"}";
    }

    @Test
    void partnerConnectsOwnNumber() throws Exception {
        stubHappyPath();
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);

        mockMvc.perform(get("/api/partner/embedded-signup/config").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configId").value("987654321"));

        mockMvc.perform(post("/api/partner/embedded-signup/complete").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("good-code")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sender.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.sender.qualityRating").value("GREEN"))
                .andExpect(jsonPath("$.data.sender.senderType").value("PARTNER_OWN_SENDER"))
                .andExpect(jsonPath("$.data.steps[0].step").value("EXCHANGE_CODE"))
                .andExpect(jsonPath("$.data.checklist.items[0].key").value("PAYMENT_METHOD"));

        PhoneNumber pn = phoneNumberRepository.findByPhoneNumberId(PHONE).orElseThrow();
        assertThat(pn.isRegistered()).isTrue();
        assertThat(pn.getStatus()).isEqualTo(SenderStatus.CONNECTED);
        assertThat(pn.getPinEnc()).startsWith("v1:");
        WhatsappAccount w = wabaRepository.findByWabaId(WABA).orElseThrow();
        assertThat(crypto.decrypt(w.getAccessTokenEnc())).isEqualTo("BUSINESS_TOKEN");
        assertThat(w.getOwnerType()).isEqualTo(WabaOwnerType.PARTNER);
        assertThat(w.getBillingMode()).isEqualTo(BillingMode.TECH_PROVIDER);
        assertThat(assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).orElseThrow().getPartnerId()).isEqualTo(p.getId());
        META.verify(postRequestedFor(urlPathEqualTo(V + "/" + PHONE + "/register"))
                .withRequestBody(matchingJsonPath("$.pin", equalTo("123456"))));

        mockMvc.perform(get("/api/partner/senders").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].phoneNumberId").value(PHONE))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void codeExchangeFailureReturnsMetaError() throws Exception {
        META.stubFor(WireMock.get(urlPathEqualTo(V + "/oauth/access_token"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"Invalid verification code format.\",\"code\":100,\"type\":\"OAuthException\"}}")));
        Partner p = testData.partner(BigDecimal.ZERO);
        mockMvc.perform(post("/api/partner/embedded-signup/complete").header("Authorization", TestData.bearer(testData.accessToken(p)))
                        .contentType(MediaType.APPLICATION_JSON).content(body("bad-code")))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("META_API_ERROR"));
        assertThat(phoneNumberRepository.findByPhoneNumberId(PHONE)).isEmpty();
    }

    @Test
    void ownNumberNotAllowed() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        p.setAllowOwnNumber(false);
        partnerRepository.save(p);
        mockMvc.perform(post("/api/partner/embedded-signup/complete").header("Authorization", TestData.bearer(testData.accessToken(p)))
                        .contentType(MediaType.APPLICATION_JSON).content(body("good-code")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OWN_NUMBER_NOT_ALLOWED"));
        mockMvc.perform(get("/api/partner/embedded-signup/config").header("Authorization", TestData.bearer(testData.accessToken(p))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.reason").isNotEmpty());
    }

    @Test
    void numberAlreadyConnectedToAnotherPartnerConflicts() throws Exception {
        stubHappyPath();
        Partner other = testData.partner(BigDecimal.ZERO);
        WhatsappAccount w = testData.waba(WabaOwnerType.PARTNER, other.getId(), BillingMode.TECH_PROVIDER);
        PhoneNumber pn = testData.sender(w, other.getId(), SenderType.PARTNER_OWN_SENDER);
        pn.setPhoneNumberId(PHONE);
        phoneNumberRepository.save(pn);

        Partner p = testData.partner(BigDecimal.ZERO);
        mockMvc.perform(post("/api/partner/embedded-signup/complete").header("Authorization", TestData.bearer(testData.accessToken(p)))
                        .contentType(MediaType.APPLICATION_JSON).content(body("good-code")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        META.verify(0, postRequestedFor(urlPathEqualTo(V + "/" + PHONE + "/register")));
    }

    @Test
    void partnerCannotSeeAnotherPartnersSender() throws Exception {
        Partner owner = testData.partner(BigDecimal.ZERO);
        WhatsappAccount w = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        PhoneNumber pn = testData.sender(w, owner.getId(), SenderType.PLATFORM_SENDER);
        Partner intruder = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(intruder);
        mockMvc.perform(get("/api/partner/senders/" + pn.getId()).header("Authorization", TestData.bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/partner/senders/" + pn.getId() + "/qr-codes").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/partner/senders/" + pn.getId() + "/register").header("Authorization",
                                TestData.bearer(testData.accessToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void qrCodeCreatedWithShortLink() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        WhatsappAccount w = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        PhoneNumber pn = testData.sender(w, p.getId(), SenderType.PLATFORM_SENDER);
        META.stubFor(WireMock.post(urlPathEqualTo(V + "/" + pn.getPhoneNumberId() + "/message_qrdls"))
                .willReturn(okJson("{\"code\":\"ABC123\",\"prefilled_message\":\"Hi there\",\"deep_link_url\":\"https://wa.me/message/ABC123\"}")));
        mockMvc.perform(post("/api/partner/senders/" + pn.getId() + "/qr-codes").header("Authorization", TestData.bearer(testData.accessToken(p)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prefilledMessage\":\"Hi there\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ABC123"))
                .andExpect(jsonPath("$.data.deepLinkUrl").value("https://wa.me/message/ABC123"))
                .andExpect(jsonPath("$.data.shortLink").value("https://wa.me/919876543210?text=Hi%20there"));
        String body = mockMvc.perform(get("/api/partner/senders/" + pn.getId() + "/qr-codes").header("Authorization",
                        TestData.bearer(testData.accessToken(p)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long qrId = TestData.json(body).at("/data/0/id").asLong();
        byte[] png = mockMvc.perform(get("/api/partner/senders/" + pn.getId() + "/qr-codes/" + qrId + "/image?format=png")
                        .header("Authorization", TestData.bearer(testData.accessToken(p))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(png[1]).isEqualTo((byte) 'P');
    }
}
