package in.codebasket.cbwa.messaging;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.meta.MetaSettings;
import in.codebasket.cbwa.meta.MetaSettingsRepository;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.template.ComponentType;
import in.codebasket.cbwa.template.ParameterFormat;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateComponent;
import in.codebasket.cbwa.template.TemplateComponentRepository;
import in.codebasket.cbwa.template.TemplateRepository;
import in.codebasket.cbwa.template.TemplateStatus;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.wallet.Wallet;
import in.codebasket.cbwa.wallet.WalletRepository;
import in.codebasket.cbwa.wallet.WalletTransaction;
import in.codebasket.cbwa.wallet.WalletTransactionRepository;
import in.codebasket.cbwa.wallet.WalletTxnType;
import in.codebasket.cbwa.webhook.WebhookInboundProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessagingIT extends AbstractIntegrationTest {

    static final WireMockServer META = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String APP_SECRET = "test-app-secret";
    static final String TO = "919812345678";

    static {
        META.start();
    }

    @DynamicPropertySource
    static void metaProps(DynamicPropertyRegistry r) {
        r.add("cbwa.meta.graph-base-url", () -> "http://localhost:" + META.port());
        r.add("cbwa.webhook.async", () -> "false");
    }

    @AfterAll
    static void stop() {
        META.resetAll();
    }

    @Autowired MetaSettingsRepository metaSettingsRepository;
    @Autowired CryptoService crypto;
    @Autowired TemplateRepository templateRepository;
    @Autowired TemplateComponentRepository componentRepository;
    @Autowired MessageQueueWorker worker;
    @Autowired WebhookInboundProcessor webhookProcessor;
    @Autowired MessageRepository messageRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository txnRepository;
    @Autowired SettingsService settingsService;

    Partner partner;
    WhatsappAccount waba;
    PhoneNumber sender;
    String token;
    Template utility;
    Template marketing;

    @BeforeEach
    void setUp() {
        META.resetAll();
        MetaSettings ms = metaSettingsRepository.findById(1L).orElseThrow();
        ms.setAppId("111");
        ms.setAppSecretEnc(crypto.encrypt(APP_SECRET));
        ms.setWebhookVerifyTokenEnc(crypto.encrypt("verify-me"));
        metaSettingsRepository.save(ms);

        partner = testData.partner(new BigDecimal("100"));
        waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        sender = testData.sender(waba, partner.getId(), SenderType.PLATFORM_SENDER);
        token = testData.accessToken(partner);
        testData.price("IN", MessageCategory.UTILITY, "0.20");
        testData.price("IN", MessageCategory.MARKETING, "0.80");
        utility = template("order_update", MessageCategory.UTILITY, "Hello {{1}}, your order {{2}} is shipped");
        marketing = template("diwali_offer", MessageCategory.MARKETING, "Big sale for {{1}}");
    }

    // ------------------------------------------------------------------ send + charging

    @Test
    void templateSendHoldsWorkerSendsAndDeliveredChargesExactlyOnce() throws Exception {
        stubSend("wamid.A1");
        String uid = sendTemplate(utility, "Ravi", "#123").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.chargeStatus").value("HELD"))
                .andExpect(jsonPath("$.data.price").value("0.2000"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"uid\":\"([A-Z0-9]+)\".*", "$1");
        assertWallet("99.8", "0.2");

        assertThat(worker.runOnce()).isEqualTo(1);
        Message m = messageRepository.findByUid(uid).orElseThrow();
        assertThat(m.getWamid()).isEqualTo("wamid.A1");
        assertThat(m.getStatus()).isEqualTo(MessageStatus.ACCEPTED);
        META.verify(postRequestedFor(urlEqualTo("/v25.0/" + sender.getPhoneNumberId() + "/messages")));

        webhook(statusPayload("wamid.A1", "sent", 1000, true));
        webhook(statusPayload("wamid.A1", "delivered", 1001, true));
        webhook(statusPayload("wamid.A1", "delivered", 1001, true)); // exact duplicate delivery
        webhook(statusPayload("wamid.A1", "delivered", 1002, true)); // same status, different body
        webhook(statusPayload("wamid.A1", "read", 1003, true));

        m = messageRepository.findByUid(uid).orElseThrow();
        assertThat(m.getStatus()).isEqualTo(MessageStatus.READ);
        assertThat(m.getChargeStatus()).isEqualTo(ChargeStatus.CHARGED);
        assertWallet("99.8", "0");
        assertThat(ledger(WalletTxnType.DEBIT)).hasSize(1);
    }

    @Test
    void failedStatusRefundsHoldWhenChargingOnDelivered() throws Exception {
        stubSend("wamid.F1");
        sendTemplate(utility, "A", "B").andExpect(status().isCreated());
        worker.runOnce();
        webhook(statusPayload("wamid.F1", "sent", 1000, true));
        webhook(failedPayload("wamid.F1", 131026));
        Message m = messageRepository.findByWamid("wamid.F1").orElseThrow();
        assertThat(m.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(m.getChargeStatus()).isEqualTo(ChargeStatus.REFUNDED);
        assertThat(m.getErrorMessage()).contains("not on WhatsApp");
        assertWallet("100", "0");
        assertThat(ledger(WalletTxnType.REFUND)).hasSize(1);
    }

    @Test
    void chargeOnSentFinalizesOnSentAndDoesNotRefundLaterFailure() throws Exception {
        jdbc.update("update settings set value = 'SENT' where group_code = 'CHARGING' and setting_key = 'charge_on'");
        settingsService.evictAll();
        stubSend("wamid.S1");
        sendTemplate(utility, "A", "B").andExpect(status().isCreated());
        worker.runOnce();
        webhook(statusPayload("wamid.S1", "sent", 1000, true));
        assertThat(messageRepository.findByWamid("wamid.S1").orElseThrow().getChargeStatus()).isEqualTo(ChargeStatus.CHARGED);
        webhook(failedPayload("wamid.S1", 131026));
        Message m = messageRepository.findByWamid("wamid.S1").orElseThrow();
        assertThat(m.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(m.getChargeStatus()).isEqualTo(ChargeStatus.CHARGED);
        assertWallet("99.8", "0");
    }

    @Test
    void notBillableTemplateIsRefundedByDefault() throws Exception {
        stubSend("wamid.NB");
        sendTemplate(utility, "A", "B").andExpect(status().isCreated());
        worker.runOnce();
        webhook(statusPayload("wamid.NB", "sent", 1000, false));
        assertThat(messageRepository.findByWamid("wamid.NB").orElseThrow().getChargeStatus()).isEqualTo(ChargeStatus.REFUNDED);
        assertWallet("100", "0");
    }

    @Test
    void insufficientBalanceRejectsAndQueuesNothing() throws Exception {
        jdbc.update("update wallets set balance = 0.10 where partner_id = ?", partner.getId());
        sendTemplate(utility, "A", "B").andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("WALLET_INSUFFICIENT_BALANCE"));
        assertThat(messageRepository.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from message_queue", Integer.class)).isZero();
    }

    @Test
    void retryOn500ThenSuccess() throws Exception {
        String path = "/v25.0/" + sender.getPhoneNumberId() + "/messages";
        META.stubFor(post(urlEqualTo(path)).inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":{\"message\":\"boom\",\"code\":1}}"))
                .willSetStateTo("ok"));
        META.stubFor(post(urlEqualTo(path)).inScenario("retry").whenScenarioStateIs("ok")
                .willReturn(json("{\"messages\":[{\"id\":\"wamid.R1\"}]}")));
        sendTemplate(utility, "A", "B").andExpect(status().isCreated());
        worker.runOnce();
        Message m = messageRepository.findAll().get(0);
        assertThat(m.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(jdbc.queryForObject("select attempts from message_queue", Integer.class)).isEqualTo(1);
        jdbc.update("update message_queue set next_attempt_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND");
        worker.runOnce();
        m = messageRepository.findAll().get(0);
        assertThat(m.getStatus()).isEqualTo(MessageStatus.ACCEPTED);
        assertThat(m.getWamid()).isEqualTo("wamid.R1");
    }

    @Test
    void permanentErrorFailsAndReleasesHold() throws Exception {
        META.stubFor(post(urlEqualTo("/v25.0/" + sender.getPhoneNumberId() + "/messages"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"(#131026) Message undeliverable\",\"code\":131026}}")));
        sendTemplate(utility, "A", "B").andExpect(status().isCreated());
        worker.runOnce();
        Message m = messageRepository.findAll().get(0);
        assertThat(m.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(m.getChargeStatus()).isEqualTo(ChargeStatus.NONE);
        assertThat(m.getErrorCode()).isEqualTo(131026);
        assertWallet("100", "0");
        assertThat(ledger(WalletTxnType.RELEASE)).hasSize(1);
        assertThat(jdbc.queryForObject("select status from message_queue", String.class)).isEqualTo("DEAD");
    }

    @Test
    void variablesMismatchIsRejected() throws Exception {
        perform("/api/partner/messages/send", """
                {"senderId":%d,"to":"%s","type":"template","template":{"templateId":%d,"variables":{"body":["only one"]}}}
                """.formatted(sender.getId(), TO, utility.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TEMPLATE_VARIABLES_MISMATCH"));
    }

    // ------------------------------------------------------------------ window, inbound, opt-out

    @Test
    void freeFormNeedsOpenWindowWhichInboundMessageOpens() throws Exception {
        String text = """
                {"senderId":%d,"to":"%s","type":"text","text":{"body":"Hi there"}}""".formatted(sender.getId(), TO);
        perform("/api/partner/messages/send", text).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WINDOW_CLOSED"));
        webhook(inboundText("wamid.IN1", "Hello business"));
        perform("/api/partner/messages/send", text).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("SERVICE"))
                .andExpect(jsonPath("$.data.chargeStatus").value("NONE"));

        mockMvc.perform(get("/api/partner/inbox/conversations").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].waId").value(TO))
                .andExpect(jsonPath("$.data.content[0].windowOpen").value(true))
                .andExpect(jsonPath("$.data.content[0].contactName").value("Ravi Kumar"));
        mockMvc.perform(get("/api/partner/inbox/conversations/" + sender.getId() + "/" + TO + "/messages")
                        .header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].direction").value("INBOUND"));
    }

    @Test
    void duplicateInboundDeliveryIsStoredOnce() throws Exception {
        String body = inboundText("wamid.DUP", "Hello");
        webhook(body);
        webhook(body);
        assertThat(jdbc.queryForObject("select count(*) from webhook_inbound_logs", Integer.class)).isEqualTo(1);
        webhook(inboundText("wamid.DUP", "Hello again")); // different body, same wamid
        assertThat(messageRepository.findAll().stream().filter(m -> "wamid.DUP".equals(m.getWamid())).count()).isEqualTo(1);
    }

    @Test
    void stopKeywordCreatesOptOutAndBlocksMarketing() throws Exception {
        webhook(inboundText("wamid.STOP", " stop "));
        assertThat(jdbc.queryForObject("select count(*) from opt_outs where partner_id = ? and phone = ?", Integer.class,
                partner.getId(), TO)).isEqualTo(1);
        sendTemplate(marketing, "X").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECIPIENT_OPTED_OUT"));
        // utility templates are still allowed
        stubSend("wamid.U");
        sendTemplate(utility, "A", "B").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ webhook security & isolation

    @Test
    void webhookSignatureAndVerification() throws Exception {
        String body = statusPayload("wamid.X", "sent", 1, true);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON).content(body).header("X-Hub-Signature-256", "sha256=deadbeef"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from webhook_inbound_logs", Integer.class)).isZero();

        mockMvc.perform(get("/api/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "verify-me")
                .param("hub.challenge", "12345")).andExpect(status().isOk()).andExpect(content().string("12345"));
        mockMvc.perform(get("/api/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "wrong")
                .param("hub.challenge", "12345")).andExpect(status().isForbidden());
    }

    @Test
    void partnerCannotReadAnotherPartnersMessage() throws Exception {
        stubSend("wamid.O1");
        String body = sendTemplate(utility, "A", "B").andReturn().getResponse().getContentAsString();
        String uid = TestData.json(body).at("/data/uid").asText();
        mockMvc.perform(get("/api/partner/messages/" + uid).header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.uid").value(uid));
        Partner other = testData.partner(BigDecimal.TEN);
        mockMvc.perform(get("/api/partner/messages/" + uid).header("Authorization", TestData.bearer(testData.accessToken(other))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/partner/messages").header("Authorization", TestData.bearer(testData.accessToken(other))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void estimateShowsCostAndBalance() throws Exception {
        perform("/api/partner/messages/estimate", """
                {"senderId":%d,"type":"template","templateId":%d,"recipients":["9812345678","919812345679","bad"]}
                """.formatted(sender.getId(), utility.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipients").value(2))
                .andExpect(jsonPath("$.data.invalid").value(1))
                .andExpect(jsonPath("$.data.total").value("0.4000"))
                .andExpect(jsonPath("$.data.sufficient").value(true));
    }

    // ------------------------------------------------------------------ account-level webhooks, jobs, admin

    @Autowired HoldTimeoutJob holdTimeoutJob;
    @Autowired in.codebasket.cbwa.sender.PhoneNumberRepository phoneNumberRepository;

    @Test
    void templateStatusWebhookPausesTemplateAndBlocksSending() throws Exception {
        webhookField("message_template_status_update", """
                {"event":"PAUSED","message_template_id":%s,"message_template_name":"order_update","message_template_language":"en","reason":"LOW_QUALITY"}
                """.formatted(utility.getMetaTemplateId()));
        assertThat(templateRepository.findById(utility.getId()).orElseThrow().getStatus()).isEqualTo(TemplateStatus.PAUSED);
        sendTemplate(utility, "A", "B").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_APPROVED"));
        assertThat(jdbc.queryForObject("select count(*) from notifications where partner_id is null and type = 'TEMPLATE_STATUS'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void phoneQualityFlaggedPausesSenderAndCampaigns() throws Exception {
        webhookField("phone_number_quality_update", """
                {"display_phone_number":"919876543210","event":"FLAGGED","current_limit":"TIER_1K"}""");
        PhoneNumber pn = phoneNumberRepository.findById(sender.getId()).orElseThrow();
        assertThat(pn.isPaused()).isTrue();
        assertThat(pn.getQualityRating()).isEqualTo("RED");
        assertThat(pn.getMessagingLimitTier()).isEqualTo("TIER_1K");
        sendTemplate(utility, "A", "B").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SENDER_PAUSED"));
        assertThat(jdbc.queryForObject("select count(*) from notifications where partner_id = ? and type = 'SENDER_QUALITY'",
                Integer.class, partner.getId())).isEqualTo(1);
    }

    @Test
    void holdTimeoutRefundsMessagesWithoutConfirmation() throws Exception {
        sendTemplate(utility, "A", "B").andExpect(status().isCreated());
        jdbc.update("update messages set created_at = UTC_TIMESTAMP(6) - INTERVAL 80 HOUR");
        holdTimeoutJob.run();
        Message m = messageRepository.findAll().get(0);
        assertThat(m.getChargeStatus()).isEqualTo(ChargeStatus.REFUNDED);
        assertThat(m.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(jdbc.queryForObject("select status from message_queue", String.class)).isEqualTo("DEAD");
        assertWallet("100", "0");
    }

    @Test
    void adminSeesAllMessagesAndCanExport() throws Exception {
        stubSend("wamid.ADM");
        sendTemplate(utility, "A", "B").andExpect(status().isCreated());
        String admin = testData.accessToken(testData.superAdmin());
        mockMvc.perform(get("/api/admin/messages").param("partnerId", partner.getId().toString())
                        .header("Authorization", TestData.bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].partner.businessName").value(partner.getBusinessName()));
        mockMvc.perform(get("/api/admin/messages/export").param("format", "xlsx").header("Authorization", TestData.bearer(admin)))
                .andExpect(status().isOk());
        String csv = mockMvc.perform(get("/api/partner/messages/export").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Hello A, your order B is shipped");
        mockMvc.perform(get("/api/admin/messages").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isForbidden());
    }

    private void webhookField(String field, String value) throws Exception {
        webhook("""
                {"object":"whatsapp_business_account","entry":[{"id":"%s","changes":[{"field":"%s","value":%s}]}]}"""
                .formatted(waba.getWabaId(), field, value));
    }

    // ------------------------------------------------------------------ helpers

    private Template template(String name, MessageCategory category, String body) {
        Template t = new Template();
        t.setUid(Ulids.next());
        t.setWhatsappAccountId(waba.getId());
        t.setMetaTemplateId(String.valueOf(System.nanoTime()));
        t.setName(name);
        t.setLanguage("en");
        t.setCategory(category);
        t.setParameterFormat(ParameterFormat.POSITIONAL);
        t.setStatus(TemplateStatus.APPROVED);
        templateRepository.save(t);
        TemplateComponent c = new TemplateComponent();
        c.setTemplateId(t.getId());
        c.setPosition(1);
        c.setType(ComponentType.BODY);
        c.setText(body);
        componentRepository.save(c);
        return t;
    }

    private ResultActions sendTemplate(Template t, String... vars) throws Exception {
        String list = String.join(",", java.util.Arrays.stream(vars).map(v -> "\"" + v + "\"").toList());
        return perform("/api/partner/messages/send", """
                {"senderId":%d,"to":"%s","type":"template","clientReference":"REF-1",
                 "template":{"templateId":%d,"variables":{"body":[%s]}}}""".formatted(sender.getId(), TO, t.getId(), list));
    }

    private ResultActions perform(String url, String json) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void stubSend(String wamid) {
        META.stubFor(post(urlPathMatching("/v25.0/.*/messages")).willReturn(json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"" + wamid + "\"}]}")));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private void webhook(String body) throws Exception {
        String sig = "sha256=" + HashUtil.hmacSha256Hex(APP_SECRET.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON).content(body).header("X-Hub-Signature-256", sig))
                .andExpect(status().isOk());
        webhookProcessor.processPending();
    }

    private String envelope(String value) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"%s","changes":[{"field":"messages","value":%s}]}]}"""
                .formatted(waba.getWabaId(), value);
    }

    private String metadata() {
        return "\"metadata\":{\"display_phone_number\":\"919876543210\",\"phone_number_id\":\"" + sender.getPhoneNumberId() + "\"}";
    }

    private String statusPayload(String wamid, String status, long ts, boolean billable) {
        return envelope("""
                {"messaging_product":"whatsapp",%s,"statuses":[{"id":"%s","status":"%s","timestamp":"%d","recipient_id":"%s",
                 "pricing":{"billable":%s,"pricing_model":"PMP","category":"utility","type":"regular"}}]}"""
                .formatted(metadata(), wamid, status, Instant.now().getEpochSecond() + ts, TO, billable));
    }

    private String failedPayload(String wamid, int code) {
        return envelope("""
                {"messaging_product":"whatsapp",%s,"statuses":[{"id":"%s","status":"failed","timestamp":"%d","recipient_id":"%s",
                 "errors":[{"code":%d,"title":"Message undeliverable"}]}]}"""
                .formatted(metadata(), wamid, Instant.now().getEpochSecond() + 5000, TO, code));
    }

    private String inboundText(String wamid, String text) {
        return envelope("""
                {"messaging_product":"whatsapp",%s,"contacts":[{"profile":{"name":"Ravi Kumar"},"wa_id":"%s"}],
                 "messages":[{"from":"%s","id":"%s","timestamp":"%d","type":"text","text":{"body":"%s"}}]}"""
                .formatted(metadata(), TO, TO, wamid, Instant.now().getEpochSecond(), text));
    }

    private void assertWallet(String balance, String held) {
        Wallet w = walletRepository.findByPartnerId(partner.getId()).orElseThrow();
        assertThat(w.getBalance()).isEqualByComparingTo(balance);
        assertThat(w.getHeldAmount()).isEqualByComparingTo(held);
    }

    private List<WalletTransaction> ledger(WalletTxnType type) {
        Wallet w = walletRepository.findByPartnerId(partner.getId()).orElseThrow();
        return txnRepository.findByWalletIdOrderByIdAsc(w.getId()).stream().filter(t -> t.getType() == type).toList();
    }
}
