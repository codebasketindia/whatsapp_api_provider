package in.codebasket.cbwa.template;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.FieldErrorDto;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.template.dto.TemplateDtos.ButtonDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.ComponentDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates a template against Meta's rules before it is saved or submitted (F6), so partners get
 * clear field errors instead of a Meta rejection. All problems are collected and returned together.
 */
public final class TemplateValidator {

    public static final Pattern NAME = Pattern.compile("^[a-z0-9_]{1,512}$");
    public static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2,3}(_[A-Z]{2})?$");
    public static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    private static final Pattern NAMED_VAR = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
    private static final Set<String> CATEGORIES = Set.of("MARKETING", "UTILITY", "AUTHENTICATION");
    private static final Set<String> MEDIA = Set.of("IMAGE", "VIDEO", "DOCUMENT");

    private final List<FieldErrorDto> errors = new ArrayList<>();

    private TemplateValidator() {
    }

    /** Returns the variable names used in a text, in order of first appearance. */
    public static List<String> variables(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text != null) {
            Matcher m = VARIABLE.matcher(text);
            while (m.find()) {
                out.add(m.group(1));
            }
        }
        return new ArrayList<>(out);
    }

    public static void validate(String name, String language, String category, ParameterFormat format, List<ComponentDto> components) {
        TemplateValidator v = new TemplateValidator();
        v.run(name, language, category, format, components);
        if (!v.errors.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, v.errors.get(0).message(), null, v.errors);
        }
    }

    private void run(String name, String language, String category, ParameterFormat format, List<ComponentDto> components) {
        if (name == null || !NAME.matcher(name).matches()) {
            err("name", "Use only lowercase letters, numbers and underscores (e.g. order_update)");
        }
        if (language == null || !LANGUAGE.matcher(language).matches()) {
            err("language", "Choose a language like en, en_US or hi");
        }
        if (category == null || !CATEGORIES.contains(category)) {
            err("category", "Choose Marketing, Utility or Authentication");
            return;
        }
        if (components == null || components.isEmpty()) {
            err("components", "Add the message body");
            return;
        }
        String[] order = {"HEADER", "BODY", "FOOTER", "BUTTONS"};
        int last = -1;
        Set<String> seen = new LinkedHashSet<>();
        for (ComponentDto c : components) {
            String t = c.type() == null ? "" : c.type().toUpperCase();
            int idx = List.of(order).indexOf(t);
            if (idx < 0) {
                err("components", "Unknown component type " + c.type());
                continue;
            }
            if (!seen.add(t)) {
                err("components", "Only one " + t.toLowerCase() + " is allowed");
            }
            if (idx < last) {
                err("components", "Components must be in the order header, body, footer, buttons");
            }
            last = Math.max(last, idx);
        }
        if (!seen.contains("BODY")) {
            err("components", "The message body is required");
        }
        if ("AUTHENTICATION".equals(category)) {
            authentication(components);
            return;
        }
        for (ComponentDto c : components) {
            switch (c.type().toUpperCase()) {
                case "HEADER" -> header(c, format);
                case "BODY" -> body(c, format);
                case "FOOTER" -> footer(c);
                case "BUTTONS" -> buttons(c, category);
                default -> { }
            }
        }
    }

    private void header(ComponentDto c, ParameterFormat format) {
        String f = c.format() == null ? "TEXT" : c.format().toUpperCase();
        if (f.equals("TEXT")) {
            if (blank(c.text())) {
                err("header", "Header text is empty");
                return;
            }
            if (c.text().length() > 60) {
                err("header", "Header text can be at most 60 characters");
            }
            List<String> vars = variables(c.text());
            if (vars.size() > 1) {
                err("header", "Header text can have only one variable");
            }
            checkVars("header", vars, format);
            if (!vars.isEmpty() && TemplatePayloads.exampleValues(c.example(), "header_text", vars, format).size() < vars.size()) {
                err("header", "Add a sample value for the header variable");
            }
        } else if (MEDIA.contains(f)) {
            if (blank(c.headerHandle()) && blank(c.headerMediaFileUid())
                    && (c.example() == null || !c.example().has("header_handle"))) {
                err("header", "Upload a sample " + f.toLowerCase() + " for the header");
            }
        } else if (!f.equals("LOCATION")) {
            err("header", "Header must be text, image, video, document or location");
        }
    }

    private void body(ComponentDto c, ParameterFormat format) {
        if (blank(c.text())) {
            err("body", "Message body is empty");
            return;
        }
        if (c.text().length() > 1024) {
            err("body", "Message body can be at most 1024 characters");
        }
        List<String> vars = variables(c.text());
        checkVars("body", vars, format);
        String trimmed = c.text().trim();
        if (!vars.isEmpty() && (trimmed.startsWith("{{") || trimmed.endsWith("}}"))) {
            err("body", "The body cannot start or end with a variable - add some text before/after it");
        }
        if (!vars.isEmpty() && TemplatePayloads.exampleValues(c.example(), "body_text", vars, format).size() < vars.size()) {
            err("body", "Add a sample value for every variable in the body");
        }
        if (vars.size() > 0 && trimmed.replaceAll("\\{\\{[^}]*}}", "").trim().length() < vars.size() * 2) {
            err("body", "Too many variables for the amount of text");
        }
    }

    private void footer(ComponentDto c) {
        if (blank(c.text())) {
            err("footer", "Footer text is empty");
            return;
        }
        if (c.text().length() > 60) {
            err("footer", "Footer can be at most 60 characters");
        }
        if (!variables(c.text()).isEmpty()) {
            err("footer", "Footer cannot contain variables");
        }
    }

    private void buttons(ComponentDto c, String category) {
        List<ButtonDto> buttons = c.buttons() == null ? List.of() : c.buttons();
        if (buttons.isEmpty()) {
            err("buttons", "Add at least one button or remove the buttons section");
            return;
        }
        if (buttons.size() > 10) {
            err("buttons", "At most 10 buttons are allowed");
        }
        int url = 0;
        int phone = 0;
        int copy = 0;
        boolean quickBlockEnded = false;
        boolean inQuick = false;
        boolean seenQuick = false;
        for (int i = 0; i < buttons.size(); i++) {
            ButtonDto b = buttons.get(i);
            String t = b.type() == null ? "" : b.type().toUpperCase();
            String field = "buttons[" + i + "]";
            if (t.equals("QUICK_REPLY")) {
                if (seenQuick && quickBlockEnded) {
                    err(field, "Quick reply buttons must be next to each other");
                }
                seenQuick = true;
                inQuick = true;
            } else if (inQuick) {
                quickBlockEnded = true;
                inQuick = false;
            }
            switch (t) {
                case "QUICK_REPLY" -> textCheck(field, b.text());
                case "URL" -> {
                    url++;
                    textCheck(field, b.text());
                    if (blank(b.url()) || !(b.url().startsWith("https://") || b.url().startsWith("http://"))) {
                        err(field, "Enter a website address starting with https://");
                    } else {
                        List<String> vars = variables(b.url());
                        if (vars.size() > 1) {
                            err(field, "A website button can have only one variable");
                        } else if (vars.size() == 1) {
                            if (!b.url().trim().endsWith("}}")) {
                                err(field, "The variable must be at the end of the website address");
                            }
                            if (b.example() == null || b.example().isNull() || (b.example().isTextual() && b.example().asText().isBlank())
                                    || (b.example().isArray() && b.example().isEmpty())) {
                                err(field, "Add a sample website address for the variable");
                            }
                        }
                    }
                }
                case "PHONE_NUMBER" -> {
                    phone++;
                    textCheck(field, b.text());
                    if (blank(b.phoneNumber()) || !PHONE.matcher(b.phoneNumber().replaceAll("[ -]", "")).matches()) {
                        err(field, "Enter the phone number with country code, e.g. +919876543210");
                    }
                }
                case "COPY_CODE" -> {
                    copy++;
                    if (!"MARKETING".equals(category)) {
                        err(field, "Copy code buttons are allowed only in marketing templates");
                    }
                    String ex = b.example() == null ? null : b.example().isArray() && !b.example().isEmpty()
                            ? b.example().get(0).asText() : b.example().asText(null);
                    if (blank(ex) || ex.length() > 15) {
                        err(field, "Add a sample offer code (max 15 characters)");
                    }
                }
                case "OTP" -> err(field, "One-time password buttons are only for authentication templates");
                default -> err(field, "Unknown button type " + b.type());
            }
        }
        if (url > 2) {
            err("buttons", "At most 2 website buttons are allowed");
        }
        if (phone > 1) {
            err("buttons", "Only one phone button is allowed");
        }
        if (copy > 1) {
            err("buttons", "Only one copy code button is allowed");
        }
    }

    private void authentication(List<ComponentDto> components) {
        boolean otp = false;
        for (ComponentDto c : components) {
            switch (c.type().toUpperCase()) {
                case "HEADER" -> err("header", "Authentication templates cannot have a header");
                case "FOOTER" -> {
                    Integer m = c.codeExpirationMinutes();
                    if (m != null && (m < 1 || m > 90)) {
                        err("footer", "Code expiry must be between 1 and 90 minutes");
                    }
                }
                case "BUTTONS" -> {
                    List<ButtonDto> buttons = c.buttons() == null ? List.of() : c.buttons();
                    if (buttons.size() != 1 || buttons.get(0).type() == null || !"OTP".equalsIgnoreCase(buttons.get(0).type())) {
                        err("buttons", "Authentication templates need exactly one one-time password button");
                    } else {
                        otp = true;
                        ButtonDto b = buttons.get(0);
                        String ot = b.otpType() == null ? "COPY_CODE" : b.otpType().toUpperCase();
                        if (!Set.of("COPY_CODE", "ONE_TAP", "ZERO_TAP").contains(ot)) {
                            err("buttons[0]", "OTP type must be copy code, one-tap or zero-tap");
                        } else if (!ot.equals("COPY_CODE") && (blank(b.packageName()) || blank(b.signatureHash()))) {
                            err("buttons[0]", "One-tap and zero-tap need your Android package name and app signature hash");
                        }
                        if (b.text() != null && b.text().length() > 25) {
                            err("buttons[0]", "Button text can be at most 25 characters");
                        }
                    }
                }
                default -> { }
            }
        }
        if (!otp) {
            err("buttons", "Add a one-time password button");
        }
    }

    private void checkVars(String field, List<String> vars, ParameterFormat format) {
        if (vars.isEmpty()) {
            return;
        }
        if (format == ParameterFormat.NAMED) {
            for (String v : vars) {
                if (!NAMED_VAR.matcher(v).matches()) {
                    err(field, "Variable {{" + v + "}} must use lowercase letters, numbers and underscores (e.g. {{customer_name}})");
                }
            }
        } else {
            for (int i = 0; i < vars.size(); i++) {
                if (!vars.get(i).equals(String.valueOf(i + 1))) {
                    err(field, "Variables must be numbered in order: {{1}}, {{2}}, {{3}} ...");
                    return;
                }
            }
        }
    }

    private void textCheck(String field, String text) {
        if (blank(text)) {
            err(field, "Button text is empty");
        } else if (text.length() > 25) {
            err(field, "Button text can be at most 25 characters");
        }
    }

    private void err(String field, String message) {
        errors.add(new FieldErrorDto(field, message));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

}
