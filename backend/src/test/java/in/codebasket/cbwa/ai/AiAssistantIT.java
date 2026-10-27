package in.codebasket.cbwa.ai;

import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.messaging.ConversationWindowRepository;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageDirection;
import in.codebasket.cbwa.messaging.MessageRepository;
import in.codebasket.cbwa.messaging.MessageSource;
import in.codebasket.cbwa.meta.MetaSettings;
import in.codebasket.cbwa.meta.MetaSettingsRepository;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.wallet.WalletRepository;
import in.codebasket.cbwa.webhook.WebhookInboundProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiAssistantIT extends AbstractIntegrationTest {

    static final String APP_SECRET = "test-app-secret";
    static final String TO = "919812345678";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cbwa.webhook.async", () -> "false");
    }

    @MockitoBean AiModelClient model;
    @Autowired MetaSettingsRepository metaSettingsRepository;
    @Autowired CryptoService crypto;
    @Autowired WebhookInboundProcessor webhookProcessor;
    @Autowired SettingsService settings;
    @Autowired MessageRepository messageRepository;
    @Autowired AiUsageLogRepository usageRepository;
    @Autowired ConversationWindowRepository windowRepository;
    @Autowired WalletRepository walletRepository;

    Partner partner;
    WhatsappAccount waba;
    PhoneNumber sender;
    String token;

    @BeforeEach
    void setUp() {
        MetaSettings ms = metaSettingsRepository.findById(1L).orElseThrow();
        ms.setAppId("111");
        ms.setAppSecretEnc(crypto.encrypt(APP_SECRET));
        metaSettingsRepository.save(ms);
        partner = testData.partner(new BigDecimal("10"));
        waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        sender = testData.sender(waba, partner.getId(), SenderType.PLATFORM_SENDER);
        token = testData.accessToken(partner);
        settings.set(SettingKeys.AI, "enabled", "true");
        settings.set(SettingKeys.AI, "anthropic_api_key", "sk-ant-test");
        settings.set(SettingKeys.AI, "fee_per_reply", "0.10");
    }

    @Test
    void assistantCannotBeSwitchedOnWithoutKnowledgeAndFallback() throws Exception {
        mockMvc.perform(get("/api/partner/ai/assistant").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.platformEnabled").value(true))
                .andExpect(jsonPath("$.data.feePerReply").value("0.10"));
        send(put("/api/partner/ai/assistant"), """
                {"enabled":true,"businessName":"Sharma Sweets"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("fallbackMessage"));
        send(put("/api/partner/ai/assistant"), """
                {"enabled":true,"fallbackMessage":"Our team will reply soon"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("enabled"));
    }

    @Test
    void customerQuestionIsAnsweredFromKnowledgeWithPhotoAndFeeDebited() throws Exception {
        long menuId = setupAssistant();
        when(model.complete(any())).thenReturn(new AiModelClient.AiResult("Kaju katli ₹900/kg hai. Menu bhej raha hoon.",
                List.of(menuId, 99999L), false, "price in knowledge", 1200, 30, 0, 1100));

        webhook(inboundText("wamid.Q1", "kaju katli ka rate kya hai?"));

        await().atMost(Duration.ofSeconds(10)).until(() -> usageRepository.count() == 1);
        AiUsageLog log = usageRepository.findAll().get(0);
        assertThat(log.getOutcome()).isEqualTo(AiOutcome.REPLIED);
        assertThat(log.getCharged()).isEqualByComparingTo("0.10");
        assertThat(log.getCacheWriteTokens()).isEqualTo(1100);
        List<Message> out = messageRepository.findAll().stream().filter(m -> m.getDirection() == MessageDirection.OUTBOUND).toList();
        assertThat(out).hasSize(2); // text + the menu photo; the unknown attachment id is ignored
        assertThat(out).allMatch(m -> m.getSource() == MessageSource.AI);
        assertThat(walletRepository.findByPartnerId(partner.getId()).orElseThrow().getBalance()).isEqualByComparingTo("9.90");

        ArgumentCaptor<AiModelClient.AiRequest> req = ArgumentCaptor.forClass(AiModelClient.AiRequest.class);
        verify(model).complete(req.capture());
        assertThat(req.getValue().model()).isEqualTo("claude-haiku-5-5");
        assertThat(req.getValue().system()).contains("Answer ONLY with facts written in the KNOWLEDGE")
                .contains("Kaju katli: 900 rupees per kg").contains("[has attachment: image]");
        assertThat(req.getValue().message()).isEqualTo("kaju katli ka rate kya hai?");

        mockMvc.perform(get("/api/partner/ai/usage").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.replies").value(1))
                .andExpect(jsonPath("$.data.page.content[0].outcome").value("REPLIED"));
    }

    @Test
    void unknownAnswerSendsFallbackAndHandsChatToHuman() throws Exception {
        setupAssistant();
        when(model.complete(any())).thenReturn(new AiModelClient.AiResult("", List.of(), true, "not in knowledge", 1000, 10, 0, 0));

        webhook(inboundText("wamid.Q2", "do you deliver to Pune?"));
        await().atMost(Duration.ofSeconds(10)).until(() -> usageRepository.count() == 1);
        assertThat(usageRepository.findAll().get(0).getOutcome()).isEqualTo(AiOutcome.FALLBACK);
        assertThat(usageRepository.findAll().get(0).getCharged()).isEqualByComparingTo("0");
        Message fallback = messageRepository.findAll().stream().filter(m -> m.getDirection() == MessageDirection.OUTBOUND)
                .findFirst().orElseThrow();
        assertThat(fallback.getPreview()).isEqualTo("Our team will reply soon");

        mockMvc.perform(get("/api/partner/inbox/conversations").header("Authorization", TestData.bearer(token)))
                .andExpect(jsonPath("$.data.content[0].aiPaused").value(true))
                .andExpect(jsonPath("$.data.content[0].needsHuman").value(true));

        // Paused chat: the next message is not sent to the AI at all.
        webhook(inboundText("wamid.Q3", "hello?"));
        Thread.sleep(500);
        assertThat(usageRepository.count()).isEqualTo(1);

        send(patch("/api/partner/inbox/conversations/" + sender.getId() + "/" + TO + "/ai"), "{\"paused\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.aiPaused").value(false))
                .andExpect(jsonPath("$.data.needsHuman").value(false));
    }

    @Test
    void handoverKeywordSkipsTheModelAndLowBalanceIsNotCharged() throws Exception {
        setupAssistant();
        webhook(inboundText("wamid.H1", "I want to talk to a human agent"));
        await().atMost(Duration.ofSeconds(10)).until(() -> usageRepository.count() == 1);
        assertThat(usageRepository.findAll().get(0).getOutcome()).isEqualTo(AiOutcome.HANDOVER);
        verify(model, never()).complete(any());

        // Out of money: no AI call, chat flagged for a human.
        jdbc.update("update wallets set balance = 0.05 where partner_id = ?", partner.getId());
        jdbc.update("update conversation_windows set ai_paused = false, needs_human = false");
        webhook(inboundText("wamid.H2", "kaju katli price?"));
        await().atMost(Duration.ofSeconds(10)).until(() -> usageRepository.count() == 2);
        assertThat(usageRepository.findAll().get(1).getOutcome()).isEqualTo(AiOutcome.SKIPPED);
        verify(model, never()).complete(any());
    }

    @Test
    void stopKeywordAndPlatformSwitchOffAreIgnoredByAi() throws Exception {
        setupAssistant();
        webhook(inboundText("wamid.S1", "STOP"));
        settings.set(SettingKeys.AI, "enabled", "false");
        webhook(inboundText("wamid.S2", "kaju katli price?"));
        Thread.sleep(500);
        assertThat(usageRepository.count()).isZero();
        verify(model, never()).complete(any());
    }

    @Test
    void playgroundAnswersWithoutChargingAndPartnersAreIsolated() throws Exception {
        long menuId = setupAssistant();
        when(model.complete(any())).thenReturn(new AiModelClient.AiResult("Yes, here is the menu", List.of(menuId), false, "menu", 900, 12, 0, 0));
        send(post("/api/partner/ai/test"), """
                {"message":"menu dikhao","history":[{"role":"customer","text":"hi"},{"role":"business","text":"Hello!"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("Yes, here is the menu"))
                .andExpect(jsonPath("$.data.attachments[0].media.type").value("image"));
        assertThat(walletRepository.findByPartnerId(partner.getId()).orElseThrow().getBalance()).isEqualByComparingTo("10");

        Partner other = testData.partner(new BigDecimal("5"));
        String otherToken = testData.accessToken(other);
        mockMvc.perform(get("/api/partner/ai/knowledge").header("Authorization", TestData.bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(put("/api/partner/ai/knowledge/" + menuId).header("Authorization", TestData.bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isNotFound());

        settings.set(SettingKeys.AI, "anthropic_api_key", null);
        send(post("/api/partner/ai/test"), "{\"message\":\"hi\"}")
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_NOT_AVAILABLE"));
    }

    // ---- helpers

    /** Knowledge with a photo, fallback message and handover keyword; returns the menu item id. */
    private long setupAssistant() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        String upload = mockMvc.perform(multipart("/api/partner/ai/knowledge/media")
                        .file(new MockMultipartFile("file", "menu.png", "image/png", png))
                        .header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("image"))
                .andReturn().getResponse().getContentAsString();
        String fileUid = TestData.json(upload).path("data").path("fileUid").asText();
        String created = send(post("/api/partner/ai/knowledge"), """
                {"title":"Menu and prices","content":"Kaju katli: 900 rupees per kg. Rasgulla: 300 rupees per kg.","fileUid":"%s"}"""
                .formatted(fileUid)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        send(put("/api/partner/ai/assistant"), """
                {"enabled":true,"businessName":"Sharma Sweets","fallbackMessage":"Our team will reply soon",
                 "handoverKeywords":["human","agent"],"language":"AUTO","tone":"FRIENDLY"}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true));
        return TestData.json(created).path("data").path("id").asLong();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String json) throws Exception {
        return mockMvc.perform(b.header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void webhook(String body) throws Exception {
        String sig = "sha256=" + HashUtil.hmacSha256Hex(APP_SECRET.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", sig)).andExpect(status().isOk());
        webhookProcessor.processPending();
    }

    private String inboundText(String wamid, String text) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"%s","changes":[{"field":"messages","value":
                {"messaging_product":"whatsapp","metadata":{"display_phone_number":"919876543210","phone_number_id":"%s"},
                 "contacts":[{"profile":{"name":"Ravi Kumar"},"wa_id":"%s"}],
                 "messages":[{"from":"%s","id":"%s","timestamp":"%d","type":"text","text":{"body":"%s"}}]}}]}]}"""
                .formatted(waba.getWabaId(), sender.getPhoneNumberId(), TO, TO, wamid, Instant.now().getEpochSecond(), text);
    }
}
