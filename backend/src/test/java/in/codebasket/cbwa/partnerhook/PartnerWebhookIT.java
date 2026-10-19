package in.codebasket.cbwa.partnerhook;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerEvent;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartnerWebhookIT extends AbstractIntegrationTest {

    static final WireMockServer WM = new WireMockServer(options().dynamicPort());

    static {
        WM.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cbwa.partner-webhooks.allow-insecure", () -> "true");
    }

    @AfterAll
    static void stop() {
        WM.resetAll();
    }

    @Autowired
    ApplicationEventPublisher events;
    @Autowired
    PartnerWebhookDispatcher dispatcher;
    @Autowired
    PartnerWebhookService service;

    @BeforeEach
    void reset() {
        WM.resetAll();
    }

    @Test
    void subscribedEventIsDeliveredWithValidSignature() throws Exception {
        WM.stubFor(post(urlEqualTo("/hook")).willReturn(aResponse().withStatus(200).withBody("ok")));
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        String body = mockMvc.perform(put("/api/partner/webhook").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + WM.baseUrl() + "/hook\",\"events\":[\"message.status\"],\"isActive\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secret = TestData.json(body).at("/data/secret").asText();
        assertThat(secret).startsWith("whsec_");

        // a second save never returns the secret again
        mockMvc.perform(put("/api/partner/webhook").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + WM.baseUrl() + "/hook\",\"events\":[\"message.status\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.secret").doesNotExist())
                .andExpect(jsonPath("$.data.secretSet").value(true));

        events.publishEvent(new PartnerEvent(p.getId(), PartnerEvent.MESSAGE_STATUS, Map.of("id", "01ABC", "status", "delivered")));
        events.publishEvent(new PartnerEvent(p.getId(), PartnerEvent.TEMPLATE_STATUS, Map.of("x", 1))); // not subscribed
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        List<LoggedRequest> reqs = WM.findAll(postRequestedFor(urlEqualTo("/hook")));
        assertThat(reqs).hasSize(1);
        LoggedRequest req = reqs.get(0);
        String ts = req.getHeader("X-CB-Timestamp");
        String expected = WebhookSender.sign(secret, Long.parseLong(ts), req.getBodyAsString());
        assertThat(req.getHeader("X-CB-Signature")).isEqualTo(expected);
        assertThat(req.getHeader("X-CB-Event")).isEqualTo("message.status");
        assertThat(TestData.json(req.getBodyAsString()).at("/data/status").asText()).isEqualTo("delivered");
        assertThat(TestData.json(req.getBodyAsString()).at("/eventId").asText()).isEqualTo(req.getHeader("X-CB-Event-Id"));

        mockMvc.perform(get("/api/partner/webhook/logs").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void failuresAreRetriedOnScheduleThenDead() throws Exception {
        WM.stubFor(post(urlEqualTo("/fail")).willReturn(aResponse().withStatus(500).withBody("boom")));
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        mockMvc.perform(put("/api/partner/webhook").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + WM.baseUrl() + "/fail\",\"events\":[\"wallet.low_balance\"]}"))
                .andExpect(status().isOk());
        events.publishEvent(new PartnerEvent(p.getId(), PartnerEvent.WALLET_LOW_BALANCE, Map.of("balance", "1.0000")));

        long[] expectedDelaysSeconds = {60, 300, 900, 3600, 21600};
        for (int attempt = 1; attempt <= 6; attempt++) {
            Instant before = Instant.now();
            assertThat(dispatcher.dispatchDue()).isEqualTo(1);
            Map<String, Object> row = jdbc.queryForMap("select status, attempt, response_code, next_retry_at from webhook_delivery_logs");
            assertThat(((Number) row.get("attempt")).intValue()).isEqualTo(attempt);
            assertThat(((Number) row.get("response_code")).intValue()).isEqualTo(500);
            if (attempt <= 5) {
                assertThat(row.get("status")).isEqualTo("FAILED");
                Instant next = ((java.time.LocalDateTime) row.get("next_retry_at")).toInstant(java.time.ZoneOffset.UTC);
                assertThat(next).isAfterOrEqualTo(before.plusSeconds(expectedDelaysSeconds[attempt - 1] - 2));
                assertThat(dispatcher.dispatchDue()).isZero(); // not due yet
                jdbc.update("update webhook_delivery_logs set next_retry_at = date_sub(utc_timestamp(6), interval 1 second)");
            } else {
                assertThat(row.get("status")).isEqualTo("DEAD");
                assertThat(row.get("next_retry_at")).isNull();
            }
        }
        assertThat(WM.findAll(postRequestedFor(urlEqualTo("/fail")))).hasSize(6);
        assertThat(jdbc.queryForObject("select consecutive_failures from partner_webhooks", Integer.class)).isEqualTo(6);

        // manual retry puts it back in the queue
        Long id = jdbc.queryForObject("select id from webhook_delivery_logs", Long.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/webhook/logs/" + id + "/retry")
                        .header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void testEventAndPartnerIsolation() throws Exception {
        WM.stubFor(post(urlEqualTo("/test")).willReturn(aResponse().withStatus(204)));
        Partner a = testData.partner(BigDecimal.ZERO);
        Partner b = testData.partner(BigDecimal.ZERO);
        String tokenA = testData.accessToken(a);
        String tokenB = testData.accessToken(b);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/webhook/test")
                        .header("Authorization", TestData.bearer(tokenA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/partner/webhook").header("Authorization", TestData.bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + WM.baseUrl() + "/test\",\"events\":[\"message.inbound\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/webhook/test")
                        .header("Authorization", TestData.bearer(tokenA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.responseCode").value(204));
        assertThat(WM.findAll(postRequestedFor(urlEqualTo("/test"))).get(0).getHeader("X-CB-Event")).isEqualTo("test.ping");

        Long logId = jdbc.queryForObject("select id from webhook_delivery_logs", Long.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/partner/webhook/logs/" + logId + "/retry")
                        .header("Authorization", TestData.bearer(tokenB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/partner/webhook/logs").header("Authorization", TestData.bearer(tokenB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(put("/api/partner/webhook").header("Authorization", TestData.bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"" + WM.baseUrl() + "/test\",\"events\":[\"bogus\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void developerInfoAndPostmanCollection() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        mockMvc.perform(get("/api/partner/developer/info").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseUrl").value(org.hamcrest.Matchers.endsWith("/api/v1/public")))
                .andExpect(jsonPath("$.data.rateLimitPerMinute").value(600));
        mockMvc.perform(get("/api/partner/developer/postman").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.schema").value(org.hamcrest.Matchers.containsString("v2.1.0")))
                .andExpect(jsonPath("$.item.length()").value(10));
    }
}
