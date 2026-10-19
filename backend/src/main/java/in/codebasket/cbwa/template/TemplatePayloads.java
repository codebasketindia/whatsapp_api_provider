package in.codebasket.cbwa.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.template.dto.TemplateDtos.ButtonDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.ComponentDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between our ComponentDto (what the builder UI sends) and Meta's message_templates
 * component JSON. Example values are accepted in friendly shapes (["Ravi","#12"], {"name":"Ravi"})
 * or Meta's own shapes and always stored/sent in Meta's format.
 */
public final class TemplatePayloads {

    private TemplatePayloads() {
    }

    /** Sample values for the variables, in variable order. kind = "body_text" or "header_text". */
    public static List<String> exampleValues(JsonNode ex, String kind, List<String> vars, ParameterFormat format) {
        List<String> out = new ArrayList<>();
        if (ex == null || ex.isNull() || ex.isMissingNode()) {
            return out;
        }
        JsonNode named = ex.get(kind + "_named_params");
        if (named != null && named.isArray()) {
            Map<String, String> map = new LinkedHashMap<>();
            named.forEach(n -> map.put(n.path("param_name").asText(), n.path("example").asText()));
            vars.forEach(v -> {
                if (map.containsKey(v) && !map.get(v).isBlank()) {
                    out.add(map.get(v));
                }
            });
            return out;
        }
        JsonNode node = ex.has(kind) ? ex.get(kind) : ex;
        if (node.isArray() && !node.isEmpty() && node.get(0).isArray()) {
            node = node.get(0);
        }
        if (node.isArray()) {
            for (JsonNode n : node) {
                if (n.isObject() && n.has("param_name")) {
                    if (!n.path("example").asText("").isBlank()) {
                        out.add(n.path("example").asText());
                    }
                } else if (!n.asText("").isBlank()) {
                    out.add(n.asText());
                }
            }
            return out;
        }
        if (node.isObject()) {
            for (String v : vars) {
                String val = node.path(v).asText("");
                if (!val.isBlank()) {
                    out.add(val);
                }
            }
            return out;
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            out.add(node.asText());
        }
        return out;
    }

    /** Meta-format example node for a text with variables (or null when there are none). */
    public static JsonNode metaExample(String kind, String text, JsonNode example, ParameterFormat format) {
        List<String> vars = TemplateValidator.variables(text);
        if (vars.isEmpty()) {
            return null;
        }
        List<String> values = exampleValues(example, kind, vars, format);
        ObjectNode out = JsonUtil.MAPPER.createObjectNode();
        if (format == ParameterFormat.NAMED) {
            ArrayNode arr = out.putArray(kind + "_named_params");
            for (int i = 0; i < vars.size(); i++) {
                ObjectNode p = arr.addObject();
                p.put("param_name", vars.get(i));
                p.put("example", i < values.size() ? values.get(i) : "");
            }
        } else if ("header_text".equals(kind)) {
            ArrayNode arr = out.putArray(kind);
            values.forEach(arr::add);
        } else {
            ArrayNode arr = out.putArray(kind).addArray();
            values.forEach(arr::add);
        }
        return out;
    }

    /** Normalises a UI component into the shape we store (examples in Meta format). */
    public static ComponentDto normalise(ComponentDto c, ParameterFormat format) {
        String type = c.type().toUpperCase();
        String fmt = c.format() == null ? null : c.format().toUpperCase();
        JsonNode example = c.example();
        if (type.equals("BODY")) {
            example = metaExample("body_text", c.text(), c.example(), format);
        } else if (type.equals("HEADER") && (fmt == null || fmt.equals("TEXT"))) {
            fmt = "TEXT";
            example = metaExample("header_text", c.text(), c.example(), format);
        } else if (type.equals("HEADER") && fmt != null && !fmt.equals("LOCATION")) {
            String handle = c.headerHandle();
            if ((handle == null || handle.isBlank()) && example != null && example.path("header_handle").isArray()
                    && !example.path("header_handle").isEmpty()) {
                handle = example.path("header_handle").get(0).asText();
            }
            example = null;
            return new ComponentDto(type, fmt, null, null, null, c.headerMediaFileUid(), handle, null, null);
        } else if (type.equals("HEADER")) {
            return new ComponentDto(type, "LOCATION", null, null, null, null, null, null, null);
        } else if (type.equals("FOOTER")) {
            example = null;
        }
        List<ButtonDto> buttons = null;
        if (type.equals("BUTTONS") && c.buttons() != null) {
            buttons = new ArrayList<>();
            for (ButtonDto b : c.buttons()) {
                String bt = b.type() == null ? null : b.type().toUpperCase();
                JsonNode bex = b.example();
                if ("URL".equals(bt) && bex != null && bex.isTextual()) {
                    bex = JsonUtil.MAPPER.createArrayNode().add(bex.asText());
                }
                if ("URL".equals(bt) && TemplateValidator.variables(b.url()).isEmpty()) {
                    bex = null;
                }
                if ("COPY_CODE".equals(bt) && bex != null && bex.isArray() && !bex.isEmpty()) {
                    bex = bex.get(0);
                }
                String phone = b.phoneNumber() == null ? null : b.phoneNumber().replaceAll("[ -]", "");
                buttons.add(new ButtonDto(bt, b.text() == null ? null : b.text().trim(), b.url() == null ? null : b.url().trim(),
                        phone, bex, b.otpType() == null ? null : b.otpType().toUpperCase(), b.packageName(), b.signatureHash(),
                        b.autofillText()));
            }
        }
        return new ComponentDto(type, fmt, c.text(), example, buttons, null, null, c.addSecurityRecommendation(),
                c.codeExpirationMinutes());
    }

    /** Meta "components" array for create/edit. */
    public static List<Map<String, Object>> toMeta(List<ComponentDto> components, String category) {
        boolean auth = "AUTHENTICATION".equals(category);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ComponentDto c : components) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", c.type());
            switch (c.type()) {
                case "HEADER" -> {
                    m.put("format", c.format());
                    if ("TEXT".equals(c.format())) {
                        m.put("text", c.text());
                        if (c.example() != null) {
                            m.put("example", c.example());
                        }
                    } else if (!"LOCATION".equals(c.format()) && c.headerHandle() != null) {
                        m.put("example", Map.of("header_handle", List.of(c.headerHandle())));
                    }
                }
                case "BODY" -> {
                    if (auth) {
                        m.put("add_security_recommendation", Boolean.TRUE.equals(c.addSecurityRecommendation()));
                    } else {
                        m.put("text", c.text());
                        if (c.example() != null) {
                            m.put("example", c.example());
                        }
                    }
                }
                case "FOOTER" -> {
                    if (auth) {
                        if (c.codeExpirationMinutes() == null) {
                            continue;
                        }
                        m.put("code_expiration_minutes", c.codeExpirationMinutes());
                    } else {
                        m.put("text", c.text());
                    }
                }
                case "BUTTONS" -> {
                    List<Map<String, Object>> buttons = new ArrayList<>();
                    for (ButtonDto b : c.buttons() == null ? List.<ButtonDto>of() : c.buttons()) {
                        Map<String, Object> bm = new LinkedHashMap<>();
                        bm.put("type", b.type());
                        switch (b.type()) {
                            case "QUICK_REPLY" -> bm.put("text", b.text());
                            case "URL" -> {
                                bm.put("text", b.text());
                                bm.put("url", b.url());
                                if (b.example() != null) {
                                    bm.put("example", b.example());
                                }
                            }
                            case "PHONE_NUMBER" -> {
                                bm.put("text", b.text());
                                bm.put("phone_number", b.phoneNumber());
                            }
                            case "COPY_CODE" -> bm.put("example", b.example() == null ? null : b.example().asText());
                            case "OTP" -> {
                                String ot = b.otpType() == null ? "COPY_CODE" : b.otpType();
                                bm.put("otp_type", ot);
                                if (b.text() != null && !b.text().isBlank()) {
                                    bm.put("text", b.text());
                                }
                                if (!"COPY_CODE".equals(ot)) {
                                    if (b.autofillText() != null && !b.autofillText().isBlank()) {
                                        bm.put("autofill_text", b.autofillText());
                                    }
                                    bm.put("supported_apps", List.of(Map.of("package_name", b.packageName(),
                                            "signature_hash", b.signatureHash())));
                                }
                            }
                            default -> { }
                        }
                        buttons.add(bm);
                    }
                    m.put("buttons", buttons);
                }
                default -> { }
            }
            out.add(m);
        }
        return out;
    }

    /** Parses Meta's components array (sync / webhook) into our DTOs. */
    public static List<ComponentDto> fromMeta(JsonNode components) {
        List<ComponentDto> out = new ArrayList<>();
        if (components == null || !components.isArray()) {
            return out;
        }
        for (JsonNode c : components) {
            String type = c.path("type").asText().toUpperCase();
            List<ButtonDto> buttons = null;
            if (type.equals("BUTTONS")) {
                buttons = new ArrayList<>();
                for (JsonNode b : c.path("buttons")) {
                    JsonNode app = b.path("supported_apps").isArray() && !b.path("supported_apps").isEmpty()
                            ? b.path("supported_apps").get(0) : JsonUtil.MAPPER.createObjectNode();
                    buttons.add(new ButtonDto(b.path("type").asText().toUpperCase(), b.path("text").asText(null),
                            b.path("url").asText(null), b.path("phone_number").asText(null),
                            b.has("example") ? b.get("example") : null, b.path("otp_type").asText(null),
                            app.path("package_name").asText(b.path("package_name").asText(null)),
                            app.path("signature_hash").asText(b.path("signature_hash").asText(null)),
                            b.path("autofill_text").asText(null)));
                }
            }
            String handle = c.path("example").path("header_handle").isArray() && !c.path("example").path("header_handle").isEmpty()
                    ? c.path("example").path("header_handle").get(0).asText() : null;
            JsonNode example = c.has("example") && handle == null ? c.get("example") : null;
            out.add(new ComponentDto(type, c.path("format").asText(null), c.path("text").asText(null), example, buttons, null,
                    handle, c.has("add_security_recommendation") ? c.get("add_security_recommendation").asBoolean() : null,
                    c.has("code_expiration_minutes") ? c.get("code_expiration_minutes").asInt() : null));
        }
        return out;
    }
}
