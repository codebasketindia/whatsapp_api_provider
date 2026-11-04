package in.codebasket.cbwa.registration;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.payment.PaymentLogService;
import in.codebasket.cbwa.webhook.InboundLogStatus;
import in.codebasket.cbwa.webhook.WebhookInboundLog;
import in.codebasket.cbwa.webhook.WebhookInboundLogRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * PH1: webhook from a partner's own Razorpay account (set in their Razorpay dashboard). Confirms
 * registration fees even when the customer closes the browser before returning. Signature = hex
 * HMAC_SHA256(partner webhook secret, raw body); each event is stored once.
 */
@Slf4j
@Tag(name = "Webhooks")
@RestController
@RequiredArgsConstructor
public class PartnerRazorpayWebhookController {

    private final PartnerRepository partnerRepository;
    private final PartnerPaymentService payments;
    private final RegistrationService registrations;
    private final WebhookInboundLogRepository logRepository;

    @PostMapping("/api/webhooks/razorpay/partners/{partnerUid}")
    public ResponseEntity<ApiResponse<Map<String, String>>> receive(@PathVariable String partnerUid, @RequestBody byte[] body,
                                                                     @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                                                     @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        Long partnerId = partnerRepository.findByUid(partnerUid).map(p -> p.getId()).orElse(null);
        if (partnerId == null || !payments.verifyWebhookSignature(partnerId, body, signature)) {
            log.warn("Partner Razorpay webhook with invalid signature rejected");
            return ResponseEntity.status(400).body(ApiResponse.error(ErrorCode.WEBHOOK_SIGNATURE_INVALID, null, null, null));
        }
        JsonNode event;
        try {
            event = JsonUtil.MAPPER.readTree(body);
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("result", "IGNORED")));
        }
        String id = "rzpp:" + partnerId + ":" + (eventId != null && !eventId.isBlank() ? eventId.trim() : HashUtil.sha256Hex(body));
        WebhookInboundLog entry = new WebhookInboundLog();
        entry.setSource("RAZORPAY_PARTNER");
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
            return ResponseEntity.ok(ApiResponse.ok(Map.of("result", "DUPLICATE")));
        }
        String result;
        try {
            result = registrations.handlePartnerEvent(partnerId, event);
            entry.setStatus("IGNORED".equals(result) ? InboundLogStatus.IGNORED : InboundLogStatus.PROCESSED);
        } catch (RuntimeException e) {
            log.error("Partner Razorpay webhook processing failed for {}", id, e);
            result = "FAILED";
            entry.setStatus(InboundLogStatus.FAILED);
            String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            entry.setError(m.substring(0, Math.min(1000, m.length())));
        }
        entry.setAttempts(entry.getAttempts() + 1);
        entry.setProcessedAt(Instant.now());
        logRepository.save(entry);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("result", result)));
    }
}
