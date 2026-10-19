package in.codebasket.cbwa.sender;

import com.github.tomakehurst.wiremock.client.WireMock;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.meta.MetaTestBase;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.TestData;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SenderAdminIT extends MetaTestBase {

    @Autowired
    PhoneNumberRepository phoneNumberRepository;

    @Test
    void adminAssignsPlatformNumberOncePerPartner() throws Exception {
        String admin = testData.accessToken(testData.superAdmin());
        WhatsappAccount w = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        PhoneNumber pn = testData.sender(w, null, SenderType.PLATFORM_SENDER);
        Partner a = testData.partner(BigDecimal.ZERO);
        Partner b = testData.partner(BigDecimal.ZERO);

        mockMvc.perform(post("/api/admin/senders/" + pn.getId() + "/assign").header("Authorization", TestData.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"partnerId\":" + a.getId() + ",\"isDefault\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.partner.id").value(a.getId()))
                .andExpect(jsonPath("$.data.isDefault").value(true));
        mockMvc.perform(post("/api/admin/senders/" + pn.getId() + "/assign").header("Authorization", TestData.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"partnerId\":" + b.getId() + ",\"isDefault\":false}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/partner/senders").header("Authorization", TestData.bearer(testData.accessToken(a))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(post("/api/admin/senders/" + pn.getId() + "/unassign").header("Authorization", TestData.bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/senders/" + pn.getId() + "/assign").header("Authorization", TestData.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"partnerId\":" + b.getId() + ",\"isDefault\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/senders?partnerId=" + b.getId()).header("Authorization", TestData.bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void syncWithRedQualityPausesSender() throws Exception {
        Partner p = testData.partner(BigDecimal.ZERO);
        WhatsappAccount w = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        PhoneNumber pn = testData.sender(w, p.getId(), SenderType.PLATFORM_SENDER);
        META.stubFor(WireMock.get(urlPathEqualTo(V + "/" + pn.getPhoneNumberId()))
                .willReturn(okJson("{\"id\":\"" + pn.getPhoneNumberId() + "\",\"quality_rating\":\"RED\",\"status\":\"CONNECTED\","
                        + "\"messaging_limit_tier\":\"TIER_250\"}")));
        mockMvc.perform(post("/api/admin/senders/" + pn.getId() + "/sync").header("Authorization",
                        TestData.bearer(testData.accessToken(testData.superAdmin()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.qualityRating").value("RED"))
                .andExpect(jsonPath("$.data.isPaused").value(true));
        PhoneNumber fresh = phoneNumberRepository.findById(pn.getId()).orElseThrow();
        assertThat(fresh.getPauseReason()).contains("RED");
        Integer notes = jdbc.queryForObject("select count(*) from notifications where partner_id = ? and type = 'SENDER_HEALTH'",
                Integer.class, p.getId());
        assertThat(notes).isEqualTo(1);
    }
}
