package in.codebasket.cbwa.partnerhook;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.common.util.JsonUtil;
import org.springframework.stereotype.Component;

/** Postman v2.1 collection for the public API (contract PU1–PU9). */
@Component
public class PostmanCollectionBuilder {

    public String build(String baseUrl) {
        ObjectNode root = JsonUtil.MAPPER.createObjectNode();
        ObjectNode info = root.putObject("info");
        info.put("name", "CB WhatsApp Gateway - Public API");
        info.put("description", "Set apiKey and apiSecret collection variables from Developer > API keys.");
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");

        ArrayNode vars = root.putArray("variable");
        var(vars, "baseUrl", baseUrl);
        var(vars, "apiKey", "cbk_live_xxxxxxxxxxxxxxxxxxxxxxxx");
        var(vars, "apiSecret", "cbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

        ArrayNode items = root.putArray("item");
        item(items, "Send text message", "POST", "/messages/send", """
                {
                  "senderId": 1,
                  "to": "919812345678",
                  "type": "text",
                  "text": { "body": "Hello from CB WhatsApp Gateway", "previewUrl": false },
                  "clientReference": "ORDER-1001"
                }""");
        item(items, "Send template message", "POST", "/messages/send", """
                {
                  "senderId": 1,
                  "to": "919812345678",
                  "type": "template",
                  "template": { "name": "order_update", "language": "en", "variables": { "body": ["Ravi", "#1001"] } },
                  "clientReference": "ORDER-1001"
                }""");
        item(items, "Send bulk template messages", "POST", "/messages/bulk", """
                {
                  "senderId": 1,
                  "template": { "name": "order_update", "language": "en" },
                  "recipients": [
                    { "to": "919812345678", "variables": { "body": ["Ravi", "#1001"] }, "clientReference": "ORDER-1001" },
                    { "to": "919812345679", "variables": { "body": ["Asha", "#1002"] }, "clientReference": "ORDER-1002" }
                  ]
                }""");
        item(items, "Get message status", "GET", "/messages/{{messageId}}", null);
        item(items, "List templates", "GET", "/templates?status=APPROVED&page=0&size=20", null);
        item(items, "Create template", "POST", "/templates", """
                {
                  "wabaId": "123456789012345",
                  "name": "order_update",
                  "language": "en",
                  "category": "UTILITY",
                  "parameterFormat": "POSITIONAL",
                  "components": [
                    { "type": "BODY", "text": "Hi {{1}}, your order {{2}} is on the way.", "example": { "body_text": [["Ravi", "#1001"]] } }
                  ]
                }""");
        item(items, "List senders", "GET", "/senders", null);
        item(items, "Wallet balance", "GET", "/wallet/balance", null);
        item(items, "List contacts", "GET", "/contacts?page=0&size=20", null);
        item(items, "Create contact", "POST", "/contacts", """
                {
                  "phone": "919812345678",
                  "name": "Ravi Kumar",
                  "tags": ["customer"],
                  "attributes": { "city": "Pune" }
                }""");
        return root.toPrettyString();
    }

    private static void var(ArrayNode vars, String key, String value) {
        ObjectNode v = vars.addObject();
        v.put("key", key);
        v.put("value", value);
    }

    private static void item(ArrayNode items, String name, String method, String path, String body) {
        ObjectNode item = items.addObject();
        item.put("name", name);
        ObjectNode req = item.putObject("request");
        req.put("method", method);
        ArrayNode headers = req.putArray("header");
        header(headers, "X-API-KEY", "{{apiKey}}");
        header(headers, "X-API-SECRET", "{{apiSecret}}");
        if (body != null) {
            header(headers, "Content-Type", "application/json");
            ObjectNode b = req.putObject("body");
            b.put("mode", "raw");
            b.put("raw", body);
            b.putObject("options").putObject("raw").put("language", "json");
        }
        req.put("url", "{{baseUrl}}" + path);
    }

    private static void header(ArrayNode headers, String key, String value) {
        ObjectNode h = headers.addObject();
        h.put("key", key);
        h.put("value", value);
    }
}
