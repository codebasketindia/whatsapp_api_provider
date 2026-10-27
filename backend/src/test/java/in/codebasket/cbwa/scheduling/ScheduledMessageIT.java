package in.codebasket.cbwa.scheduling;

import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.messaging.ConversationWindow;
import in.codebasket.cbwa.messaging.ConversationWindowRepository;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageRepository;
import in.codebasket.cbwa.messaging.MessageSource;
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
import in.codebasket.cbwa.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduledMessageIT extends AbstractIntegrationTest {

    static final String TO = "919812345678";

    @Autowired ScheduledMessageJob job;
    @Autowired ScheduledMessageRepository repository;
    @Autowired MessageRepository messageRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ConversationWindowRepository windowRepository;
    @Autowired TemplateRepository templateRepository;
    @Autowired TemplateComponentRepository componentRepository;

    Partner partner;
    WhatsappAccount waba;
    PhoneNumber sender;
    String token;
    Template utility;

    @BeforeEach
    void setUp() {
        partner = testData.partner(new BigDecimal("10"));
        waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        sender = testData.sender(waba, partner.getId(), SenderType.PLATFORM_SENDER);
        token = testData.accessToken(partner);
        testData.price("IN", MessageCategory.UTILITY, "0.20");
        utility = template();
    }

    @Test
    void templateIsScheduledEditedRescheduledAndSentByTheJobWithMoneyHeldOnlyAtSendTime() throws Exception {
        String uid = uid(send(post("/api/partner/scheduled-messages"), body(in(Duration.ofHours(2)), templateMessage("Ravi")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.type").value("template"))
                .andExpect(jsonPath("$.data.windowOpenAtSchedule").value(true))
                .andExpect(jsonPath("$.data.sender.id").value(sender.getId())));
        assertBalance("10");

        send(put("/api/partner/scheduled-messages/" + uid), body(in(Duration.ofHours(3)), templateMessage("Sita")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.request.template.variables.body[0]").value("Sita"));
        send(post("/api/partner/scheduled-messages/" + uid + "/reschedule"), "{\"scheduledAt\":\"" + in(Duration.ofDays(1)) + "\"}")
                .andExpect(status().isOk());
        assertThat(job.runOnce()).isZero(); // not due yet

        jdbc.update("update scheduled_messages set scheduled_at = ? where uid = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(5)), uid);
        assertThat(job.runOnce()).isEqualTo(1);
        ScheduledMessage s = repository.findByUidAndPartnerId(uid, partner.getId()).orElseThrow();
        assertThat(s.getStatus()).isEqualTo(ScheduledStatus.SENT);
        Message m = messageRepository.findById(s.getMessageId()).orElseThrow();
        assertThat(m.getSource()).isEqualTo(MessageSource.SCHEDULED);
        assertThat(m.getPreview()).contains("Sita");
        assertBalance("9.8");
        assertThat(job.runOnce()).isZero(); // never sent twice

        send(post("/api/partner/scheduled-messages/" + uid + "/cancel"), "")
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/partner/scheduled-messages").param("status", "SENT").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].messageUid").value(m.getUid()));
    }

    @Test
    void freeFormFailsWhenWindowClosedAndSendNowWorksWhenOpen() throws Exception {
        String text = "{\"senderId\":%d,\"to\":\"%s\",\"type\":\"text\",\"text\":{\"body\":\"Your order is ready\"}}".formatted(sender.getId(), TO);
        String closed = uid(send(post("/api/partner/scheduled-messages"), body(in(Duration.ofHours(1)), text))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.windowOpenAtSchedule").value(false)));
        send(post("/api/partner/scheduled-messages/" + closed + "/send-now"), "")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.error").value(org.hamcrest.Matchers.containsString("24-hour window")));

        ConversationWindow w = new ConversationWindow();
        w.setPartnerId(partner.getId());
        w.setPhoneNumberId(sender.getId());
        w.setWaId(TO);
        w.setUnreadCount(0);
        w.setLastInboundAt(Instant.now());
        w.setLastMessageAt(Instant.now());
        w.setExpiresAt(Instant.now().plus(Duration.ofHours(23)));
        windowRepository.save(w);
        String open = uid(send(post("/api/partner/scheduled-messages"), body(in(Duration.ofHours(1)), text))
                .andExpect(jsonPath("$.data.windowOpenAtSchedule").value(true)));
        send(post("/api/partner/scheduled-messages/" + open + "/send-now"), "")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.messageUid").isNotEmpty());
    }

    @Test
    void validationCancelAndIsolation() throws Exception {
        send(post("/api/partner/scheduled-messages"), body(Instant.now().plusSeconds(10).toString(), templateMessage("A")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("scheduledAt"));
        send(post("/api/partner/scheduled-messages"), body(in(Duration.ofDays(91)), templateMessage("A")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("scheduledAt"));
        send(post("/api/partner/scheduled-messages"), body(in(Duration.ofHours(1)),
                "{\"senderId\":%d,\"to\":\"%s\",\"type\":\"template\",\"template\":{\"templateId\":%d,\"variables\":{\"body\":[]}}}"
                        .formatted(sender.getId(), TO, utility.getId())))
                .andExpect(status().is4xxClientError()); // variables missing → caught while scheduling

        String uid = uid(send(post("/api/partner/scheduled-messages"), body(in(Duration.ofHours(1)), templateMessage("A")))
                .andExpect(status().isCreated()));
        Partner other = testData.partner(new BigDecimal("5"));
        String otherToken = testData.accessToken(other);
        mockMvc.perform(get("/api/partner/scheduled-messages/" + uid).header("Authorization", TestData.bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/partner/scheduled-messages/" + uid + "/cancel").header("Authorization", TestData.bearer(otherToken)))
                .andExpect(status().isNotFound());

        send(post("/api/partner/scheduled-messages/" + uid + "/cancel"), "")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        jdbc.update("update scheduled_messages set scheduled_at = ? where uid = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(5)), uid);
        assertThat(job.runOnce()).isZero();
        send(post("/api/partner/scheduled-messages/" + uid + "/reschedule"), "{\"scheduledAt\":\"" + in(Duration.ofHours(2)) + "\"}")
                .andExpect(status().isConflict());
    }

    // ---- helpers

    private static String in(Duration d) {
        return Instant.now().plus(d).toString();
    }

    private static String body(String at, String message) {
        return "{\"scheduledAt\":\"" + at + "\",\"message\":" + message + "}";
    }

    private String templateMessage(String name) {
        return "{\"senderId\":%d,\"to\":\"%s\",\"type\":\"template\",\"template\":{\"templateId\":%d,\"variables\":{\"body\":[\"%s\",\"#1\"]}}}"
                .formatted(sender.getId(), TO, utility.getId(), name);
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String json) throws Exception {
        return mockMvc.perform(b.header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String uid(ResultActions r) throws Exception {
        return TestData.json(r.andReturn().getResponse().getContentAsString()).path("data").path("uid").asText();
    }

    private void assertBalance(String expected) {
        BigDecimal b = walletRepository.findByPartnerId(partner.getId()).orElseThrow().getBalance();
        assertThat(b).isEqualByComparingTo(expected);
    }

    private Template template() {
        Template t = new Template();
        t.setUid(Ulids.next());
        t.setWhatsappAccountId(waba.getId());
        t.setMetaTemplateId(String.valueOf(System.nanoTime()));
        t.setName("order_update");
        t.setLanguage("en");
        t.setCategory(MessageCategory.UTILITY);
        t.setParameterFormat(ParameterFormat.POSITIONAL);
        t.setStatus(TemplateStatus.APPROVED);
        templateRepository.save(t);
        TemplateComponent c = new TemplateComponent();
        c.setTemplateId(t.getId());
        c.setPosition(1);
        c.setType(ComponentType.BODY);
        c.setText("Hello {{1}}, your order {{2}} is shipped");
        componentRepository.save(c);
        return t;
    }
}
