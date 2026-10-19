package in.codebasket.cbwa.payment;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Razorpay Orders + Payments API. Keys come from admin settings (secret stored encrypted).
 *  - checkout signature: hex HMAC_SHA256(order_id + "|" + payment_id, key_secret)
 *  - webhook signature : hex HMAC_SHA256(raw body, webhook_secret) in X-Razorpay-Signature
 */
@Slf4j
@Component
public class RazorpayGateway implements PaymentGateway {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final SettingsService settings;
    private final AppProperties props;

    public RazorpayGateway(WebClient.Builder builder, SettingsService settings, AppProperties props) {
        this.webClient = builder.clone().build();
        this.settings = settings;
        this.props = props;
    }

    @Override
    public GatewayType type() {
        return GatewayType.RAZORPAY;
    }

    @Override
    public boolean isConfigured() {
        return notBlank(keyId()) && notBlank(keySecret());
    }

    @Override
    public String publicKeyId() {
        return keyId();
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode res = call("POST", "/v1/orders", body);
        return new GatewayOrder(res.path("id").asText(), res.path("status").asText(), res);
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        return toPayment(call("GET", "/v1/payments/" + paymentId, null));
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String gatewayOrderId) {
        JsonNode res = call("GET", "/v1/orders/" + gatewayOrderId + "/payments", null);
        List<GatewayPayment> out = new ArrayList<>();
        res.path("items").forEach(i -> out.add(toPayment(i)));
        return out;
    }

    @Override
    public boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        String secret = keySecret();
        if (!notBlank(secret) || signature == null) {
            return false;
        }
        String expected = HashUtil.hmacSha256Hex(secret, gatewayOrderId + "|" + gatewayPaymentId);
        return HashUtil.constantTimeEquals(expected, signature.trim().toLowerCase());
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        String secret = settings.getString(SettingKeys.PAYMENT, "razorpay_webhook_secret");
        if (!notBlank(secret) || signature == null || rawBody == null) {
            return false;
        }
        String expected = HashUtil.hmacSha256Hex(secret.getBytes(StandardCharsets.UTF_8), rawBody);
        return HashUtil.constantTimeEquals(expected, signature.trim().toLowerCase());
    }

    @Override
    public void testConnection() {
        if (!isConfigured()) {
            throw new GatewayException(0, "Razorpay Key ID and Key Secret are not set");
        }
        call("GET", "/v1/orders?count=1", null);
    }

    public static GatewayPayment toPayment(JsonNode p) {
        String status = p.path("status").asText("");
        PaymentState state = switch (status) {
            case "captured" -> PaymentState.CAPTURED;
            case "authorized" -> PaymentState.AUTHORIZED;
            case "failed" -> PaymentState.FAILED;
            case "refunded" -> PaymentState.REFUNDED;
            default -> PaymentState.PENDING;
        };
        long created = p.path("created_at").asLong(0);
        return new GatewayPayment(p.path("id").asText(null), p.path("order_id").asText(null), p.path("amount").asLong(0),
                p.path("currency").asText("INR"), state, p.path("method").asText(null),
                p.hasNonNull("error_description") ? p.path("error_description").asText() : null,
                created > 0 ? Instant.ofEpochSecond(created) : null, p);
    }

    private JsonNode call(String method, String path, Object body) {
        if (!isConfigured()) {
            throw new GatewayException(0, "Razorpay keys are not configured");
        }
        String auth = Base64.getEncoder().encodeToString((keyId() + ":" + keySecret()).getBytes(StandardCharsets.UTF_8));
        String url = props.getRazorpay().getBaseUrl() + path;
        try {
            var spec = webClient.method(org.springframework.http.HttpMethod.valueOf(method)).uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + auth);
            String res = (body == null ? spec : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(JsonUtil.toJson(body)))
                    .retrieve().bodyToMono(String.class).block(TIMEOUT);
            return JsonUtil.readTree(res);
        } catch (WebClientResponseException e) {
            String desc;
            try {
                desc = JsonUtil.readTree(e.getResponseBodyAsString()).path("error").path("description").asText(e.getStatusText());
            } catch (RuntimeException ex) {
                desc = e.getStatusText();
            }
            log.warn("Razorpay {} {} failed: {} {}", method, path.replaceAll("\\?.*", ""), e.getStatusCode().value(), desc);
            throw new GatewayException(e.getStatusCode().value(), desc);
        } catch (WebClientRequestException e) {
            log.warn("Razorpay network error: {}", e.getMessage());
            throw new GatewayException(0, "Could not reach Razorpay");
        } catch (IllegalStateException e) {
            throw new GatewayException(0, "Razorpay did not respond in time");
        }
    }

    private String keyId() {
        return settings.getString(SettingKeys.PAYMENT, "razorpay_key_id");
    }

    private String keySecret() {
        return settings.getString(SettingKeys.PAYMENT, "razorpay_key_secret");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
