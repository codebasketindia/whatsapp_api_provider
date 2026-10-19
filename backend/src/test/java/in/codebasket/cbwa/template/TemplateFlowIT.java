package in.codebasket.cbwa.template;

import com.github.tomakehurst.wiremock.client.WireMock;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.meta.MetaTestBase;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

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

class TemplateFlowIT extends MetaTestBase {

    @Autowired
    TemplateRepository templateRepository;

    private static String request(Long senderId, String name, boolean submit) {
        return "{\"senderId\":" + senderId + ",\"name\":\"" + name + "\",\"language\":\"en\",\"category\":\"UTILITY\","
                + "\"parameterFormat\":\"POSITIONAL\",\"submit\":" + submit + ",\"components\":["
                + "{\"type\":\"BODY\",\"text\":\"Hi {{1}}, your order {{2}} is ready.\",\"example\":[\"Ravi\",\"#12\"]},"
                + "{\"type\":\"BUTTONS\",\"buttons\":[{\"type\":\"QUICK_REPLY\",\"text\":\"Thanks\"}]}]}";
    }

    @Test
    void ownWabaTemplateGoesStraightToMeta() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        WhatsappAccount w = testData.waba(WabaOwnerType.PARTNER, p.getId(), BillingMode.TECH_PROVIDER);
        PhoneNumber pn = testData.sender(w, p.getId(), SenderType.PARTNER_OWN_SENDER);
        META.stubFor(WireMock.post(urlPathEqualTo(V + "/" + w.getWabaId() + "/message_templates"))
                .willReturn(okJson("{\"id\":\"555001\",\"status\":\"PENDING\",\"category\":\"UTILITY\"}")));

        mockMvc.perform(post("/api/partner/templates").header("Authorization", TestData.bearer(testData.accessToken(p)))
                        .contentType(MediaType.APPLICATION_JSON).content(request(pn.getId(), "order_ready", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.metaTemplateId").value("555001"))
                .andExpect(jsonPath("$.data.components[0].example.body_text[0][1]").value("#12"));

        META.verify(postRequestedFor(urlPathEqualTo(V + "/" + w.getWabaId() + "/message_templates"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("order_ready")))
                .withRequestBody(matchingJsonPath("$.components[0].example.body_text[0][0]", equalTo("Ravi"))));

        // duplicate name/language rejected
        mockMvc.perform(post("/api/partner/templates").header("Authorization", TestData.bearer(testData.accessToken(p)))
                        .contentType(MediaType.APPLICATION_JSON).content(request(pn.getId(), "order_ready", false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void platformWabaTemplateNeedsAdminReview() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        WhatsappAccount w = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        PhoneNumber pn = testData.sender(w, p.getId(), SenderType.PLATFORM_SENDER);
        META.stubFor(WireMock.post(urlPathEqualTo(V + "/" + w.getWabaId() + "/message_templates"))
                .willReturn(okJson("{\"id\":\"555002\",\"status\":\"PENDING\",\"category\":\"UTILITY\"}")));

        String body = mockMvc.perform(post("/api/partner/templates").header("Authorization", TestData.bearer(testData.accessToken(p)))
                        .contentType(MediaType.APPLICATION_JSON).content(request(pn.getId(), "promo_one", true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andReturn().getResponse().getContentAsString();
        long id = TestData.json(body).at("/data/id").asLong();
        META.verify(0, postRequestedFor(urlPathEqualTo(V + "/" + w.getWabaId() + "/message_templates")));

        User admin = testData.superAdmin();
        String adminToken = testData.accessToken(admin);
        mockMvc.perform(get("/api/admin/templates/review-queue").header("Authorization", TestData.bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(id));
        mockMvc.perform(post("/api/admin/templates/" + id + "/approve").header("Authorization", TestData.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"looks good\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        META.verify(1, postRequestedFor(urlPathEqualTo(V + "/" + w.getWabaId() + "/message_templates")));
        assertThat(templateRepository.findById(id).orElseThrow().getMetaTemplateId()).isEqualTo("555002");

        // a partner cannot use the admin review endpoints
        mockMvc.perform(post("/api/admin/templates/" + id + "/approve").header("Authorization", TestData.bearer(testData.accessToken(p))))
                .andExpect(status().isForbidden());
    }

    @Test
    void syncUpsertsAndMarksMissingDeleted() throws Exception {
        WhatsappAccount w = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        String page = "{\"data\":[{\"id\":\"9001\",\"name\":\"welcome\",\"language\":\"en\",\"status\":\"APPROVED\",\"category\":\"MARKETING\","
                + "\"quality_score\":{\"score\":\"GREEN\"},\"components\":[{\"type\":\"BODY\",\"text\":\"Welcome!\"}]},"
                + "{\"id\":\"9002\",\"name\":\"otp\",\"language\":\"en\",\"status\":\"PAUSED\",\"category\":\"AUTHENTICATION\","
                + "\"components\":[{\"type\":\"BODY\",\"text\":\"{{1}} is your code\"}]}]}";
        META.stubFor(WireMock.get(urlPathEqualTo(V + "/" + w.getWabaId() + "/message_templates")).willReturn(okJson(page)));
        String adminToken = testData.accessToken(testData.superAdmin());
        mockMvc.perform(post("/api/admin/templates/sync").header("Authorization", TestData.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"wabaDbId\":" + w.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(2));
        Template welcome = templateRepository.findByMetaTemplateId("9001").orElseThrow();
        assertThat(welcome.getStatus()).isEqualTo(TemplateStatus.APPROVED);
        assertThat(welcome.getQualityScore()).isEqualTo("GREEN");
        assertThat(templateRepository.findByMetaTemplateId("9002").orElseThrow().getStatus()).isEqualTo(TemplateStatus.PAUSED);

        META.stubFor(WireMock.get(urlPathEqualTo(V + "/" + w.getWabaId() + "/message_templates"))
                .willReturn(okJson("{\"data\":[{\"id\":\"9001\",\"name\":\"welcome\",\"language\":\"en\",\"status\":\"APPROVED\","
                        + "\"category\":\"MARKETING\",\"components\":[{\"type\":\"BODY\",\"text\":\"Welcome back!\"}]}]}")));
        mockMvc.perform(post("/api/admin/templates/sync").header("Authorization", TestData.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"wabaDbId\":" + w.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updated").value(1)).andExpect(jsonPath("$.data.deleted").value(1));
        assertThat(templateRepository.findByMetaTemplateId("9002").orElseThrow().getStatus()).isEqualTo(TemplateStatus.DELETED);
    }
}
