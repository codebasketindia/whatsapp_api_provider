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
 * Google Gemini models through the Generative Language REST API.
 */
@Slf4j
@Component
public class GeminiAiClient implements AiModelClient.Provider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ATTEMPTS = 3;

    private final String baseUrl;
    private final HttpClient http;

    public GeminiAiClient(@Value("${cbwa.ai.gemini-base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).proxy(ProxySelector.getDefault()).build();
    }

    @Override
    public String name() {
        return "GEMINI";
    }

    @Override
    public AiModelClient.AiResult complete(AiModelClient.AiRequest req) {
        String model = req.model() != null ? req.model() : "gemini-1.5-flash";
        String body = body(req).toString();
        String url = baseUrl + "/models/" + model + ":generateContent?key=" + req.apiKey();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                res = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new AiModelClient.AiModelException("Gemini not reachable: " + e.getMessage(), e);
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
            throw new AiModelClient.AiModelException("Gemini error " + res.statusCode() + ": " + errorMessage(res.body()), null);
        }
        JsonNode n;
        try {
            n = JSON.readTree(res.body());
        } catch (IOException e) {
            throw new AiModelClient.AiModelException("Gemini returned an unreadable answer", e);
        }

        // Parse Gemini response
        JsonNode usage = n.path("usageMetadata");
        int in = usage.path("promptTokenCount").asInt(0);
        int out = usage.path("candidatesTokenCount").asInt(0);
        int cached = usage.path("cachedContentTokenCount").asInt(0);

        JsonNode candidate = n.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asText("");

        if ("SAFETY".equals(finishReason) || "BLOCKED".equals(finishReason)) {
            return new AiModelClient.AiResult("", java.util.List.of(), true, "Model refused due to safety", in, out, cached, 0);
        }

        String content = candidate.path("content").path("parts").path(0).path("text").asText("");

        // Try to extract JSON from the response
        String jsonContent = extractJson(content);

        return AiModelClient.parse(JSON, jsonContent, in, out, cached, 0);
    }

    ObjectNode body(AiModelClient.AiRequest req) {
        ObjectNode b = JSON.createObjectNode();

        // System instruction
        if (req.system() != null && !req.system().isBlank()) {
            ObjectNode systemInstruction = b.putObject("systemInstruction");
            ArrayNode parts = systemInstruction.putArray("parts");
            parts.addObject().put("text", req.system());
            if (req.context() != null && !req.context().isBlank()) {
                parts.addObject().put("text", req.context());
            }
        }

        // Contents (conversation history + current message)
        ArrayNode contents = b.putArray("contents");
        for (AiModelClient.Turn t : AiModelClient.alternate(req.history(), req.message())) {
            ObjectNode msg = contents.addObject();
            msg.put("role", t.customer() ? "user" : "model");
            msg.putArray("parts").addObject().put("text", t.text());
        }

        // Generation config
        ObjectNode config = b.putObject("generationConfig");
        config.put("maxOutputTokens", req.maxTokens());
        config.put("responseMimeType", "application/json");

        // Response schema for structured output
        ObjectNode schema = config.putObject("responseSchema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("reply").put("type", "string");
        ObjectNode attachments = props.putObject("attachments");
        attachments.put("type", "array");
        attachments.putObject("items").put("type", "string");
        props.putObject("handover").put("type", "boolean");
        props.putObject("reason").put("type", "string");
        schema.putArray("required").add("reply").add("attachments").add("handover").add("reason");

        return b;
    }

    private static String extractJson(String content) {
        // If content is already valid JSON, return it
        content = content.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            return content;
        }
        // Try to find JSON in markdown code blocks
        int start = content.indexOf("```json");
        if (start >= 0) {
            start = content.indexOf("\n", start) + 1;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        // Try to find JSON without code block markers
        start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
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
