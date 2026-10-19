package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.util.Ulids;
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
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 10 DoD: a message is queued via the public API with an API key (PU1-PU9). */
class PublicApiIT extends AbstractIntegrationTest {

    @Autowired
    TemplateRepository templateRepository;
    @Autowired
    TemplateComponentRepository componentRepository;
    @Autowired
    WalletRepository walletRepository;

    Partner partner;
    PhoneNumber sender;
    Template template;
    String apiKey;
    String apiSecret;

    @BeforeEach
    void setUp() throws Exception {
        partner = testData.partner(new BigDecimal("100"));
        WhatsappAccount waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        sender = testData.sender(waba, partner.getId(), SenderType.PLATFORM_SENDER);
        testData.price("IN", MessageCategory.UTILITY, "0.2000");
        template = new Template();
        template.setUid(Ulids.next());
        template.setWhatsappAccountId(waba.getId());
        template.setMetaTemplateId(String.valueOf(System.nanoTime()));
        template.setName("order_update");
        template.setLanguage("en");
        template.setCategory(MessageCategory.UTILITY);
        template.setParameterFormat(ParameterFormat.POSITIONAL);
        template.setStatus(TemplateStatus.APPROVED);
        templateRepository.save(template);
        TemplateComponent c = new TemplateComponent();
        c.setTemplateId(template.getId());
        c.setPosition(1);
        c.setType(ComponentType.BODY);
        c.setText("Hi {{1}}, your order {{2}} is shipped");
        componentRepository.save(c);

        String token = testData.accessToken(partner);
        String body = mockMvc.perform(post("/api/partner/api-keys").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"server\",\"ipWhitelist\":[]}"))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        JsonNode data = TestData.json(body).path("data");
        apiKey = data.path("apiKey").asText();
        apiSecret = data.path("apiSecret").asText();
        assertThat(apiSecret).startsWith("cbs_");
    }

    @Test
    void sendTemplateByFromNumberThenReadStatusAndBalance() throws Exception {
        String res = mockMvc.perform(auth(post("/api/v1/public/messages/send")).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "abc-1")
                        .content("""
                                {"from":"%s","to":"+91 98123 45678","type":"template","clientReference":"INV-1",
                                 "template":{"name":"order_update","language":"en","variables":{"body":["Ravi","#123"]}}}"""
                                .formatted(sender.getWaNumber())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.price").value("0.2000"))
                .andReturn().getResponse().getContentAsString();
        String id = TestData.json(res).at("/data/id").asText();

        // same Idempotency-Key -> same message, no second charge
        mockMvc.perform(auth(post("/api/v1/public/messages/send")).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "abc-1")
                        .content("""
                                {"from":"%s","to":"919812345678","type":"template",
                                 "template":{"name":"order_update","language":"en","variables":{"body":["Ravi","#123"]}}}"""
                                .formatted(sender.getWaNumber())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));

        mockMvc.perform(auth(get("/api/v1/public/messages/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientReference").value("INV-1"));
        mockMvc.perform(auth(get("/api/v1/public/wallet/balance"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value("99.8000"))
                .andExpect(jsonPath("$.data.held").value("0.2000"));
        mockMvc.perform(auth(get("/api/v1/public/senders"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].phoneNumber").value(sender.getWaNumber()));
        mockMvc.perform(auth(get("/api/v1/public/templates"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("order_update"));
    }

    @Test
    void bulkReportsAcceptedAndRejectedPerRecipient() throws Exception {
        mockMvc.perform(auth(post("/api/v1/public/messages/bulk")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"template":{"name":"order_update","language":"en"},
                         "recipients":[{"to":"919812345678","bodyVariables":["A","1"]},
                                       {"to":"12","bodyVariables":["B","2"]},
                                       {"to":"919812345679","bodyVariables":["C"]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted.length()").value(1))
                .andExpect(jsonPath("$.data.rejected.length()").value(2))
                .andExpect(jsonPath("$.data.totalCost").value("0.2000"));
    }

    @Test
    void contactsAndAuthErrors() throws Exception {
        mockMvc.perform(auth(post("/api/v1/public/contacts")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"9812345678\",\"name\":\"Ravi\"}")).andExpect(status().isCreated());
        mockMvc.perform(auth(get("/api/v1/public/contacts"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].phone").value("919812345678"));
        mockMvc.perform(get("/api/v1/public/wallet/balance").header("X-API-KEY", apiKey).header("X-API-SECRET", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("API_KEY_INVALID"));
        // dashboard JWT is not accepted on the public API
        mockMvc.perform(get("/api/v1/public/wallet/balance").header("Authorization", TestData.bearer(testData.accessToken(partner))))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("X-API-KEY", apiKey).header("X-API-SECRET", apiSecret);
    }
}
