package in.codebasket.cbwa.contact;

import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContactsIT extends AbstractIntegrationTest {

    @Autowired FileStorageService storage;

    Partner partner;
    String token;

    @BeforeEach
    void setUp() {
        partner = testData.partner(BigDecimal.ZERO);
        token = testData.accessToken(partner);
    }

    @Test
    void contactCrudTagsGroupsAndOptOut() throws Exception {
        String group = post("/api/partner/contact-groups", "{\"name\":\"VIP\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long groupId = TestData.json(group).at("/data/id").asLong();

        String created = post("/api/partner/contacts", """
                {"phone":"98765 43210","name":"Ravi","tags":["customer","vip"],"attributes":{"city":"Pune"},"groupIds":[%d]}
                """.formatted(groupId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").value("919876543210"))
                .andExpect(jsonPath("$.data.countryCode").value("IN"))
                .andExpect(jsonPath("$.data.groups[0].name").value("VIP"))
                .andReturn().getResponse().getContentAsString();
        long id = TestData.json(created).at("/data/id").asLong();

        post("/api/partner/contacts", "{\"phone\":\"+919876543210\"}").andExpect(status().isConflict());
        post("/api/partner/contacts", "{\"phone\":\"123\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECIPIENT_INVALID"));
        post("/api/partner/contacts", "{\"phone\":\"9876543211\",\"tags\":[\"customer\"],\"attributes\":{\"Bad Key\":\"x\"}}")
                .andExpect(status().isBadRequest());
        post("/api/partner/contacts", "{\"phone\":\"9876543211\",\"tags\":[\"customer\"]}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/partner/contacts/tags").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].tag").value("customer"))
                .andExpect(jsonPath("$.data[0].count").value(2));
        mockMvc.perform(get("/api/partner/contacts").param("tag", "vip").header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/partner/contacts").param("groupId", String.valueOf(groupId)).header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/partner/contact-groups").header("Authorization", TestData.bearer(token)))
                .andExpect(jsonPath("$.data.content[0].memberCount").value(1));

        post("/api/partner/contacts/" + id + "/opt-out", "{\"reason\":\"asked\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.optedOut").value(true));
        mockMvc.perform(get("/api/partner/opt-outs").header("Authorization", TestData.bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        post("/api/partner/contacts/" + id + "/opt-in", "{}").andExpect(status().isBadRequest());
        post("/api/partner/contacts/" + id + "/opt-in", "{\"confirmConsent\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.optedOut").value(false));

        // another partner cannot see it
        Partner other = testData.partner(BigDecimal.ZERO);
        mockMvc.perform(get("/api/partner/contacts/" + id).header("Authorization", TestData.bearer(testData.accessToken(other))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/partner/contacts/" + id).header("Authorization", TestData.bearer(token))).andExpect(status().isOk());
        mockMvc.perform(get("/api/partner/contact-groups").header("Authorization", TestData.bearer(token)))
                .andExpect(jsonPath("$.data.content[0].memberCount").value(0));
    }

    @Test
    void importCreatesUpdatesAndReportsInvalidRows() throws Exception {
        String csv = "﻿Mobile,Full name,Labels,City\n"
                + "919876500001,A One,customer;vip,Pune\n"
                + "9876500002,B Two,customer,Delhi\n"
                + "abc,Bad,,\n"
                + "+91 98765 00001,Dup,,\n";
        String mapping = "{\"phone\":\"Mobile\",\"name\":\"Full name\",\"tags\":\"Labels\",\"attributes\":{\"City\":\"city\"}}";
        String body = importCsv(csv, mapping, "false").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.invalid").value(1))
                .andExpect(jsonPath("$.data.duplicates").value(1))
                .andReturn().getResponse().getContentAsString();
        String reportUid = TestData.json(body).at("/data/reportFileUid").asText();
        String report = new String(storage.readBytes(storage.get(reportUid)), StandardCharsets.UTF_8);
        assertThat(report).contains("INVALID").contains("DUPLICATE").contains("CREATED");

        importCsv("Mobile,Full name,Labels,City\n919876500002,B Changed,new,Mumbai\n", mapping, "false")
                .andExpect(jsonPath("$.data.duplicates").value(1)).andExpect(jsonPath("$.data.updated").value(0));
        importCsv("Mobile,Full name,Labels,City\n919876500002,B Changed,new,Mumbai\n", mapping, "true")
                .andExpect(jsonPath("$.data.updated").value(1));
        mockMvc.perform(get("/api/partner/contacts").param("search", "500002").header("Authorization", TestData.bearer(token)))
                .andExpect(jsonPath("$.data.content[0].name").value("B Changed"))
                .andExpect(jsonPath("$.data.content[0].attributes.city").value("Mumbai"))
                .andExpect(jsonPath("$.data.content[0].tags.length()").value(2));

        mockMvc.perform(multipart("/api/partner/contacts/import/preview")
                        .file(new MockMultipartFile("file", "c.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", TestData.bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.columns[0]").value("Mobile"))
                .andExpect(jsonPath("$.data.rowCount").value(4));
        importCsv(csv, "{\"phone\":\"Nope\"}", "false").andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions importCsv(String csv, String mapping, String update) throws Exception {
        return mockMvc.perform(multipart("/api/partner/contacts/import")
                .file(new MockMultipartFile("file", "contacts.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("mapping", mapping).param("updateExisting", update)
                .header("Authorization", TestData.bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions post(String url, String json) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", TestData.bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
