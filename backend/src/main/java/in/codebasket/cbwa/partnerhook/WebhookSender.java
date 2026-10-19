package in.codebasket.cbwa.partnerhook;

import in.codebasket.cbwa.common.crypto.HashUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Performs one signed HTTP delivery to a partner webhook (contract §4.17):
 * X-CB-Signature = "sha256=" + hex(HMAC_SHA256(secret, timestamp + "." + body)).
 */
@Slf4j
@Component
public class WebhookSender {

    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BODY = 2000;

    private final WebClient webClient;

    public WebhookSender(WebClient.Builder builder) {
        this.webClient = builder.clone().build();
    }

    public record Result(boolean success, Integer responseCode, String responseBody, int durationMs) {
    }

    public static String sign(String secret, long timestamp, String body) {
        return "sha256=" + HashUtil.hmacSha256Hex(secret, timestamp + "." + body);
    }

    public Result send(String url, String secret, String eventType, String eventId, String body) {
        long ts = Instant.now().getEpochSecond();
        long start = System.nanoTime();
        try {
            var response = webClient.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, "CB-WhatsApp-Gateway-Webhook/1.0")
                    .header("X-CB-Event", eventType)
                    .header("X-CB-Event-Id", eventId)
                    .header("X-CB-Timestamp", String.valueOf(ts))
                    .header("X-CB-Signature", sign(secret, ts, body))
                    .bodyValue(body)
                    .exchangeToMono(r -> r.bodyToMono(String.class).defaultIfEmpty("")
                            .map(b -> new Object[]{r.statusCode().value(), b}))
                    .block(TIMEOUT);
            int ms = elapsed(start);
            if (response == null) {
                return new Result(false, null, "No response", ms);
            }
            int code = (Integer) response[0];
            return new Result(code >= 200 && code < 300, code, truncate((String) response[1]), ms);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (e instanceof IllegalStateException && msg.contains("Timeout")) {
                msg = "Timed out after " + TIMEOUT.toSeconds() + " seconds";
            }
            log.debug("Partner webhook delivery to {} failed: {}", url, msg);
            return new Result(false, null, truncate(msg), elapsed(start));
        }
    }

    private static int elapsed(long start) {
        return (int) ((System.nanoTime() - start) / 1_000_000);
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() > MAX_BODY ? s.substring(0, MAX_BODY) : s;
    }
}
