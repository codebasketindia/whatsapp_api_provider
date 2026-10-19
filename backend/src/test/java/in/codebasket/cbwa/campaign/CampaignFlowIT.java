package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.contact.OptOut;
import in.codebasket.cbwa.contact.OptOutRepository;
import in.codebasket.cbwa.contact.OptOutSource;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageRepository;
import in.codebasket.cbwa.messaging.OutboundMessageService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.pricing.PriceResolver;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.SenderAccessService;
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
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletRepository;
import in.codebasket.cbwa.wallet.WalletService;
import in.codebasket.cbwa.wallet.WalletTransaction;
import in.codebasket.cbwa.wallet.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CampaignFlowIT extends AbstractIntegrationTest {

    /** Replaces (or provides) the messaging engine with the test-only stub. */
    @MockitoBean
    OutboundMessageService outbound;

    @Autowired MessageRepository messageRepository;
    @Autowired TemplateRepository templateRepository;
    @Autowired TemplateComponentRepository componentRepository;
    @Autowired PriceResolver priceResolver;
    @Autowired SenderAccessService senderAccess;
    @Autowired WalletService walletService;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository txnRepository;
    @Autowired OptOutRepository optOutRepository;
    @Autowired CampaignRepository campaignRepository;
    @Autowired CampaignDispatcher dispatcher;
    @Autowired CampaignProgressJob progressJob;

    Partner partner;
    PhoneNumber sender;
    Template template;
    String token;

    @BeforeEach
    void setUp() {
        TestOutboundStub stub = new TestOutboundStub(messageRepository, templateRepository, priceResolver, senderAccess, walletService);
        when(outbound.enqueue(any())).thenAnswer(inv -> stub.enqueue(inv.getArgument(0)));
        when(outbound.unitPrice(anyLong(), anyLong(), any(), anyString()))
                .thenAnswer(inv -> stub.unitPrice(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));

        testData.price("IN", MessageCategory.MARKETING, "0.80");
        partner = testData.partner(new BigDecimal("1000"));
        WhatsappAccount waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        sender = testData.sender(waba, partner.getId(), SenderType.PLATFORM_SENDER);
        template = template(waba.getId(), partner.getId(), TemplateStatus.APPROVED);
        token = testData.accessToken(partner);
    }

    @Test
    void hundredRowCampaignRunsWithRefundsAndAccurateReport() throws Exception {
        // 92 unique valid numbers (2 opted out) + 5 invalid + 3 duplicates = 100 rows
        StringBuilder csv = new StringBuilder("mobile,name,code\n");
        for (int i = 0; i < 92; i++) {
            csv.append(phone(i)).append(",Customer ").append(i).append(",C").append(i).append('\n');
        }
        for (int i = 0; i < 5; i++) {
            csv.append("12").append(i).append(",Bad,X\n");
        }
        for (int i = 0; i < 3; i++) {
            csv.append("+").append(phone(i)).append(",Again,Y\n");
        }
        optOut(phone(10));
        optOut(phone(11));

        Long id = createCampaign(upload(csv.toString()));

        mockMvc.perform(post("/api/partner/campaigns/" + id + "/prepare").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.total").value(100))
                .andExpect(jsonPath("$.data.counts.valid").value(90))
                .andExpect(jsonPath("$.data.counts.invalid").value(5))
                .andExpect(jsonPath("$.data.counts.duplicates").value(3))
                .andExpect(jsonPath("$.data.counts.optedOut").value(2))
                .andExpect(jsonPath("$.data.estimate.total").value("72.0000"))
                .andExpect(jsonPath("$.data.estimate.sufficient").value(true))
                .andExpect(jsonPath("$.data.preview[0].renderedText").value("Hi Customer 0, your code is C0"));

        launch(id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));

        assertThat(dispatcher.dispatch(id)).isEqualTo(90);
        Wallet w = wallet();
        assertThat(w.getBalance()).isEqualByComparingTo("928");
        assertThat(w.getHeldAmount()).isEqualByComparingTo("72");

        // Simulate delivery results: 60 delivered (charged), 20 failed (refunded), 10 still SENT (held)
        List<Message> msgs = messageRepository.findAll().stream().filter(m -> id.equals(m.getCampaignId())).toList();
        assertThat(msgs).hasSize(90);
        for (int i = 0; i < 90; i++) {
            Message m = msgs.get(i);
            if (i < 60) {
                walletService.finalizeHold(partner.getId(), m.getPrice(), WalletRefType.MESSAGE, m.getUid(), "delivered");
                setStatus(m, "DELIVERED", "CHARGED");
            } else if (i < 80) {
                walletService.refundHold(partner.getId(), m.getPrice(), WalletRefType.MESSAGE, m.getUid(), "failed");
                setStatus(m, "FAILED", "REFUNDED");
            } else {
                setStatus(m, "SENT", "HELD");
            }
        }
        progressJob.refresh(id);
        Campaign c = campaignRepository.findById(id).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.RUNNING);
        assertThat(c.getSentCount()).isEqualTo(70);
        assertThat(c.getDeliveredCount()).isEqualTo(60);
        assertThat(c.getFailedCount()).isEqualTo(20);
        assertThat(c.getChargedAmount()).isEqualByComparingTo("56");
        assertThat(c.getRefundedAmount()).isEqualByComparingTo("16");
        assertThat(c.getSkippedCount()).isEqualTo(5); // 3 duplicates + 2 opted out
        assertThat(c.getInvalidCount()).isEqualTo(5);

        for (int i = 80; i < 90; i++) {
            Message m = msgs.get(i);
            walletService.finalizeHold(partner.getId(), m.getPrice(), WalletRefType.MESSAGE, m.getUid(), "delivered");
            setStatus(m, "DELIVERED", "CHARGED");
        }
        progressJob.refresh(id);
        c = campaignRepository.findById(id).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(c.getDeliveredCount()).isEqualTo(70);
        assertThat(c.getChargedAmount()).isEqualByComparingTo("56");

        w = wallet();
        assertThat(w.getBalance()).isEqualByComparingTo("944");
        assertThat(w.getHeldAmount()).isEqualByComparingTo("0");
        assertLedgerChain(w);

        mockMvc.perform(get("/api/partner/campaigns/" + id + "/recipients").param("status", "SKIPPED")
                        .header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(10));
        String report = mockMvc.perform(get("/api/partner/campaigns/" + id + "/report").param("format", "csv")
                        .header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(report.lines().count()).isEqualTo(101);
        assertThat(report).contains("OPTED_OUT").contains("INVALID_NUMBER").contains("DUPLICATE").contains("FAILED");
        mockMvc.perform(get("/api/partner/campaigns/" + id + "/report").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void insufficientBalanceBlocksLaunch() throws Exception {
        jdbc.update("update wallets set balance = 10 where partner_id = ?", partner.getId());
        Long id = createCampaign(upload(simpleCsv(50)));
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/prepare").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimate.sufficient").value(false));
        launch(id).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("WALLET_INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.data.required").value("40.0000"));
        assertThat(campaignRepository.findById(id).orElseThrow().getStatus()).isEqualTo(CampaignStatus.DRAFT);
    }

    @Test
    void launchRequiresApprovedTemplateAndPreparation() throws Exception {
        Long id = createCampaign(upload(simpleCsv(3)));
        launch(id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPAIGN_INVALID_STATE"));
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/prepare").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk());
        jdbc.update("update templates set status = 'PAUSED' where id = ?", template.getId());
        launch(id).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TEMPLATE_NOT_APPROVED"));
    }

    @Test
    void pauseResumeCancelTransitionsAndBalanceRunningOutPausesCampaign() throws Exception {
        Long id = createCampaign(upload(simpleCsv(20)));
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/prepare").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk());
        launch(id).andExpect(status().isOk());

        mockMvc.perform(post("/api/partner/campaigns/" + id + "/resume").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPAIGN_INVALID_STATE"));
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/pause").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAUSED"));
        assertThat(dispatcher.dispatch(id)).isZero();
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/pause").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/resume").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));

        // Balance only covers 5 messages -> 5 queued, then the campaign pauses itself
        jdbc.update("update wallets set balance = 4 where partner_id = ?", partner.getId());
        assertThat(dispatcher.dispatch(id)).isEqualTo(5);
        Campaign c = campaignRepository.findById(id).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.PAUSED);
        assertThat(c.getPauseReason()).isEqualTo("INSUFFICIENT_BALANCE");
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/resume").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isPaymentRequired());

        mockMvc.perform(post("/api/partner/campaigns/" + id + "/cancel").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.counts.queued").value(5));
        Integer pending = jdbc.queryForObject("select count(*) from campaign_recipients where campaign_id = ? and status = 'PENDING'",
                Integer.class, id);
        assertThat(pending).isZero();
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/cancel").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanPauseAndPartnersAreIsolated() throws Exception {
        Long id = createCampaign(upload(simpleCsv(2)));
        Partner other = testData.partner(BigDecimal.TEN);
        String otherToken = testData.accessToken(other);
        mockMvc.perform(get("/api/partner/campaigns/" + id).header("Authorization", TestData.bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/prepare").header("Authorization", TestData.bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/partner/campaigns/" + id + "/prepare").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk());
        launch(id).andExpect(status().isOk());
        String admin = testData.accessToken(testData.superAdmin());
        mockMvc.perform(get("/api/admin/campaigns").param("partnerId", partner.getId().toString())
                        .header("Authorization", TestData.bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].partner.businessName").value(partner.getBusinessName()));
        mockMvc.perform(post("/api/admin/campaigns/" + id + "/pause").header("Authorization", TestData.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam complaints\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pauseReason").value("ADMIN"));
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/resume").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/admin/campaigns").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void groupAudienceUsesContactAttributesAndStaticValues() throws Exception {
        String g = mockMvc.perform(post("/api/partner/contact-groups").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Buyers\"}"))
                .andReturn().getResponse().getContentAsString();
        long groupId = TestData.json(g).at("/data/id").asLong();
        mockMvc.perform(post("/api/partner/contacts").header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\",\"name\":\"Asha\",\"attributes\":{\"coupon\":\"SAVE10\"},\"groupIds\":[%d]}".formatted(phone(1), groupId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/partner/contacts").header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\",\"name\":\"NoCoupon\",\"groupIds\":[%d]}".formatted(phone(2), groupId)))
                .andExpect(status().isCreated());
        String req = """
                {"name":"Group run","senderId":%d,"templateId":%d,"audience":{"type":"GROUPS","groupIds":[%d]},
                 "mapping":{"variables":{"1":"{{name}}","2":"{{coupon}}"}}}
                """.formatted(sender.getId(), template.getId(), groupId);
        String body = mockMvc.perform(post("/api/partner/campaigns").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(req)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = TestData.json(body).at("/data/id").asLong();
        mockMvc.perform(post("/api/partner/campaigns/" + id + "/prepare").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.valid").value(1))
                .andExpect(jsonPath("$.data.counts.missingVariables").value(1))
                .andExpect(jsonPath("$.data.preview[0].renderedText").value("Hi Asha, your code is SAVE10"));
    }

    // ------------------------------------------------------------------ helpers
    private String upload(String csv) throws Exception {
        String body = mockMvc.perform(multipart("/api/partner/campaigns/upload")
                        .file(new MockMultipartFile("file", "list.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return TestData.json(body).at("/data/fileUid").asText();
    }

    private Long createCampaign(String fileUid) throws Exception {
        String req = """
                {"name":"Diwali offer","senderId":%d,"templateId":%d,
                 "audience":{"type":"FILE","fileUid":"%s"},
                 "mapping":{"phone":"mobile","name":"name","variables":{"1":"name","2":"code"}},
                 "defaultCountry":"IN","saveContacts":true}
                """.formatted(sender.getId(), template.getId(), fileUid);
        String body = mockMvc.perform(post("/api/partner/campaigns").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return TestData.json(body).at("/data/id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions launch(Long id) throws Exception {
        return mockMvc.perform(post("/api/partner/campaigns/" + id + "/launch").header("Authorization", TestData.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmCost\":true}"));
    }

    private static String phone(int i) {
        return "9198123" + String.format("%05d", i);
    }

    private static String simpleCsv(int n) {
        StringBuilder sb = new StringBuilder("mobile,name,code\n");
        for (int i = 0; i < n; i++) {
            sb.append(phone(i)).append(",N").append(i).append(",K").append(i).append('\n');
        }
        return sb.toString();
    }

    private void optOut(String phone) {
        OptOut o = new OptOut();
        o.setPartnerId(partner.getId());
        o.setPhone(phone);
        o.setSource(OptOutSource.MANUAL);
        o.setCreatedAt(Instant.now());
        optOutRepository.save(o);
    }

    private void setStatus(Message m, String status, String charge) {
        jdbc.update("update messages set status = ?, charge_status = ?, wamid = ? where id = ?", status, charge, "wamid." + m.getUid(), m.getId());
    }

    private Wallet wallet() {
        return walletRepository.findByPartnerId(partner.getId()).orElseThrow();
    }

    private void assertLedgerChain(Wallet w) {
        List<WalletTransaction> txns = txnRepository.findByWalletIdOrderByIdAsc(w.getId());
        for (int i = 1; i < txns.size(); i++) {
            assertThat(txns.get(i).getBalanceBefore()).isEqualByComparingTo(txns.get(i - 1).getBalanceAfter());
            assertThat(txns.get(i).getHeldBefore()).isEqualByComparingTo(txns.get(i - 1).getHeldAfter());
        }
        WalletTransaction last = txns.get(txns.size() - 1);
        assertThat(last.getBalanceAfter()).isEqualByComparingTo(w.getBalance());
        assertThat(last.getHeldAfter()).isEqualByComparingTo(w.getHeldAmount());
    }

    private Template template(Long wabaId, Long partnerId, TemplateStatus status) {
        Template t = new Template();
        t.setUid(Ulids.next());
        t.setPartnerId(partnerId);
        t.setWhatsappAccountId(wabaId);
        t.setName("diwali_offer");
        t.setLanguage("en");
        t.setCategory(MessageCategory.MARKETING);
        t.setParameterFormat(ParameterFormat.POSITIONAL);
        t.setStatus(status);
        templateRepository.save(t);
        TemplateComponent body = new TemplateComponent();
        body.setTemplateId(t.getId());
        body.setPosition(1);
        body.setType(ComponentType.BODY);
        body.setText("Hi {{1}}, your code is {{2}}");
        componentRepository.save(body);
        return t;
    }
}
