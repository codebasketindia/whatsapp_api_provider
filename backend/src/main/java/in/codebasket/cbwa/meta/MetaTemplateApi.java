package in.codebasket.cbwa.meta;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Graph API calls for message templates, template analytics and the template library (F6). */
@Component
@RequiredArgsConstructor
public class MetaTemplateApi {

    public static final String TEMPLATE_FIELDS =
            "id,name,language,status,category,components,quality_score,rejected_reason,parameter_format,previous_category";

    private final MetaGraphClient client;

    /** POST /{WABA_ID}/message_templates -> {id, status, category}. */
    public JsonNode create(String wabaId, Map<String, Object> payload, String token) {
        return client.post("/" + wabaId + "/message_templates", payload, token);
    }

    /** POST /{TEMPLATE_ID} - edit components (and category when allowed). */
    public JsonNode edit(String templateId, Map<String, Object> payload, String token) {
        return client.post("/" + templateId, payload, token);
    }

    public JsonNode get(String templateId, String token) {
        return client.get("/" + templateId, Map.of("fields", TEMPLATE_FIELDS), token);
    }

    /** Deleting by name removes every language of that template at Meta. */
    public void deleteByName(String wabaId, String name, String token) {
        client.delete("/" + wabaId + "/message_templates", Map.of("name", name), token);
    }

    /** All templates of a WABA following paging cursors. */
    public List<JsonNode> listAll(String wabaId, String token) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode page = client.get("/" + wabaId + "/message_templates", Map.of("fields", TEMPLATE_FIELDS, "limit", 100), token);
        int guard = 0;
        while (true) {
            page.path("data").forEach(out::add);
            String next = page.path("paging").path("next").asText(null);
            if (next == null || next.isBlank() || ++guard > 50) {
                break;
            }
            page = client.getAbsolute(next, token);
        }
        return out;
    }

    public void enableInsights(String wabaId, String token) {
        client.postQuery("/" + wabaId, Map.of("is_enabled_for_insights", "true"), token);
    }

    public JsonNode analytics(String wabaId, String metaTemplateId, long startUnix, long endUnix, String token) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("start", startUnix);
        q.put("end", endUnix);
        q.put("granularity", "DAILY");
        q.put("template_ids", "[" + metaTemplateId + "]");
        q.put("metric_types", "[\"SENT\",\"DELIVERED\",\"READ\",\"CLICKED\"]");
        return client.get("/" + wabaId + "/template_analytics", q, token);
    }

    public JsonNode library(Map<String, Object> query, String token) {
        return client.get("/message_template_library", query, token);
    }
}
