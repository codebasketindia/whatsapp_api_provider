package in.codebasket.cbwa.partner;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartnerManagementIT extends AbstractIntegrationTest {

    private static final String CREATE = """
            {"businessName":"Acme Traders","contactName":"Ravi Kumar","email":"ravi@acme.test","mobile":"+919876500001",
             "gstin":"27AAPFU0939F1ZV","address":{"line1":"1 MG Road","city":"Pune","state":"Maharashtra","pincode":"411001"},
             "allowOwnNumber":true,"password":"Welcome@123","sendWelcomeEmail":false}
            """;

    @Test
    void adminCreatesPartnerWhoCanLogIn() throws Exception {
        User admin = testData.superAdmin();
        String token = testData.accessToken(admin);
        String body = mockMvc.perform(post("/api/admin/partners").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.partnerCode").value("CBP000001"))
                .andExpect(jsonPath("$.data.address.stateCode").value("27"))
                .andExpect(jsonPath("$.data.wallet.balance").value("0.0000"))
                .andReturn().getResponse().getContentAsString();
        long id = TestData.json(body).at("/data/id").asLong();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ravi@acme.test\",\"password\":\"Welcome@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.userType").value("PARTNER"))
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true));

        // duplicate email
        mockMvc.perform(post("/api/admin/partners").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("email"));

        mockMvc.perform(get("/api/admin/partners/" + id).header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stats.senders").value(0))
                .andExpect(jsonPath("$.data.settings.effectiveLowBalanceThreshold").value("500.0000"));
        mockMvc.perform(get("/api/admin/partners?search=acme").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void suspendBlocksLoginAndLoginAsWorksForActivePartner() throws Exception {
        User admin = testData.superAdmin();
        String token = testData.accessToken(admin);
        Partner p = testData.partner(new BigDecimal("50"));

        String imp = mockMvc.perform(post("/api/admin/partners/" + p.getId() + "/login-as").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Support ticket 42\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.impersonatedBy.uid").value(admin.getUid()))
                .andReturn().getResponse().getContentAsString();
        String impToken = TestData.json(imp).at("/data/accessToken").asText();
        mockMvc.perform(get("/api/partner/wallet").header("Authorization", TestData.bearer(impToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value("50.0000"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'LOGIN_AS_PARTNER'", Long.class)).isEqualTo(1);

        mockMvc.perform(patch("/api/admin/partners/" + p.getId() + "/status").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/partners/" + p.getId() + "/status").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"reason\":\"Unpaid dues\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + p.getEmail() + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
        mockMvc.perform(post("/api/admin/partners/" + p.getId() + "/login-as").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Support ticket 42\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PARTNER_SUSPENDED"));
    }

    @Test
    void platformSenderAssignedToOnlyOnePartnerAtATime() throws Exception {
        String token = testData.accessToken(testData.superAdmin());
        Partner a = testData.partner(BigDecimal.ZERO);
        Partner b = testData.partner(BigDecimal.ZERO);
        WhatsappAccount waba = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        PhoneNumber pn = testData.sender(waba, null, SenderType.PLATFORM_SENDER);

        mockMvc.perform(post("/api/admin/partners/" + a.getId() + "/senders").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumberDbId\":" + pn.getId() + ",\"isDefault\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.senderType").value("PLATFORM_SENDER"));
        mockMvc.perform(post("/api/admin/partners/" + b.getId() + "/senders").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumberDbId\":" + pn.getId() + ",\"isDefault\":true}"))
                .andExpect(status().isConflict());
        String list = mockMvc.perform(get("/api/admin/partners/" + a.getId() + "/senders").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode arr = TestData.json(list).at("/data");
        assertThat(arr.size()).isEqualTo(1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/partners/" + a.getId() + "/senders/" + pn.getId()).header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/partners/" + b.getId() + "/senders").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumberDbId\":" + pn.getId() + ",\"isDefault\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void staffPermissionsAreEnforced() throws Exception {
        String support = testData.accessToken(testData.admin("SUPPORT"));
        mockMvc.perform(get("/api/admin/partners").header("Authorization", TestData.bearer(support))).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/partners").header("Authorization", TestData.bearer(support))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isForbidden());
        Partner p = testData.partner(BigDecimal.ZERO);
        mockMvc.perform(post("/api/admin/partners/" + p.getId() + "/login-as").header("Authorization", TestData.bearer(support))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x-check\"}"))
                .andExpect(status().isForbidden());
    }
}
