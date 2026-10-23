package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    void adminAndPartnerLoginToTheirOwnAreasAndWrongRoleIsBlocked() throws Exception {
        User admin = testData.superAdmin();
        Partner partner = testData.partner(BigDecimal.ZERO);

        String adminToken = login(admin.getEmail());
        String partnerToken = login(partner.getEmail());

        mockMvc.perform(get("/api/admin/roles").header("Authorization", TestData.bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/roles").header("Authorization", TestData.bearer(partnerToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/me").header("Authorization", TestData.bearer(partnerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userType").value("PARTNER"))
                .andExpect(jsonPath("$.data.partner.businessName").value(partner.getBusinessName()));
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void wrongPasswordLocksAccountAfterMaxAttempts() throws Exception {
        User admin = testData.superAdmin();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + admin.getEmail() + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        }
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + admin.getEmail() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + admin.getEmail() + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesFamily() throws Exception {
        User admin = testData.superAdmin();
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + admin.getEmail() + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rt1 = TestData.json(body).at("/data/refreshToken").asText();
        String body2 = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rt1 + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rt2 = TestData.json(body2).at("/data/refreshToken").asText();
        assertThat(rt2).isNotEqualTo(rt1);
        // reuse of rt1 -> invalid and the whole family (rt2) is revoked
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rt1 + "\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rt2 + "\"}")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void loginWithMobileNumberInAnyFormat() throws Exception {
        User admin = testData.superAdmin();
        Partner partner = testData.partner(BigDecimal.ZERO);
        String partnerLocal = partner.getMobile().substring(2); // 10-digit national number
        for (String typed : new String[]{partnerLocal, "+91 " + partnerLocal, "0" + partnerLocal, partner.getMobile()}) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mobile\":\"" + typed + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.userType").value("PARTNER"));
        }
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + admin.getMobile() + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.userType").value("ADMIN"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + partnerLocal + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
        // forgot password by mobile always answers with a challenge (no account enumeration)
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + partnerLocal + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.challengeId").isNotEmpty());
    }

    @Test
    void mobileNumberMustBeUniqueAcrossUsers() throws Exception {
        User admin = testData.superAdmin();
        Partner partner = testData.partner(BigDecimal.ZERO);
        String token = login(admin.getEmail());
        mockMvc.perform(post("/api/admin/staff").header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Staff\",\"email\":\"staff1@test.local\",\"mobile\":\"+91 " + partner.getMobile().substring(2)
                                + "\",\"roleId\":3,\"password\":\"Staff@12345\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("mobile"));
        mockMvc.perform(post("/api/admin/staff").header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Staff\",\"email\":\"staff1@test.local\",\"mobile\":\"98111 22233\",\"roleId\":3,\"password\":\"Staff@12345\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"9811122233\",\"password\":\"Staff@12345\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return TestData.json(body).at("/data/accessToken").asText();
    }
}
