package in.codebasket.cbwa.webhook;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.meta.MetaSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** WH1 / WH2: Meta webhook verification and event intake (one URL for all tenants). */
@Slf4j
@Tag(name = "Webhooks - Meta")
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
public class MetaWebhookController {

    private final MetaSettingsService metaSettings;
    private final WebhookInboundProcessor processor;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String expected = metaSettings.verifyTokenOrNull();
        if ("subscribe".equals(mode) && expected != null && HashUtil.constantTimeEquals(expected, token) && challenge != null) {
            return ResponseEntity.ok(challenge);
        }
        log.warn("Meta webhook verification failed (mode={})", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
    }

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<ApiResponse<Void>> receive(@RequestBody(required = false) byte[] raw,
                                                     @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        if (raw == null || raw.length == 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.BAD_REQUEST, "Empty body", null, null));
        }
        if (!validSignature(raw, signature)) {
            log.warn("Meta webhook rejected: invalid X-Hub-Signature-256");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ErrorCode.WEBHOOK_SIGNATURE_INVALID, null, null, null));
        }
        Long id;
        try {
            id = processor.record(raw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.BAD_REQUEST, "Invalid JSON", null, null));
        }
        if (id != null && processor.isAsyncEnabled()) {
            processor.processAsync(id);
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private boolean validSignature(byte[] raw, String header) {
        String secret = metaSettings.appSecretOrNull();
        if (secret == null || secret.isBlank() || header == null || !header.startsWith("sha256=")) {
            return false;
        }
        String expected = "sha256=" + HashUtil.hmacSha256Hex(secret.getBytes(StandardCharsets.UTF_8), raw);
        return HashUtil.constantTimeEquals(expected, header.trim().toLowerCase());
    }
}
