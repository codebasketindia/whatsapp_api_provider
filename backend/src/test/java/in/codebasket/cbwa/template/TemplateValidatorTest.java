package in.codebasket.cbwa.template;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.template.dto.TemplateDtos.ButtonDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.ComponentDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateValidatorTest {

    private static ComponentDto body(String text, JsonNode example) {
        return new ComponentDto("BODY", null, text, example, null, null, null, null, null);
    }

    private static JsonNode json(String s) {
        return JsonUtil.readTree(s);
    }

    private static String firstError(Runnable r) {
        try {
            r.run();
            return null;
        } catch (AppException e) {
            return e.getErrors().get(0).field() + ": " + e.getErrors().get(0).message();
        }
    }

    @Test
    void validPositionalTemplatePasses() {
        List<ComponentDto> c = List.of(
                new ComponentDto("HEADER", "TEXT", "Order {{1}}", json("[\"#123\"]"), null, null, null, null, null),
                body("Hi {{1}}, your order {{2}} is shipped.", json("[\"Ravi\",\"#123\"]")),
                new ComponentDto("FOOTER", null, "Thanks for shopping", null, null, null, null, null, null),
                new ComponentDto("BUTTONS", null, null, null, List.of(
                        new ButtonDto("QUICK_REPLY", "Stop", null, null, null, null, null, null, null),
                        new ButtonDto("URL", "Track", "https://shop.in/t/{{1}}", null, json("\"https://shop.in/t/abc\""), null, null, null, null),
                        new ButtonDto("PHONE_NUMBER", "Call us", null, "+919876543210", null, null, null, null, null)),
                        null, null, null, null));
        assertThatCode(() -> TemplateValidator.validate("order_shipped", "en", "UTILITY", ParameterFormat.POSITIONAL, c))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBadNameSkippedVariablesAndMissingExamples() {
        assertThat(firstError(() -> TemplateValidator.validate("Order Update", "en", "UTILITY", ParameterFormat.POSITIONAL,
                List.of(body("Hello there", null))))).startsWith("name");
        assertThat(firstError(() -> TemplateValidator.validate("x", "en", "UTILITY", ParameterFormat.POSITIONAL,
                List.of(body("Hi {{1}} and {{3}} ok", json("[\"a\",\"b\"]")))))).contains("numbered in order");
        assertThat(firstError(() -> TemplateValidator.validate("x", "en", "UTILITY", ParameterFormat.POSITIONAL,
                List.of(body("Hi {{1}} welcome", null))))).contains("sample value");
        assertThat(firstError(() -> TemplateValidator.validate("x", "en", "UTILITY", ParameterFormat.POSITIONAL,
                List.of(body("{{1}} welcome back", json("[\"a\"]")))))).contains("cannot start or end");
    }

    @Test
    void namedParametersAndButtonRules() {
        List<ComponentDto> ok = List.of(body("Hi {{customer_name}}, welcome!", json("{\"customer_name\":\"Ravi\"}")));
        assertThatCode(() -> TemplateValidator.validate("welcome", "en_US", "MARKETING", ParameterFormat.NAMED, ok))
                .doesNotThrowAnyException();
        assertThat(firstError(() -> TemplateValidator.validate("w", "en", "MARKETING", ParameterFormat.NAMED,
                List.of(body("Hi {{Name}} there", json("{\"Name\":\"x\"}")))))).contains("lowercase");
        List<ComponentDto> split = List.of(body("Hello there", null), new ComponentDto("BUTTONS", null, null, null, List.of(
                new ButtonDto("QUICK_REPLY", "A", null, null, null, null, null, null, null),
                new ButtonDto("URL", "Site", "https://a.in", null, null, null, null, null, null),
                new ButtonDto("QUICK_REPLY", "B", null, null, null, null, null, null, null)), null, null, null, null));
        assertThat(firstError(() -> TemplateValidator.validate("w", "en", "MARKETING", ParameterFormat.POSITIONAL, split)))
                .contains("next to each other");
        List<ComponentDto> copy = List.of(body("Hello there", null), new ComponentDto("BUTTONS", null, null, null, List.of(
                new ButtonDto("COPY_CODE", null, null, null, json("\"SAVE10\""), null, null, null, null)), null, null, null, null));
        assertThat(firstError(() -> TemplateValidator.validate("w", "en", "UTILITY", ParameterFormat.POSITIONAL, copy)))
                .contains("only in marketing");
    }

    @Test
    void authenticationTemplateRules() {
        List<ComponentDto> ok = List.of(new ComponentDto("BODY", null, null, null, null, null, null, true, null),
                new ComponentDto("FOOTER", null, null, null, null, null, null, null, 10),
                new ComponentDto("BUTTONS", null, null, null, List.of(new ButtonDto("OTP", "Copy code", null, null, null,
                        "COPY_CODE", null, null, null)), null, null, null, null));
        assertThatCode(() -> TemplateValidator.validate("login_otp", "en", "AUTHENTICATION", ParameterFormat.POSITIONAL, ok))
                .doesNotThrowAnyException();
        List<Map<String, Object>> meta = TemplatePayloads.toMeta(ok.stream()
                .map(c -> TemplatePayloads.normalise(c, ParameterFormat.POSITIONAL)).toList(), "AUTHENTICATION");
        assertThat(meta.get(0)).containsEntry("add_security_recommendation", true);
        assertThat(meta.get(1)).containsEntry("code_expiration_minutes", 10);
        List<ComponentDto> oneTapMissing = List.of(new ComponentDto("BODY", null, null, null, null, null, null, true, null),
                new ComponentDto("BUTTONS", null, null, null, List.of(new ButtonDto("OTP", null, null, null, null,
                        "ONE_TAP", null, null, null)), null, null, null, null));
        assertThatThrownBy(() -> TemplateValidator.validate("o", "en", "AUTHENTICATION", ParameterFormat.POSITIONAL, oneTapMissing))
                .isInstanceOf(AppException.class);
    }

    @Test
    void payloadUsesMetaExampleFormat() {
        ComponentDto c = TemplatePayloads.normalise(body("Hi {{1}}, order {{2}} ready", json("[\"Ravi\",\"#9\"]")), ParameterFormat.POSITIONAL);
        assertThat(c.example().toString()).isEqualTo("{\"body_text\":[[\"Ravi\",\"#9\"]]}");
        ComponentDto n = TemplatePayloads.normalise(body("Hi {{name}} there", json("{\"name\":\"Ravi\"}")), ParameterFormat.NAMED);
        assertThat(n.example().toString()).isEqualTo("{\"body_text_named_params\":[{\"param_name\":\"name\",\"example\":\"Ravi\"}]}");
        List<ComponentDto> back = TemplatePayloads.fromMeta(json("[{\"type\":\"BODY\",\"text\":\"Hi {{1}}\",\"example\":{\"body_text\":[[\"R\"]]}},"
                + "{\"type\":\"BUTTONS\",\"buttons\":[{\"type\":\"URL\",\"text\":\"Go\",\"url\":\"https://a.in/{{1}}\",\"example\":[\"https://a.in/x\"]}]}]"));
        assertThat(back).hasSize(2);
        assertThat(back.get(1).buttons().get(0).url()).isEqualTo("https://a.in/{{1}}");
    }
}
