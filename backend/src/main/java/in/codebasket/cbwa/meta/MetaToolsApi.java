package in.codebasket.cbwa.meta;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Graph API calls for business profile, QR codes, conversational components and block list (F4-F5). */
@Component
@RequiredArgsConstructor
public class MetaToolsApi {

    private static final String PROFILE_FIELDS = "about,address,description,email,profile_picture_url,websites,vertical";

    private final MetaGraphClient client;

    public JsonNode getProfile(String phoneNumberId, String token) {
        JsonNode res = client.get("/" + phoneNumberId + "/whatsapp_business_profile", Map.of("fields", PROFILE_FIELDS), token);
        JsonNode data = res.path("data");
        return data.isArray() && !data.isEmpty() ? data.get(0) : res;
    }

    public void updateProfile(String phoneNumberId, Map<String, Object> fields, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.putAll(fields);
        client.post("/" + phoneNumberId + "/whatsapp_business_profile", body, token);
    }

    public JsonNode createQr(String phoneNumberId, String message, String token) {
        return client.post("/" + phoneNumberId + "/message_qrdls",
                Map.of("prefilled_message", message, "generate_qr_image", "PNG"), token);
    }

    public JsonNode updateQr(String phoneNumberId, String code, String message, String token) {
        return client.post("/" + phoneNumberId + "/message_qrdls",
                Map.of("code", code, "prefilled_message", message, "generate_qr_image", "PNG"), token);
    }

    public void deleteQr(String phoneNumberId, String code, String token) {
        client.delete("/" + phoneNumberId + "/message_qrdls/" + code, null, token);
    }

    public JsonNode getConversationalAutomation(String phoneNumberId, String token) {
        return client.get("/" + phoneNumberId, Map.of("fields", "conversational_automation"), token)
                .path("conversational_automation");
    }

    public void setConversationalAutomation(String phoneNumberId, boolean welcome, List<String> prompts,
                                            List<Map<String, String>> commands, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enable_welcome_message", welcome);
        body.put("prompts", prompts);
        body.put("commands", commands.stream()
                .map(c -> Map.of("command_name", c.get("name"), "command_description", c.get("description"))).toList());
        client.post("/" + phoneNumberId + "/conversational_automation", body, token);
    }

    public JsonNode blockUsers(String phoneNumberId, List<String> waIds, String token) {
        return client.post("/" + phoneNumberId + "/block_users", blockBody(waIds), token);
    }

    public JsonNode unblockUsers(String phoneNumberId, List<String> waIds, String token) {
        return client.deleteWithBody("/" + phoneNumberId + "/block_users", blockBody(waIds), token);
    }

    private static Map<String, Object> blockBody(List<String> waIds) {
        return Map.of("messaging_product", "whatsapp", "block_users", waIds.stream().map(w -> Map.of("user", w)).toList());
    }
}
