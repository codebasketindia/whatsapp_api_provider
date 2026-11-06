package in.codebasket.cbwa.registration;

import in.codebasket.cbwa.common.constants.PassStatus;
import in.codebasket.cbwa.common.entity.Registration;

import in.codebasket.cbwa.common.repository.RegistrationRepository;

import in.codebasket.cbwa.common.service.crypto.CryptoService;
import in.codebasket.cbwa.common.utils.HashUtil;
import in.codebasket.cbwa.common.constants.BillingMode;
import in.codebasket.cbwa.common.constants.MessageCategory;
import in.codebasket.cbwa.common.constants.SenderType;
import in.codebasket.cbwa.common.utils.Ulids;
import in.codebasket.cbwa.common.repository.ContactRepository;
import in.codebasket.cbwa.common.entity.Message;
import in.codebasket.cbwa.common.repository.MessageRepository;
import in.codebasket.cbwa.common.constants.MessageSource;
import in.codebasket.cbwa.common.constants.MessageType;
import in.codebasket.cbwa.common.entity.MetaSettings;
import in.codebasket.cbwa.common.repository.MetaSettingsRepository;
import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.repository.ScheduledMessageRepository;
import in.codebasket.cbwa.common.entity.PhoneNumber;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.common.constants.ComponentType;
import in.codebasket.cbwa.common.constants.ParameterFormat;
import in.codebasket.cbwa.common.entity.Template;
import in.codebasket.cbwa.common.entity.TemplateComponent;
import in.codebasket.cbwa.common.repository.TemplateComponentRepository;
import in.codebasket.cbwa.common.repository.TemplateRepository;
import in.codebasket.cbwa.common.constants.TemplateStatus;
import in.codebasket.cbwa.common.constants.WabaOwnerType;
import in.codebasket.cbwa.common.entity.WhatsappAccount;
import in.codebasket.cbwa.webhook.service.WebhookInboundProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationIT extends AbstractIntegrationTest {

    static final String APP_SECRET = "test-app-secret";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cbwa.webhook.async", () -> "false");
    }

    @Autowired MetaSettingsRepository metaSettingsRepository;
    @Autowired CryptoService crypto;
    @Autowired WebhookInboundProcessor webhookProcessor;
    @Autowired RegistrationRepository registrationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired ScheduledMessageRepository scheduledRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired TemplateRepository templateRepository;
    @Autowired TemplateComponentRepository componentRepository;

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
        partner = testData.partner(new BigDecimal("50"));
        waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        sender = testData.sender(waba, partner.getId(), SenderType.PLATFORM_SENDER);
        token = testData.accessToken(partner);
        testData.price("IN", MessageCategory.UTILITY, "0.20");
    }

    @Test
    void registrationCreatesPassFollowUpsAndClaimCodeSendsPassOnWhatsapp() throws Exception {
        Long groupId = TestData.json(send(post("/api/partner/contact-groups"), "{\"name\":\"Hajj 2027\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        String form = send(post("/api/partner/registration-forms"), """
                {"title":"Free Harmain Ziyarat Pass","slug":"harmain-pass","senderId":%d,"passPrefix":"HZP",
                 "contactGroupId":%d,"contactTags":["hajj-2027"],
                 "followUps":[{"label":"Reminder","delayMinutes":60,
                   "message":{"type":"text","text":{"body":"Salaam {name}, your pass {passNo} is ready"}}}]}"""
                .formatted(sender.getId(), groupId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.publicUrl").value(org.hamcrest.Matchers.endsWith("/r/harmain-pass")))
                .andExpect(jsonPath("$.data.passDesign").value("harmain"))
                .andReturn().getResponse().getContentAsString();
        long formId = TestData.json(form).path("data").path("id").asLong();

        mockMvc.perform(get("/api/public/register/harmain-pass")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Free Harmain Ziyarat Pass"))
                .andExpect(jsonPath("$.data.open").value(true));

        String res = register("harmain-pass", "Mohammed Shahid", "98123 45678").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passNo").value("HZP-000001"))
                .andExpect(jsonPath("$.data.whatsappLink").value(org.hamcrest.Matchers.containsString("PASS-")))
                .andReturn().getResponse().getContentAsString();
        String uid = TestData.json(res).path("data").path("uid").asText();

        // Pass image: the real design with name and mobile written on it.
        byte[] jpg = mockMvc.perform(get("/api/public/passes/" + uid + ".jpg")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG)).andReturn().getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpg));
        assertThat(img.getWidth()).isEqualTo(1599);
        assertThat(img.getHeight()).isEqualTo(2000);

        // No template and the customer has not messaged yet -> waits for the chat.
        await().atMost(Duration.ofSeconds(10)).until(() ->
                registrationRepository.findByUid(uid).orElseThrow().getPassStatus() == PassStatus.WAITING_FOR_CHAT);
        Registration r = registrationRepository.findByUid(uid).orElseThrow();
        assertThat(r.getPhone()).isEqualTo("919812345678");
        assertThat(scheduledRepository.findAll()).singleElement().satisfies(s -> {
            assertThat(s.getRegistrationId()).isEqualTo(r.getId());
            assertThat(s.getPreview()).isEqualTo("Salaam Mohammed Shahid, your pass HZP-000001 is ready");
            assertThat(s.getScheduledAt()).isAfter(Instant.now().plus(Duration.ofMinutes(58)));
        });
        assertThat(contactRepository.findByPartnerIdAndPhone(partner.getId(), "919812345678").orElseThrow().getTags()).contains("hajj-2027");
        assertThat(jdbc.queryForObject("select member_count from contact_groups where id = ?", Integer.class, groupId)).isEqualTo(1);

        // Same number again -> rejected; another person -> next pass number.
        register("harmain-pass", "Someone Else", "+91 98123 45678").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("mobile"));
        register("harmain-pass", "Ayesha Khan", "9876501234").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passNo").value("HZP-000002"));

        // A different number sending the code is ignored; the registered number gets the pass as an image.
        webhook(inboundText("919000000001", "wamid.X1", "PASS-" + r.getClaimCode()));
        Thread.sleep(400);
        assertThat(registrationRepository.findByUid(uid).orElseThrow().getPassStatus()).isEqualTo(PassStatus.WAITING_FOR_CHAT);
        webhook(inboundText("919812345678", "wamid.C1", "Hi, please send my pass. PASS-" + r.getClaimCode()));
        await().atMost(Duration.ofSeconds(10)).until(() ->
                registrationRepository.findByUid(uid).orElseThrow().getPassStatus() == PassStatus.SENT);
        Message m = messageRepository.findById(registrationRepository.findByUid(uid).orElseThrow().getPassMessageId()).orElseThrow();
        assertThat(m.getType()).isEqualTo(MessageType.IMAGE);
        assertThat(m.getSource()).isEqualTo(MessageSource.REGISTRATION);
        assertThat(m.getWaId()).isEqualTo("919812345678");

        mockMvc.perform(get("/api/partner/registrations").param("formId", String.valueOf(formId)).header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        String csv = mockMvc.perform(get("/api/partner/registration-forms/" + formId + "/export").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("HZP-000001,Mohammed Shahid,+919812345678,SENT");
    }

    @Test
    void approvedImageTemplateSendsPassImmediately() throws Exception {
        Template t = template();
        send(post("/api/partner/registration-forms"), """
                {"title":"Ziyarat","slug":"ziyarat-tpl","senderId":%d,"passTemplateId":%d,"passTemplateBody":["{name}","{passNo}"]}"""
                .formatted(sender.getId(), t.getId())).andExpect(status().isCreated());
        String uid = TestData.json(register("ziyarat-tpl", "Imran Shaikh", "9811122233").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data").path("uid").asText();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                registrationRepository.findByUid(uid).orElseThrow().getPassStatus() == PassStatus.SENT);
        Message m = messageRepository.findById(registrationRepository.findByUid(uid).orElseThrow().getPassMessageId()).orElseThrow();
        assertThat(m.getType()).isEqualTo(MessageType.TEMPLATE);
        assertThat(m.getPayload()).contains("_fileUid").contains("Imran Shaikh").contains("PASS-000001");

        // A template without an IMAGE header is refused.
        Template textOnly = template();
        jdbc.update("delete from template_components where template_id = ? and type = 'HEADER'", textOnly.getId());
        send(post("/api/partner/registration-forms"), """
                {"title":"Bad","senderId":%d,"passTemplateId":%d}""".formatted(sender.getId(), textOnly.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("passTemplateId"));
    }

    @Test
    void validationLimitsClosingAndIsolation() throws Exception {
        String form = send(post("/api/partner/registration-forms"), """
                {"title":"Limited","slug":"limited-seats","senderId":%d,"maxRegistrations":1}""".formatted(sender.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long formId = TestData.json(form).path("data").path("id").asLong();

        mockMvc.perform(multipart("/api/public/register/limited-seats").param("name", "Ravi").param("mobile", "98123")
                .param("consent", "true")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("mobile"));
        mockMvc.perform(multipart("/api/public/register/limited-seats").param("name", "Ravi Kumar").param("mobile", "9812345678"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("consent"));
        mockMvc.perform(multipart("/api/public/register/limited-seats").param("name", "<script>").param("mobile", "9812345678")
                .param("consent", "true")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("name"));
        mockMvc.perform(multipart("/api/public/register/limited-seats").param("name", "Bot").param("mobile", "9812345678")
                .param("consent", "true").param("website", "http://spam")).andExpect(status().isBadRequest());

        register("limited-seats", "Ravi Kumar", "9812345678").andExpect(status().isCreated());
        register("limited-seats", "Sita Devi", "9812345679").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("All seats are booked"));
        mockMvc.perform(get("/api/public/register/limited-seats")).andExpect(jsonPath("$.data.open").value(false));

        send(put("/api/partner/registration-forms/" + formId), """
                {"title":"Limited","slug":"limited-seats","senderId":%d,"status":"CLOSED"}""".formatted(sender.getId()))
                .andExpect(status().isOk());
        register("limited-seats", "Sita Devi", "9812345679").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Registrations are closed"));

        Partner other = testData.partner(new BigDecimal("5"));
        String otherToken = testData.accessToken(other);
        mockMvc.perform(get("/api/partner/registration-forms/" + formId).header("Authorization", TestData.bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/partner/registrations").header("Authorization", TestData.bearer(otherToken)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(post("/api/partner/registration-forms").header("Authorization", TestData.bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Steal\",\"senderId\":" + sender.getId() + "}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/public/register/nope-missing")).andExpect(status().isNotFound());
    }

    @Test
    void previewRendersTheDesign() throws Exception {
        byte[] jpg = mockMvc.perform(post("/api/partner/registration-forms/preview").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test Name\",\"mobile\":\"+91 90000 00000\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(jpg)).getWidth()).isEqualTo(1599);
    }

    // ---- helpers

    private ResultActions register(String slug, String name, String mobile) throws Exception {
        testData.resetCaches();
        return mockMvc.perform(multipart("/api/public/register/" + slug).param("name", name).param("mobile", mobile).param("consent", "true"));
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String json) throws Exception {
        return mockMvc.perform(b.header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Template template() {
        Template t = new Template();
        t.setUid(Ulids.next());
        t.setWhatsappAccountId(waba.getId());
        t.setMetaTemplateId(String.valueOf(System.nanoTime()));
        t.setName("free_pass_" + System.nanoTime());
        t.setLanguage("en");
        t.setCategory(MessageCategory.UTILITY);
        t.setParameterFormat(ParameterFormat.POSITIONAL);
        t.setStatus(TemplateStatus.APPROVED);
        templateRepository.save(t);
        TemplateComponent h = new TemplateComponent();
        h.setTemplateId(t.getId());
        h.setPosition(1);
        h.setType(ComponentType.HEADER);
        h.setFormat("IMAGE");
        componentRepository.save(h);
        TemplateComponent c = new TemplateComponent();
        c.setTemplateId(t.getId());
        c.setPosition(2);
        c.setType(ComponentType.BODY);
        c.setText("Assalamu Alaikum {{1}}, your free pass {{2}} is attached.");
        componentRepository.save(c);
        return t;
    }

    private void webhook(String body) throws Exception {
        String sig = "sha256=" + HashUtil.hmacSha256Hex(APP_SECRET.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/api/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", sig)).andExpect(status().isOk());
        webhookProcessor.processPending();
    }

    private String inboundText(String from, String wamid, String text) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"%s","changes":[{"field":"messages","value":
                {"messaging_product":"whatsapp","metadata":{"display_phone_number":"919876543210","phone_number_id":"%s"},
                 "contacts":[{"profile":{"name":"Customer"},"wa_id":"%s"}],
                 "messages":[{"from":"%s","id":"%s","timestamp":"%d","type":"text","text":{"body":"%s"}}]}}]}]}"""
                .formatted(waba.getWabaId(), sender.getPhoneNumberId(), from, from, wamid, Instant.now().getEpochSecond(), text);
    }

}
