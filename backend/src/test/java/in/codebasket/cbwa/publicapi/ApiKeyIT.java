package in.codebasket.cbwa.publicapi;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeyIT extends AbstractIntegrationTest {

    @Test
    void createShowsSecretOnceAndKeyAuthenticatesPublicApi() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        JsonNode created = create(token, "{\"name\":\"Server\",\"ipWhitelist\":[]}");
        String key = created.at("/data/apiKey").asText();
        String secret = created.at("/data/apiSecret").asText();
        assertThat(key).startsWith("cbk_live_").hasSize(33);
        assertThat(secret).startsWith("cbs_").hasSize(44);
        assertThat(created.at("/data/secretLast4").asText()).isEqualTo(secret.substring(40));

        mockMvc.perform(get("/api/partner/api-keys").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].apiKey").value(key))
                .andExpect(jsonPath("$.data[0].apiSecret").doesNotExist());

        // valid key: authenticated, there is no controller yet -> 404 (not 401)
        mockMvc.perform(get("/api/v1/public/senders").header("X-API-KEY", key).header("X-API-SECRET", secret))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/senders").header("X-API-KEY", key).header("X-API-SECRET", "cbs_wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("API_KEY_INVALID"));
    }

    @Test
    void regenerateNeedsPasswordAndOldSecretStopsWorking() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        String token = testData.accessToken(p);
        JsonNode created = create(token, "{\"name\":\"Server\"}");
        long id = created.at("/data/id").asLong();
        String key = created.at("/data/apiKey").asText();
        String oldSecret = created.at("/data/apiSecret").asText();

        mockMvc.perform(post("/api/partner/api-keys/" + id + "/regenerate").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        String body = mockMvc.perform(post("/api/partner/api-keys/" + id + "/regenerate").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newSecret = TestData.json(body).at("/data/apiSecret").asText();
        assertThat(newSecret).isNotEqualTo(oldSecret);

        mockMvc.perform(get("/api/v1/public/wallet/balance").header("X-API-KEY", key).header("X-API-SECRET", oldSecret))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/public/wallet/balance").header("X-API-KEY", key).header("X-API-SECRET", newSecret))
                .andExpect(status().isNotFound());

        // disabling the key blocks it
        mockMvc.perform(patch("/api/partner/api-keys/" + id).header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(get("/api/v1/public/wallet/balance").header("X-API-KEY", key).header("X-API-SECRET", newSecret))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("API_KEY_DISABLED"));
    }

    @Test
    void validationLimitsAndPartnerIsolation() throws Exception {
        Partner a = testData.partner(BigDecimal.ZERO);
        Partner b = testData.partner(BigDecimal.ZERO);
        String tokenA = testData.accessToken(a);
        String tokenB = testData.accessToken(b);

        mockMvc.perform(post("/api/partner/api-keys").header("Authorization", TestData.bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"ipWhitelist\":[\"999.1.1.1\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("ipWhitelist"));

        long id = 0;
        for (int i = 0; i < 5; i++) {
            id = create(tokenA, "{\"name\":\"k" + i + "\",\"ipWhitelist\":[\"203.0.113.10\",\"2001:db8::1\"]}").at("/data/id").asLong();
        }
        mockMvc.perform(post("/api/partner/api-keys").header("Authorization", TestData.bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"k6\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/partner/api-keys/" + id).header("Authorization", TestData.bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"stolen\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/partner/api-keys/" + id).header("Authorization", TestData.bearer(tokenB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/partner/api-keys/" + id).header("Authorization", TestData.bearer(tokenA)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from api_keys where partner_id = ?", Integer.class, a.getId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'API_KEY_DELETE'", Integer.class)).isEqualTo(1);
    }

    private JsonNode create(String token, String json) throws Exception {
        String body = mockMvc.perform(post("/api/partner/api-keys").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return TestData.json(body);
    }
}
