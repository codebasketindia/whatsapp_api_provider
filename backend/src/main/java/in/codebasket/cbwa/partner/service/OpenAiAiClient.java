package in.codebasket.cbwa.partner.service;

import in.codebasket.cbwa.common.service.ai.AiModelClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI (ChatGPT models) through the Chat Completions REST API with a strict JSON schema answer.
 * The shared system prompt is sent first so OpenAI's automatic prompt caching can reuse it; the
 * per-customer facts follow in a second system message.
 */
@Slf4j
@Component
public class OpenAiAiClient implements AiModelClient.Provider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ATTEMPTS = 3;

    private final String baseUrl;
    private final HttpClient http;

    public OpenAiAiClient(@Value("${cbwa.ai.openai-base-url:https://api.openai.com/v1}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).proxy(ProxySelector.getDefault()).build();
    }

    @Override
    public String name() {
        return "OPENAI";
    }

    @Override
    public AiModelClient.AiResult complete(AiModelClient.AiRequest req) {
        String body = body(req).toString();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + req.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                res = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new AiModelClient.AiModelException("OpenAI not reachable: " + e.getMessage(), e);
                }
                sleep(attempt);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiModelClient.AiModelException("Interrupted", e);
            }
            int code = res.statusCode();
            if ((code == 429 || code >= 500) && attempt < MAX_ATTEMPTS) {
                sleep(attempt);
                continue;
            }
            break;
        }
        if (res.statusCode() / 100 != 2) {
            throw new AiModelClient.AiModelException("OpenAI error " + res.statusCode() + ": " + errorMessage(res.body()), null);
        }
        JsonNode n;
        try {
            n = JSON.readTree(res.body());
        } catch (IOException e) {
            throw new AiModelClient.AiModelException("OpenAI returned an unreadable answer", e);
        }
        JsonNode usage = n.path("usage");
        int cached = usage.path("prompt_tokens_details").path("cached_tokens").asInt(0);
        int in = Math.max(0, usage.path("prompt_tokens").asInt(0) - cached);
        int out = usage.path("completion_tokens").asInt(0);
        JsonNode msg = n.path("choices").path(0).path("message");
        if (msg.hasNonNull("refusal")) {
            return new AiModelClient.AiResult("", java.util.List.of(), true, "Model refused", in, out, cached, 0);
        }
        return AiModelClient.parse(JSON, msg.path("content").asText(""), in, out, cached, 0);
    }

    ObjectNode body(AiModelClient.AiRequest req) {
        ObjectNode b = JSON.createObjectNode();
        b.put("model", req.model());
        // Reasoning models count thinking inside this limit, so leave them room beyond the visible reply.
        boolean reasoning = req.reasoningEffort() != null && !req.reasoningEffort().isBlank();
        b.put("max_completion_tokens", reasoning ? Math.max(req.maxTokens() * 4, 2000) : req.maxTokens());
        if (reasoning) {
            b.put("reasoning_effort", req.reasoningEffort());
        }
        ArrayNode messages = b.putArray("messages");
        messages.addObject().put("role", "system").put("content", req.system());
        if (req.context() != null && !req.context().isBlank()) {
            messages.addObject().put("role", "system").put("content", req.context());
        }
        for (AiModelClient.Turn t : AiModelClient.alternate(req.history(), req.message())) {
            messages.addObject().put("role", t.customer() ? "user" : "assistant").put("content", t.text());
        }
        ObjectNode format = b.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode js = format.putObject("json_schema");
        js.put("name", "whatsapp_reply");
        js.put("strict", true);
        ObjectNode schema = js.putObject("schema");
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        props.putObject("reply").put("type", "string");
        props.putObject("attachments").put("type", "array").putObject("items").put("type", "string");
        props.putObject("handover").put("type", "boolean");
        props.putObject("reason").put("type", "string");
        schema.putArray("required").add("reply").add("attachments").add("handover").add("reason");
        return b;
    }

    private static String errorMessage(String body) {
        try {
            String m = JSON.readTree(body).path("error").path("message").asText(null);
            return m == null ? "" : m.length() > 200 ? m.substring(0, 200) : m;
        } catch (IOException e) {
            return "";
        }
    }

    private static void sleep(int attempt) {
        try {
            Thread.sleep(500L * attempt * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
