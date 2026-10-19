package in.codebasket.cbwa.pricing;

import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RatePlanIT extends AbstractIntegrationTest {

    @Autowired
    RatePlanRepository planRepository;

    private static String plan(String name, boolean isDefault) {
        return "{\"name\":\"" + name + "\",\"isDefault\":" + isDefault + ",\"sessionMessageFee\":\"0\","
                + "\"platformFeePerMessage\":\"0.05\",\"platformFeeMonthlyPerNumber\":\"499\",\"items\":["
                + "{\"countryCode\":\"IN\",\"category\":\"MARKETING\",\"price\":\"0.95\"},"
                + "{\"countryCode\":\"IN\",\"category\":\"UTILITY\",\"price\":\"0.20\"},"
                + "{\"countryCode\":\"*\",\"category\":\"MARKETING\",\"price\":\"6.50\"}]}";
    }

    @Test
    void newDefaultPlanReplacesOldDefaultAndDefaultCannotBeArchived() throws Exception {
        String token = testData.accessToken(testData.superAdmin());
        Long oldDefault = testData.defaultPlan().getId();
        Partner p = testData.partner(BigDecimal.ZERO);
        try {
            run(token, oldDefault, p);
        } finally {
            // restore the seeded default plan for other tests
            planRepository.findAll().forEach(rp -> {
                rp.setDefaultPlan(rp.getId().equals(oldDefault));
                planRepository.save(rp);
            });
        }
    }

    private void run(String token, Long oldDefault, Partner p) throws Exception {
        String body = mockMvc.perform(post("/api/admin/rate-plans").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(plan("Premium", true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        long id = TestData.json(body).at("/data/id").asLong();
        assertThat(planRepository.findById(oldDefault).orElseThrow().isDefaultPlan()).isFalse();
        assertThat(planRepository.findByDefaultPlanTrue()).hasSize(1);

        mockMvc.perform(patch("/api/admin/rate-plans/" + id + "/status").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict());

        // items are replaced as a whole; duplicate rows rejected
        mockMvc.perform(put("/api/admin/rate-plans/" + id).header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(plan("Premium", true).replace("\"UTILITY\"", "\"MARKETING\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/rate-plans/" + oldDefault + "/status").header("Authorization", TestData.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1 partner")));
        mockMvc.perform(post("/api/admin/rate-plans").header("Authorization", TestData.bearer(testData.accessToken(p)))
                        .contentType(MediaType.APPLICATION_JSON).content(plan("Hack", false)))
                .andExpect(status().isForbidden());
    }
}
