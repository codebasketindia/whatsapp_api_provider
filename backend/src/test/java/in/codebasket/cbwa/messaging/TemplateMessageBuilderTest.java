package in.codebasket.cbwa.messaging;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateMessageBuilderTest {

    @Test
    void positionalWithImageHeaderAndUrlButton() {
        Template t = template(ParameterFormat.POSITIONAL, MessageCategory.MARKETING);
        List<TemplateComponent> comps = List.of(
                comp(ComponentType.HEADER, "IMAGE", null, null),
                comp(ComponentType.BODY, null, "Hi {{1}}, use {{2}}", null),
                comp(ComponentType.BUTTONS, null, null, "[{\"type\":\"URL\",\"text\":\"Shop\",\"url\":\"https://x.in/{{1}}\"},{\"type\":\"QUICK_REPLY\",\"text\":\"Stop\"}]"));
        TemplateRef ref = new TemplateRef(1L, null, null, new TemplateVariables(List.of("Ravi", "SAVE10"),
                new HeaderVariable("image", null, null, null, "https://cdn.x/img.png", null, null, null, null, null),
                List.of(new ButtonVariable(0, "url", "abc"))), null);
        TemplateMessageBuilder.Result r = TemplateMessageBuilder.build(t, comps, ref,
                hv -> (ObjectNode) JsonUtil.MAPPER.createObjectNode().put("link", hv.link()));
        String json = r.template().toString();
        assertThat(r.preview()).isEqualTo("Hi Ravi, use SAVE10");
        assertThat(json).contains("\"type\":\"header\"").contains("https://cdn.x/img.png")
                .contains("\"sub_type\":\"url\"").contains("\"index\":\"0\"").contains("\"text\":\"abc\"");
        assertThat(json).doesNotContain("quick_reply");
    }

    @Test
    void namedParametersUseParameterName() {
        Template t = template(ParameterFormat.NAMED, MessageCategory.UTILITY);
        List<TemplateComponent> comps = List.of(comp(ComponentType.BODY, null, "Order {{order_id}} for {{name}}", null));
        TemplateRef ref = new TemplateRef(1L, null, null, null, Map.of("order_id", "77", "name", "Asha"));
        String json = TemplateMessageBuilder.build(t, comps, ref, hv -> null).template().toString();
        assertThat(json).contains("\"parameter_name\":\"order_id\"").contains("\"parameter_name\":\"name\"");
        assertThatThrownBy(() -> TemplateMessageBuilder.build(t, comps, new TemplateRef(1L, null, null, null, Map.of("name", "A")), hv -> null))
                .isInstanceOf(AppException.class);
    }

    @Test
    void authenticationTemplateAddsCodeToBodyAndOtpButton() {
        Template t = template(ParameterFormat.POSITIONAL, MessageCategory.AUTHENTICATION);
        List<TemplateComponent> comps = List.of(comp(ComponentType.BODY, null, null, null),
                comp(ComponentType.BUTTONS, null, null, "[{\"type\":\"OTP\",\"otp_type\":\"COPY_CODE\"}]"));
        TemplateRef ref = new TemplateRef(1L, null, null, new TemplateVariables(List.of("482913"), null, null), null);
        TemplateMessageBuilder.Result r = TemplateMessageBuilder.build(t, comps, ref, hv -> null);
        assertThat(r.template().toString()).contains("\"sub_type\":\"url\"").contains("482913");
        assertThat(r.preview()).contains("482913");
    }

    private static Template template(ParameterFormat f, MessageCategory c) {
        Template t = new Template();
        t.setName("t");
        t.setLanguage("en");
        t.setParameterFormat(f);
        t.setCategory(c);
        return t;
    }

    private static TemplateComponent comp(ComponentType type, String format, String text, String buttons) {
        TemplateComponent c = new TemplateComponent();
        c.setType(type);
        c.setFormat(format);
        c.setText(text);
        c.setButtons(buttons);
        return c;
    }
}
