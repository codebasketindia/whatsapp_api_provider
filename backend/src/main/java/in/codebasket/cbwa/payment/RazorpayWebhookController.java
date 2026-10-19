package in.codebasket.cbwa.payment;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.webhook.InboundLogStatus;
import in.codebasket.cbwa.webhook.WebhookInboundLog;
import in.codebasket.cbwa.webhook.WebhookInboundLogRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * PG15: Razorpay webhook. Signature = hex HMAC_SHA256(webhook secret, raw body). Each event is stored
 * once (X-Razorpay-Event-Id is UNIQUE in webhook_inbound_logs) so repeated deliveries are ignored, and
 * settlement itself is idempotent per payment. Always answers 200 quickly unless the signature is bad.
 */
@Slf4j
@Tag(name = "Webhooks")
@RestController
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayGateway gateway;
    private final PaymentService paymentService;
    private final WebhookInboundLogRepository logRepository;

    @PostMapping("/api/webhooks/payments/razorpay")
    public ResponseEntity<ApiResponse<Map<String, String>>> receive(@RequestBody byte[] body,
                                                                     @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                                                     @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        if (!gateway.verifyWebhookSignature(body, signature)) {
            log.warn("Razorpay webhook with invalid signature rejected");
            return ResponseEntity.status(400).body(ApiResponse.error(ErrorCode.WEBHOOK_SIGNATURE_INVALID, null, null, null));
        }
        JsonNode event;
        try {
            event = JsonUtil.MAPPER.readTree(body);
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("result", "IGNORED")));
        }
        String id = "rzp:" + (eventId != null && !eventId.isBlank() ? eventId.trim() : HashUtil.sha256Hex(body));
        WebhookInboundLog entry = new WebhookInboundLog();
        entry.setSource("RAZORPAY");
        entry.setEventId(id.length() > 80 ? id.substring(0, 80) : id);
        entry.setSignatureValid(true);
        entry.setObjectType(event.path("entity").asText("event"));
        entry.setFields(event.path("event").asText(null));
        entry.setPayload(PaymentLogService.sanitize(event).toString());
        entry.setStatus(InboundLogStatus.RECEIVED);
        entry.setReceivedAt(Instant.now());
        try {
            logRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException dup) {
            log.info("Duplicate Razorpay event {} ignored", id);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("result", "DUPLICATE")));
        }
        String result;
        try {
            result = paymentService.handleRazorpayEvent(event);
            entry.setStatus("IGNORED".equals(result) ? InboundLogStatus.IGNORED : InboundLogStatus.PROCESSED);
        } catch (RuntimeException e) {
            log.error("Razorpay webhook processing failed for {}", id, e);
            result = "FAILED";
            entry.setStatus(InboundLogStatus.FAILED);
            entry.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().substring(0, Math.min(1000, e.getMessage().length())));
        }
        entry.setAttempts(entry.getAttempts() + 1);
        entry.setProcessedAt(Instant.now());
        logRepository.save(entry);
        // Failed processing is retried by the reconciliation job (order stays CREATED/ATTEMPTED).
        return ResponseEntity.ok(ApiResponse.ok(Map.of("result", result)));
    }
}
