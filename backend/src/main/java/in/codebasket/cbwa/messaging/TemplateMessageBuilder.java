package in.codebasket.cbwa.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.ButtonVariable;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.HeaderVariable;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.TemplateRef;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.TemplateVariables;
import in.codebasket.cbwa.template.ComponentType;
import in.codebasket.cbwa.template.ParameterFormat;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateComponent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the Meta "template" object for a send request from the stored template definition and the
 * caller's variables, validating that every variable the template needs is supplied.
 */
public final class TemplateMessageBuilder {

    static final Pattern VAR = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private TemplateMessageBuilder() {
    }

    public record Result(ObjectNode template, String preview) {
    }

    /**
     * @param mediaResolver converts a header variable into a Meta media object ({id} / {link} / {_fileUid})
     */
    public static Result build(Template t, List<TemplateComponent> components, TemplateRef ref,
                               Function<HeaderVariable, ObjectNode> mediaResolver) {
        boolean named = t.getParameterFormat() == ParameterFormat.NAMED;
        boolean auth = t.getCategory() == MessageCategory.AUTHENTICATION;
        TemplateVariables vars = ref.variables();
        List<String> bodyValues = vars == null || vars.body() == null ? List.of() : vars.body();
        Map<String, String> namedValues = ref.namedVariables() == null ? Map.of() : ref.namedVariables();

        ObjectNode template = JsonUtil.MAPPER.createObjectNode();
        template.put("name", t.getName());
        template.putObject("language").put("code", t.getLanguage());
        ArrayNode comps = JsonUtil.MAPPER.createArrayNode();
        String preview = null;
        String otpCode = null;

        for (TemplateComponent c : components) {
            if (c.getType() == ComponentType.HEADER) {
                ObjectNode header = buildHeader(c, vars == null ? null : vars.header(), named, namedValues, mediaResolver);
                if (header != null) {
                    comps.add(header);
                }
            } else if (c.getType() == ComponentType.BODY) {
                if (auth) {
                    otpCode = !bodyValues.isEmpty() ? bodyValues.get(0)
                            : namedValues.values().stream().findFirst().orElse(null);
                    if (otpCode == null || otpCode.isBlank()) {
                        throw mismatch("Authentication templates need the one-time code as the first body variable");
                    }
                    ObjectNode body = comps.addObject().put("type", "body");
                    body.putArray("parameters").addObject().put("type", "text").put("text", otpCode);
                    preview = (c.getText() == null ? "{{1}} is your verification code." : c.getText()).replace("{{1}}", otpCode);
                    continue;
                }
                String text = c.getText() == null ? "" : c.getText();
                List<String> needed = variables(text);
                if (needed.isEmpty()) {
                    preview = text;
                    continue;
                }
                ObjectNode body = comps.addObject().put("type", "body");
                ArrayNode params = body.putArray("parameters");
                String rendered = text;
                if (named) {
                    for (String name : needed) {
                        String v = namedValues.get(name);
                        if (v == null || v.isBlank()) {
                            throw mismatch("Missing value for variable {{" + name + "}}");
                        }
                        params.addObject().put("type", "text").put("parameter_name", name).put("text", v);
                        rendered = rendered.replace("{{" + name + "}}", v);
                    }
                } else {
                    if (bodyValues.size() != needed.size()) {
                        throw mismatch("This template needs " + needed.size() + " body variable(s), " + bodyValues.size() + " given");
                    }
                    for (int i = 0; i < bodyValues.size(); i++) {
                        String v = bodyValues.get(i);
                        if (v == null || v.isBlank()) {
                            throw mismatch("Body variable {{" + (i + 1) + "}} is empty");
                        }
                        params.addObject().put("type", "text").put("text", v);
                        rendered = rendered.replace("{{" + (i + 1) + "}}", v);
                    }
                }
                preview = rendered;
            } else if (c.getType() == ComponentType.BUTTONS) {
                addButtons(c, vars == null || vars.buttons() == null ? List.of() : vars.buttons(), otpCode, auth, comps);
            }
        }
        if (comps.size() > 0) {
            template.set("components", comps);
        }
        return new Result(template, preview == null ? "[template] " + t.getName() : preview);
    }

    /** Distinct variables in order of first appearance; positional ones are sorted numerically. */
    static List<String> variables(String text) {
        if (text == null) {
            return List.of();
        }
        Matcher m = VAR.matcher(text);
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        if (!found.isEmpty() && found.stream().allMatch(s -> s.matches("\\d+"))) {
            TreeSet<Integer> nums = new TreeSet<>();
            found.forEach(s -> nums.add(Integer.parseInt(s)));
            List<String> out = new ArrayList<>();
            nums.forEach(n -> out.add(String.valueOf(n)));
            return out;
        }
        return new ArrayList<>(found);
    }

    private static ObjectNode buildHeader(TemplateComponent c, HeaderVariable hv, boolean named, Map<String, String> namedValues,
                                          Function<HeaderVariable, ObjectNode> mediaResolver) {
        String format = c.getFormat() == null ? "TEXT" : c.getFormat().toUpperCase();
        switch (format) {
            case "TEXT" -> {
                List<String> needed = variables(c.getText());
                if (needed.isEmpty()) {
                    return null;
                }
                String value;
                if (named) {
                    value = namedValues.get(needed.get(0));
                    if ((value == null || value.isBlank()) && hv != null) {
                        value = hv.text();
                    }
                } else {
                    value = hv == null ? null : hv.text();
                }
                if (value == null || value.isBlank()) {
                    throw mismatch("This template needs a header text value");
                }
                ObjectNode h = JsonUtil.MAPPER.createObjectNode().put("type", "header");
                ObjectNode p = h.putArray("parameters").addObject().put("type", "text").put("text", value);
                if (named) {
                    p.put("parameter_name", needed.get(0));
                }
                return h;
            }
            case "IMAGE", "VIDEO", "DOCUMENT" -> {
                if (hv == null || (blank(hv.fileUid()) && blank(hv.metaMediaId()) && blank(hv.link()))) {
                    throw mismatch("This template needs a header " + format.toLowerCase() + " (upload a file or give a link)");
                }
                String type = format.toLowerCase();
                ObjectNode media = mediaResolver.apply(new HeaderVariable(type, null, hv.fileUid(), hv.metaMediaId(), hv.link(),
                        hv.filename(), null, null, null, null));
                if ("document".equals(type) && !blank(hv.filename())) {
                    media.put("filename", hv.filename());
                }
                ObjectNode h = JsonUtil.MAPPER.createObjectNode().put("type", "header");
                ObjectNode p = h.putArray("parameters").addObject().put("type", type);
                p.set(type, media);
                return h;
            }
            case "LOCATION" -> {
                if (hv == null || hv.latitude() == null || hv.longitude() == null) {
                    throw mismatch("This template needs a header location (latitude and longitude)");
                }
                ObjectNode h = JsonUtil.MAPPER.createObjectNode().put("type", "header");
                ObjectNode loc = h.putArray("parameters").addObject().put("type", "location").putObject("location");
                loc.put("latitude", hv.latitude()).put("longitude", hv.longitude());
                if (!blank(hv.name())) {
                    loc.put("name", hv.name());
                }
                if (!blank(hv.address())) {
                    loc.put("address", hv.address());
                }
                return h;
            }
            default -> {
                return null;
            }
        }
    }

    private static void addButtons(TemplateComponent c, List<ButtonVariable> given, String otpCode, boolean auth, ArrayNode comps) {
        if (c.getButtons() == null || c.getButtons().isBlank()) {
            return;
        }
        JsonNode buttons = JsonUtil.readTree(c.getButtons());
        if (buttons.isObject() && buttons.has("buttons")) {
            buttons = buttons.get("buttons");
        }
        if (!buttons.isArray()) {
            return;
        }
        for (int i = 0; i < buttons.size(); i++) {
            JsonNode b = buttons.get(i);
            String type = b.path("type").asText("").toUpperCase();
            ButtonVariable bv = findButton(given, i);
            switch (type) {
                case "URL" -> {
                    if (b.path("url").asText("").contains("{{")) {
                        if (bv == null || blank(bv.value())) {
                            throw mismatch("Button " + (i + 1) + " needs the dynamic URL value");
                        }
                        comps.add(button("url", i, JsonUtil.MAPPER.createObjectNode().put("type", "text").put("text", bv.value())));
                    }
                }
                case "OTP" -> {
                    String code = bv != null && !blank(bv.value()) ? bv.value() : otpCode;
                    if (blank(code)) {
                        throw mismatch("Authentication button needs the one-time code");
                    }
                    comps.add(button("url", i, JsonUtil.MAPPER.createObjectNode().put("type", "text").put("text", code)));
                }
                case "COPY_CODE" -> {
                    String code = bv != null && !blank(bv.value()) ? bv.value() : (auth ? otpCode : null);
                    if (blank(code)) {
                        throw mismatch("Copy code button " + (i + 1) + " needs a code value");
                    }
                    comps.add(button("copy_code", i, JsonUtil.MAPPER.createObjectNode().put("type", "coupon_code").put("coupon_code", code)));
                }
                case "QUICK_REPLY" -> {
                    if (bv != null && !blank(bv.value())) {
                        comps.add(button("quick_reply", i, JsonUtil.MAPPER.createObjectNode().put("type", "payload").put("payload", bv.value())));
                    }
                }
                default -> {
                    // PHONE_NUMBER, FLOW and other static buttons need no send-time parameters
                }
            }
        }
    }

    private static ObjectNode button(String subType, int index, ObjectNode param) {
        ObjectNode n = JsonUtil.MAPPER.createObjectNode().put("type", "button").put("sub_type", subType).put("index", String.valueOf(index));
        n.putArray("parameters").add(param);
        return n;
    }

    private static ButtonVariable findButton(List<ButtonVariable> list, int index) {
        return list.stream().filter(b -> b.index() == index).findFirst().orElse(null);
    }

    private static AppException mismatch(String msg) {
        return new AppException(ErrorCode.TEMPLATE_VARIABLES_MISMATCH, msg);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
