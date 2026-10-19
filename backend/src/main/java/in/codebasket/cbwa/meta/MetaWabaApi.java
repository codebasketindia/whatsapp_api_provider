package in.codebasket.cbwa.meta;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Graph API calls for WhatsApp Business Accounts and phone numbers (F1-F3). */
@Component
@RequiredArgsConstructor
public class MetaWabaApi {

    public static final String WABA_FIELDS = "id,name,currency,timezone_id,message_template_namespace,"
            + "account_review_status,business_verification_status,owner_business_info,primary_funding_id";

    public static final String PHONE_FIELDS = "id,display_phone_number,verified_name,code_verification_status,"
            + "quality_rating,platform_type,throughput,name_status,new_name_status,status,messaging_limit_tier,"
            + "health_status,is_official_business_account";

    private final MetaGraphClient client;

    public JsonNode getWaba(String wabaId, String token) {
        return client.get("/" + wabaId, Map.of("fields", WABA_FIELDS), token);
    }

    public List<JsonNode> listPhoneNumbers(String wabaId, String token) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode res = client.get("/" + wabaId + "/phone_numbers", Map.of("fields", PHONE_FIELDS, "limit", 100), token);
        res.path("data").forEach(out::add);
        return out;
    }

    public JsonNode getPhone(String phoneNumberId, String token) {
        return client.get("/" + phoneNumberId, Map.of("fields", PHONE_FIELDS), token);
    }

    public JsonNode getPhoneHealth(String phoneNumberId, String token) {
        return client.get("/" + phoneNumberId,
                Map.of("fields", "health_status,quality_rating,messaging_limit_tier,throughput,name_status,status"), token);
    }

    public void subscribeApp(String wabaId, String token) {
        client.post("/" + wabaId + "/subscribed_apps", Map.of(), token);
    }

    public void unsubscribeApp(String wabaId, String token) {
        client.delete("/" + wabaId + "/subscribed_apps", null, token);
    }

    public JsonNode subscribedApps(String wabaId, String token) {
        return client.get("/" + wabaId + "/subscribed_apps", null, token);
    }

    public void register(String phoneNumberId, String pin, String token) {
        client.post("/" + phoneNumberId + "/register", Map.of("messaging_product", "whatsapp", "pin", pin), token);
    }

    public void deregister(String phoneNumberId, String token) {
        client.post("/" + phoneNumberId + "/deregister", Map.of(), token);
    }

    public void requestCode(String phoneNumberId, String method, String language, String token) {
        client.post("/" + phoneNumberId + "/request_code", Map.of("code_method", method, "language", language), token);
    }

    public void verifyCode(String phoneNumberId, String code, String token) {
        client.post("/" + phoneNumberId + "/verify_code", Map.of("code", code), token);
    }

    public void setTwoStepPin(String phoneNumberId, String pin, String token) {
        client.post("/" + phoneNumberId, Map.of("pin", pin), token);
    }

    /** Coexistence onboarding: sync contacts/app state, then message history. */
    public void smbAppDataSync(String phoneNumberId, String syncType, String token) {
        client.post("/" + phoneNumberId + "/smb_app_data", Map.of("messaging_product", "whatsapp", "sync_type", syncType), token);
    }

    /** Solution Partner: share our credit line with the partner WABA. */
    public void shareCreditLine(String creditLineId, String wabaId, String token) {
        client.postQuery("/" + creditLineId + "/whatsapp_credit_sharing_and_attach",
                Map.of("waba_currency", "INR", "waba_id", wabaId), token);
    }

    public JsonNode appInfo(String appId, String appToken) {
        return client.get("/" + appId, Map.of("fields", "id,name"), appToken);
    }

    public JsonNode appSubscriptions(String appId, String appToken) {
        return client.get("/" + appId + "/subscriptions", null, appToken);
    }

    public JsonNode pricingAnalytics(String wabaId, long startUnix, long endUnix, String phoneDigits, String token) {
        String field = "pricing_analytics.start(" + startUnix + ").end(" + endUnix + ").granularity(DAILY)"
                + (phoneDigits == null ? "" : ".phone_numbers([\"" + phoneDigits + "\"])")
                + ".dimensions([\"PRICING_CATEGORY\",\"PRICING_TYPE\",\"COUNTRY\",\"PHONE\"])";
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("fields", field);
        return client.get("/" + wabaId, q, token);
    }
}
