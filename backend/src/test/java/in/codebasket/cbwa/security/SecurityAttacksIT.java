package in.codebasket.cbwa.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.publicapi.ApiKey;
import in.codebasket.cbwa.publicapi.ApiKeyRepository;
import in.codebasket.cbwa.publicapi.ApiKeyStatus;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserRepository;
import in.codebasket.cbwa.user.UserStatus;
import in.codebasket.cbwa.webhook.WebhookInboundLogRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Targeted attacks that the generic route matrix cannot express. */
class SecurityAttacksIT extends AbstractIntegrationTest {

    static final WireMockServer EXT = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String JWT_SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtZm9yLWNid2EtaW50ZWdyYXRpb24tdGVzdHMtNjQtYnl0ZXMtbWluaW11bSE=";

    static {
        EXT.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cbwa.meta.graph-base-url", () -> "http://localhost:" + EXT.port());
        r.add("cbwa.razorpay.base-url", () -> "http://localhost:" + EXT.port());
        r.add("cbwa.webhook.async", () -> "false");
    }

    @AfterAll
    static void stop() {
        EXT.stop();
    }

    @Autowired TortureFixtures fixtures;
    @Autowired UserRepository userRepository;
    @Autowired ApiKeyRepository apiKeyRepository;
    @Autowired WebhookInboundLogRepository inboundLogRepository;
    @Autowired in.codebasket.cbwa.webhook.WebhookInboundProcessor processor;

    TortureFixtures.World w;

    @BeforeEach
    void setUp() {
        EXT.resetAll();
        EXT.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"data\":[]}")));
        w = fixtures.build();
    }

    // ------------------------------------------------------------------ JWT

    @Test
    void forgedExpiredAndAlgNoneTokensAreRejected() throws Exception {
        String valid = w.a.token;
        String[] parts = valid.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        // privilege escalation attempt: PARTNER -> ADMIN with super admin flag, original signature
        String escalated = payload.replace("\"typ\":\"PARTNER\"", "\"typ\":\"ADMIN\"").replace("\"sa\":false", "\"sa\":true");
        assertThat(escalated).isNotEqualTo(payload);
        String forged = parts[0] + "." + b64(escalated) + "." + parts[2];
        String algNone = b64("{\"alg\":\"none\"}") + "." + b64(escalated) + ".";
        String expired = Jwts.builder().issuer("cbwa").subject("x").issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600))).claim("uidn", w.a.partner.getUserId()).claim("typ", "PARTNER")
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(JWT_SECRET)), Jwts.SIG.HS512).compact();
        String wrongKey = Jwts.builder().issuer("cbwa").subject("x").claim("typ", "ADMIN").claim("sa", true)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(new byte[64]), Jwts.SIG.HS512).compact();

        for (String bad : List.of(forged, algNone, wrongKey, "garbage", valid + "x")) {
            mockMvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + bad))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        }
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void disabledUserCannotRefreshAndImpersonationIsLimited() throws Exception {
        User staff = w.support;
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + staff.getMobile() + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = TestData.json(login).at("/data/refreshToken").asText();
        staff = userRepository.findById(staff.getId()).orElseThrow();
        staff.setStatus(UserStatus.DISABLED);
        userRepository.save(staff);
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + staff.getMobile() + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));

        // login-as: short-lived refresh, marked as impersonated, still a partner (no admin access)
        String imp = mockMvc.perform(post("/api/admin/partners/" + w.a.partner.getId() + "/login-as")
                        .header("Authorization", "Bearer " + w.superToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"support ticket 42\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(3600))
                .andExpect(jsonPath("$.data.user.impersonatedBy.uid").value(w.superAdmin.getUid()))
                .andReturn().getResponse().getContentAsString();
        String impToken = TestData.json(imp).at("/data/accessToken").asText();
        mockMvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + impToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/partner/wallet").header("Authorization", "Bearer " + impToken)).andExpect(status().isOk());
        // support staff may not impersonate
        mockMvc.perform(post("/api/admin/partners/" + w.a.partner.getId() + "/login-as")
                        .header("Authorization", "Bearer " + w.financeToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"curious\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mustChangePasswordGateBlocksEverythingElse() throws Exception {
        User u = userRepository.findById(w.a.partner.getUserId()).orElseThrow();
        u.setMustChangePassword(true);
        userRepository.save(u);
        String token = testData.accessToken(u);
        mockMvc.perform(get("/api/partner/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ brute force

    @Test
    void loginIsRateLimitedPerIpAndOtpLocksAfterFiveWrongCodes() throws Exception {
        int limited = 0;
        for (int i = 0; i < 12; i++) {
            int s = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mobile\":\"9876512345\",\"password\":\"guess" + i + "\"}")).andReturn().getResponse().getStatus();
            if (s == 429) {
                limited++;
            }
        }
        assertThat(limited).isGreaterThanOrEqualTo(2);

        testData.resetCaches();
        String ch = TestData.json(mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + w.support.getMobile() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/challengeId").asText();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"challengeId\":\"" + ch + "\",\"otp\":\"00000" + i + "\",\"newPassword\":\"NewPass123\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_OTP_INVALID"));
        }
        // 6th attempt is refused even before comparing the code
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + ch + "\",\"otp\":\"123456\",\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_OTP_EXPIRED"));
    }

    // ------------------------------------------------------------------ API keys

    @Test
    void apiKeyDisabledWhitelistAndRateLimit() throws Exception {
        ApiKey key = apiKeyRepository.findByApiKey(w.a.apiKey).orElseThrow();
        mockMvc.perform(apiGet(key, "wrong-secret")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("API_KEY_INVALID"));
        mockMvc.perform(get("/api/v1/public/wallet/balance").header("X-API-KEY", key.getApiKey()))
                .andExpect(status().isUnauthorized());

        key.setIpWhitelist(List.of("203.0.113.10"));
        apiKeyRepository.save(key);
        mockMvc.perform(apiGet(key, TortureFixtures.API_SECRET)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IP_NOT_WHITELISTED"));

        key = apiKeyRepository.findByApiKey(w.a.apiKey).orElseThrow();
        key.setIpWhitelist(List.of());
        key.setRateLimitPerMinute(2);
        apiKeyRepository.save(key);
        mockMvc.perform(apiGet(key, TortureFixtures.API_SECRET)).andExpect(status().isOk()).andExpect(header().string("X-RateLimit-Limit", "2"));
        mockMvc.perform(apiGet(key, TortureFixtures.API_SECRET)).andExpect(status().isOk());
        mockMvc.perform(apiGet(key, TortureFixtures.API_SECRET)).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        key = apiKeyRepository.findByApiKey(w.a.apiKey).orElseThrow();
        key.setStatus(ApiKeyStatus.DISABLED);
        apiKeyRepository.save(key);
        mockMvc.perform(apiGet(key, TortureFixtures.API_SECRET)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("API_KEY_DISABLED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder apiGet(ApiKey key, String secret) {
        return get("/api/v1/public/wallet/balance").header("X-API-KEY", key.getApiKey()).header("X-API-SECRET", secret);
    }

    // ------------------------------------------------------------------ uploads

    @Test
    void maliciousUploadsAreRejectedCleanly() throws Exception {
        byte[] fakePng = "<?php system($_GET['c']); ?>".getBytes();
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        byte[] big = new byte[3 * 1024 * 1024];
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(png, 0, big, 0, png.length);
        topup(new MockMultipartFile("screenshot", "proof.png", "image/png", fakePng), "UTR1")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
        topup(new MockMultipartFile("screenshot", "proof.svg", "image/svg+xml", svg), "UTR2")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
        topup(new MockMultipartFile("screenshot", "proof.png", "image/png", big), "UTR3")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        topup(new MockMultipartFile("screenshot", "proof.png", "image/png", new byte[0]), "UTR4")
                .andExpect(status().isBadRequest());
        // file name with path traversal is stored under a random name; the real PNG is accepted
        topup(new MockMultipartFile("screenshot", "../../../../etc/cron.d/x.png", "image/png", png), "UTR5")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.screenshotUrl").value(org.hamcrest.Matchers.startsWith("/api/files/")));
        mockMvc.perform(multipart("/api/admin/settings/company/logo").file(new MockMultipartFile("file", "logo.svg", "image/svg+xml", svg))
                        .header("Authorization", "Bearer " + w.superToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
    }

    private ResultActions topup(MockMultipartFile file, String utr) throws Exception {
        return mockMvc.perform(multipart("/api/partner/topup-requests").file(file)
                .param("amount", "100").param("paymentMode", "UPI").param("utr", utr + System.nanoTime())
                .param("paymentDate", LocalDate.now().toString())
                .header("Authorization", "Bearer " + w.a.token));
    }

    // ------------------------------------------------------------------ webhooks

    @Test
    void metaWebhookSurvivesBadSignaturesMalformedAndHugePayloads() throws Exception {
        mockMvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("X-Hub-Signature-256", "sha256=deadbeef")).andExpect(status().isUnauthorized());
        String malformed = "{\"entry\": [";
        mockMvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(malformed)
                        .header("X-Hub-Signature-256", "sha256=" + HashUtil.hmacSha256Hex(TortureFixtures.APP_SECRET, malformed)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        for (String body : List.of("[]", "null", "\"x\"", "{\"object\":\"whatsapp_business_account\",\"entry\":\"nope\"}",
                "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":123,\"changes\":[{\"field\":\"messages\",\"value\":{\"statuses\":\"x\",\"messages\":[{}]}}]}]}",
                "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"" + w.a.waba.getWabaId() + "\",\"changes\":[{\"field\":\"unknown_field\",\"value\":{}}]}]}",
                "{\"object\":\"whatsapp_business_account\",\"pad\":\"" + "x".repeat(2_000_000) + "\"}")) {
            mockMvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                    .header("X-Hub-Signature-256", "sha256=" + HashUtil.hmacSha256Hex(TortureFixtures.APP_SECRET, body)))
                    .andExpect(status().isOk());
        }
        // processing keeps working after junk: a normal inbound message is still accepted and stored
        String good = "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"" + w.a.waba.getWabaId() + "\",\"changes\":[{\"field\":\"messages\","
                + "\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"" + w.a.sender.getPhoneNumberId() + "\"},"
                + "\"contacts\":[{\"profile\":{\"name\":\"Asha\"},\"wa_id\":\"919812300001\"}],"
                + "\"messages\":[{\"from\":\"919812300001\",\"id\":\"wamid.tortureIN1\",\"timestamp\":\"" + Instant.now().getEpochSecond()
                + "\",\"type\":\"text\",\"text\":{\"body\":\"hello\"}}]}}]}]}";
        mockMvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(good)
                .header("X-Hub-Signature-256", "sha256=" + HashUtil.hmacSha256Hex(TortureFixtures.APP_SECRET, good))).andExpect(status().isOk());
        processor.processPending(); // junk rows are marked IGNORED/FAILED, never crash the processor
        assertThat(inboundLogRepository.findAll()).allMatch(l -> l.getStatus() != in.codebasket.cbwa.webhook.InboundLogStatus.RECEIVED);
        mockMvc.perform(get("/api/partner/inbox/conversations").header("Authorization", "Bearer " + w.a.token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[?(@.waId=='919812300001')]").exists());
    }

    @Test
    void razorpayWebhookRejectsBadSignatureAndIgnoresJunk() throws Exception {
        mockMvc.perform(put("/api/admin/settings/payment-gateway").header("Authorization", "Bearer " + w.superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activeGateway\":\"RAZORPAY\",\"razorpayKeyId\":\"rzp_test_1\",\"razorpayKeySecret\":\"SECRET_rzp_key\",\"razorpayWebhookSecret\":\"SECRET_rzp_hook\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/webhooks/payments/razorpay").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("X-Razorpay-Signature", "bad")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        mockMvc.perform(post("/api/webhooks/payments/razorpay").contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{bad", "{\"event\":\"payment.captured\",\"payload\":\"x\"}", "{\"event\":\"order.paid\",\"payload\":{\"payment\":{\"entity\":{\"id\":5}}}}",
                "{\"event\":\"something.new\"}")) {
            mockMvc.perform(post("/api/webhooks/payments/razorpay").contentType(MediaType.APPLICATION_JSON).content(body)
                            .header("X-Razorpay-Signature", HashUtil.hmacSha256Hex("SECRET_rzp_hook", body)))
                    .andExpect(status().isOk());
        }
        // secrets saved above are never returned
        String gw = mockMvc.perform(get("/api/admin/settings/payment-gateway").header("Authorization", "Bearer " + w.superToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.razorpayKeySecretSet").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(gw).doesNotContain("SECRET_rzp_key").doesNotContain("SECRET_rzp_hook");
        String meta = mockMvc.perform(get("/api/admin/meta/settings").header("Authorization", "Bearer " + w.superToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(meta).doesNotContain(TortureFixtures.APP_SECRET).doesNotContain(TortureFixtures.VERIFY_TOKEN);
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
