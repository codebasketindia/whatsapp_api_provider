package in.codebasket.cbwa.campaign;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.HeaderMedia;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.template.ComponentType;
import in.codebasket.cbwa.template.ParameterFormat;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a campaign needs to know about a template: body text and its variables, header type /
 * header text variable, and URL buttons with a dynamic suffix. Mapping keys used by campaigns:
 * body variables by name ("1", "2" or "customer_name"), "header" for a header text variable and
 * "button_{index}" for dynamic URL buttons.
 */
public final class TemplateSpec {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private final Template template;
    private final String bodyText;
    private final String headerFormat;
    private final String headerText;
    private final List<String> bodyKeys;
    private final boolean headerVariable;
    private final List<Integer> dynamicUrlButtons;

    private TemplateSpec(Template template, List<TemplateComponent> components) {
        this.template = template;
        String body = "";
        String hFormat = null;
        String hText = null;
        List<Integer> urlButtons = new ArrayList<>();
        for (TemplateComponent c : components) {
            if (c.getType() == ComponentType.BODY) {
                body = c.getText() == null ? "" : c.getText();
            } else if (c.getType() == ComponentType.HEADER) {
                hFormat = c.getFormat() == null ? "TEXT" : c.getFormat().toUpperCase();
                hText = c.getText();
            } else if (c.getType() == ComponentType.BUTTONS && c.getButtons() != null) {
                JsonNode buttons = JsonUtil.readTree(c.getButtons());
                JsonNode arr = buttons.isArray() ? buttons : buttons.path("buttons");
                for (int i = 0; i < arr.size(); i++) {
                    JsonNode b = arr.get(i);
                    String type = b.path("type").asText("").toUpperCase();
                    if ("URL".equals(type) && b.path("url").asText("").contains("{{")) {
                        urlButtons.add(i);
                    }
                }
            }
        }
        this.bodyText = body;
        this.headerFormat = hFormat;
        this.headerText = hText;
        this.bodyKeys = keys(body);
        this.headerVariable = "TEXT".equals(hFormat) && hText != null && VAR.matcher(hText).find();
        this.dynamicUrlButtons = urlButtons;
    }

    public static TemplateSpec of(Template template, List<TemplateComponent> components) {
        return new TemplateSpec(template, components.stream().sorted(Comparator.comparingInt(TemplateComponent::getPosition)).toList());
    }

    public Template template() {
        return template;
    }

    public String bodyText() {
        return bodyText;
    }

    public String headerFormat() {
        return headerFormat;
    }

    public boolean needsHeaderMedia() {
        return "IMAGE".equals(headerFormat) || "VIDEO".equals(headerFormat) || "DOCUMENT".equals(headerFormat)
                || "LOCATION".equals(headerFormat);
    }

    /** Every mapping key that must have a value for each recipient. */
    public Set<String> requiredKeys() {
        Set<String> keys = new LinkedHashSet<>(bodyKeys);
        if (headerVariable) {
            keys.add("header");
        }
        dynamicUrlButtons.forEach(i -> keys.add("button_" + i));
        return keys;
    }

    public String render(Map<String, String> vars) {
        Matcher m = VAR.matcher(bodyText);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group(0) : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Builds the contract SendMessageRequest for one recipient. */
    public SendMessageRequest request(Long senderId, String to, Map<String, String> vars, HeaderMedia headerMedia) {
        boolean named = template.getParameterFormat() == ParameterFormat.NAMED;
        List<String> body = null;
        Map<String, String> namedVars = null;
        if (named) {
            namedVars = new java.util.LinkedHashMap<>();
            for (String k : bodyKeys) {
                namedVars.put(k, vars.getOrDefault(k, ""));
            }
        } else {
            body = new ArrayList<>();
            List<String> ordered = new ArrayList<>(bodyKeys);
            ordered.sort(Comparator.comparingInt(TemplateSpec::numeric));
            for (String k : ordered) {
                body.add(vars.getOrDefault(k, ""));
            }
        }
        SendMessageRequest.HeaderVariable header = null;
        if (headerVariable) {
            header = new SendMessageRequest.HeaderVariable("text", vars.getOrDefault("header", ""), null, null, null, null,
                    null, null, null, null);
        } else if (needsHeaderMedia() && headerMedia != null) {
            String type = headerFormat.toLowerCase();
            header = new SendMessageRequest.HeaderVariable(type, null, headerMedia.fileUid(), headerMedia.metaMediaId(),
                    headerMedia.link(), headerMedia.filename(), headerMedia.latitude(), headerMedia.longitude(),
                    headerMedia.name(), headerMedia.address());
        }
        List<SendMessageRequest.ButtonVariable> buttons = null;
        if (!dynamicUrlButtons.isEmpty()) {
            buttons = new ArrayList<>();
            for (Integer i : dynamicUrlButtons) {
                buttons.add(new SendMessageRequest.ButtonVariable(i, "url", vars.getOrDefault("button_" + i, "")));
            }
        }
        SendMessageRequest.TemplateRef ref = new SendMessageRequest.TemplateRef(template.getId(), template.getName(),
                template.getLanguage(), new SendMessageRequest.TemplateVariables(body, header, buttons), namedVars);
        return new SendMessageRequest(senderId, null, to, "template", null, null, null, null, null, null, null, null, ref, null);
    }

    private static int numeric(String k) {
        try {
            return Integer.parseInt(k);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static List<String> keys(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = VAR.matcher(text == null ? "" : text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return new ArrayList<>(out);
    }
}
