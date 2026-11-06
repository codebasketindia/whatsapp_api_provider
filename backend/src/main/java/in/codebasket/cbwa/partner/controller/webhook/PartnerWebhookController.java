package in.codebasket.cbwa.partner.controller.webhook;

import in.codebasket.cbwa.partner.service.webhook.PartnerWebhookService;

import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.common.dto.PageResponse;
import in.codebasket.cbwa.partner.dto.PartnerWebhookDtos.DeliveryLogDto;
import in.codebasket.cbwa.partner.dto.PartnerWebhookDtos.SecretDto;
import in.codebasket.cbwa.partner.dto.PartnerWebhookDtos.TestRequest;
import in.codebasket.cbwa.partner.dto.PartnerWebhookDtos.TestResultDto;
import in.codebasket.cbwa.partner.dto.PartnerWebhookDtos.WebhookConfigDto;
import in.codebasket.cbwa.partner.dto.PartnerWebhookDtos.WebhookConfigRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Partner - Webhook")
@RestController
@RequestMapping("/api/partner/webhook")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTNER')")
public class PartnerWebhookController {

    private final PartnerWebhookService service;

    @GetMapping
    public ApiResponse<WebhookConfigDto> get() {
        return ApiResponse.ok(service.get(SecurityUtils.currentPartnerId()));
    }

    @PutMapping
    public ApiResponse<WebhookConfigDto> save(@Valid @RequestBody WebhookConfigRequest req) {
        WebhookConfigDto dto = service.save(SecurityUtils.currentPartnerId(), req);
        return ApiResponse.ok(dto, dto.secret() != null
                ? "Webhook saved. Copy the signing secret now - it will not be shown again" : "Webhook saved");
    }

    @PostMapping("/rotate-secret")
    public ApiResponse<SecretDto> rotate() {
        return ApiResponse.ok(service.rotateSecret(SecurityUtils.currentPartnerId()),
                "New signing secret created. Copy it now - it will not be shown again");
    }

    @PostMapping("/test")
    public ApiResponse<TestResultDto> test(@RequestBody(required = false) TestRequest req) {
        TestResultDto r = service.test(SecurityUtils.currentPartnerId(), req == null ? null : req.eventType());
        return ApiResponse.ok(r, "SUCCESS".equals(r.status()) ? "Test event delivered" : "Test event could not be delivered");
    }

    @GetMapping("/logs")
    public ApiResponse<PageResponse<DeliveryLogDto>> logs(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String eventType,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.logs(SecurityUtils.currentPartnerId(), status, eventType, from, to, page, size));
    }

    @PostMapping("/logs/{id}/retry")
    public ApiResponse<DeliveryLogDto> retry(@PathVariable Long id) {
        return ApiResponse.ok(service.retry(SecurityUtils.currentPartnerId(), id), "Delivery will be retried shortly");
    }
}
