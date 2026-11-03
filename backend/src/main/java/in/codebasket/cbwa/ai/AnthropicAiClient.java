package in.codebasket.cbwa.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Claude via the official Anthropic Java SDK. Kept cheap on purpose: low effort, a short output
 * limit, the system prompt (rules + knowledge) marked for prompt caching and structured JSON output.
 */
@Slf4j
@Component
public class AnthropicAiClient implements AiModelClient.Provider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonOutputFormat.Schema SCHEMA = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "reply", Map.of("type", "string"),
                    "attachments", Map.of("type", "array", "items", Map.of("type", "string")),
                    "handover", Map.of("type", "boolean"),
                    "reason", Map.of("type", "string"))))
            .putAdditionalProperty("required", JsonValue.from(List.of("reply", "attachments", "handover", "reason")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build();

    private final String baseUrl;
    private volatile String clientKey;
    private volatile AnthropicClient client;

    public AnthropicAiClient(@Value("${cbwa.ai.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() {
        return "ANTHROPIC";
    }

    @Override
    public AiModelClient.AiResult complete(AiModelClient.AiRequest req) {
        List<TextBlockParam> system = new ArrayList<>();
        system.add(TextBlockParam.builder().text(req.system()).cacheControl(CacheControlEphemeral.builder().build()).build());
        if (req.context() != null && !req.context().isBlank()) {
            system.add(TextBlockParam.builder().text(req.context()).build()); // per customer: after the cache breakpoint
        }
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(req.model())
                .maxTokens(req.maxTokens())
                .systemOfTextBlockParams(system)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(SCHEMA).build())
                        .build());
        for (AiModelClient.Turn t : AiModelClient.alternate(req.history(), req.message())) {
            if (t.customer()) {
                b.addUserMessage(t.text());
            } else {
                b.addAssistantMessage(t.text());
            }
        }
        Message res;
        try {
            res = client(req.apiKey()).messages().create(b.build());
        } catch (RuntimeException e) {
            throw new AiModelClient.AiModelException("AI service error: " + e.getMessage(), e);
        }
        String text = res.content().stream().flatMap(c -> c.text().stream()).map(t -> t.text()).collect(Collectors.joining());
        var u = res.usage();
        int in = (int) u.inputTokens();
        int out = (int) u.outputTokens();
        int cr = u.cacheReadInputTokens().map(Long::intValue).orElse(0);
        int cw = u.cacheCreationInputTokens().map(Long::intValue).orElse(0);
        AiModelClient.AiResult r = AiModelClient.parse(JSON, text, in, out, cr, cw);
        if (r.handover() && "Answer could not be read".equals(r.reason())) {
            log.info("AI answer was not valid JSON (stop reason {})", res.stopReason().map(Object::toString).orElse("?"));
        }
        return r;
    }

    private AnthropicClient client(String apiKey) {
        AnthropicClient c = client;
        if (c == null || !apiKey.equals(clientKey)) {
            synchronized (this) {
                if (client == null || !apiKey.equals(clientKey)) {
                    AnthropicOkHttpClient.Builder cb = AnthropicOkHttpClient.builder().apiKey(apiKey)
                            .timeout(Duration.ofSeconds(30)).maxRetries(2);
                    if (baseUrl != null && !baseUrl.isBlank()) {
                        cb.baseUrl(baseUrl);
                    }
                    client = cb.build();
                    clientKey = apiKey;
                }
                c = client;
            }
        }
        return c;
    }
}
