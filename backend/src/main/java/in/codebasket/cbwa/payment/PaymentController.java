package in.codebasket.cbwa.payment;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.payment.PaymentGateway.GatewayException;
import in.codebasket.cbwa.payment.dto.PaymentDtos.CheckoutOrderDto;
import in.codebasket.cbwa.payment.dto.PaymentDtos.CreateOrderRequest;
import in.codebasket.cbwa.payment.dto.PaymentDtos.FailedRequest;
import in.codebasket.cbwa.payment.dto.PaymentDtos.PaymentConfigDto;
import in.codebasket.cbwa.payment.dto.PaymentDtos.PaymentOrderDto;
import in.codebasket.cbwa.payment.dto.PaymentDtos.ReconcileSummary;
import in.codebasket.cbwa.payment.dto.PaymentDtos.VerifyRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** PG1-PG6 (partner), PG9-PG12 (admin), ST5 (gateway test). */
@Tag(name = "Payments")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;
    private final PaymentGatewayRegistry registry;
    private final AuditService auditService;
    private final in.codebasket.cbwa.common.settings.SettingsService settings;
    private final java.util.List<PaymentGateway> gateways;

    // ---------------- partner

    @GetMapping("/api/partner/payments/config")
    public ApiResponse<PaymentConfigDto> config() {
        return ApiResponse.ok(service.config());
    }

    @PostMapping("/api/partner/payments/orders")
    public ApiResponse<CheckoutOrderDto> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        return ApiResponse.ok(service.createOrder(SecurityUtils.currentPartnerId(), req.creditPackId(), req.amount()), "Order created");
    }

    @PostMapping("/api/partner/payments/verify")
    public ApiResponse<PaymentOrderDto> verify(@Valid @RequestBody VerifyRequest req) {
        PaymentOrderDto dto = service.verify(SecurityUtils.currentPartnerId(), req.gatewayOrderId(), req.gatewayPaymentId(), req.signature());
        return ApiResponse.ok(dto, "PAID".equals(dto.status()) ? "Payment successful. Wallet credited"
                : "Payment received. Your wallet will be credited as soon as the bank confirms");
    }

    @PostMapping("/api/partner/payments/orders/{uid}/failed")
    public ApiResponse<PaymentOrderDto> failed(@PathVariable String uid, @Valid @RequestBody FailedRequest req) {
        return ApiResponse.ok(service.failed(SecurityUtils.currentPartnerId(), uid, req.code(), req.description(), req.reason()));
    }

    @GetMapping("/api/partner/payments")
    public ApiResponse<PageResponse<PaymentOrderDto>> mine(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.listForPartner(SecurityUtils.currentPartnerId(), status, from, to, page, size));
    }

    @GetMapping("/api/partner/payments/{uid}")
    public ApiResponse<PaymentOrderDto> mineOne(@PathVariable String uid) {
        return ApiResponse.ok(service.get(SecurityUtils.currentPartnerId(), uid));
    }

    // ---------------- admin

    @GetMapping("/api/admin/payments")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public ApiResponse<PageResponse<PaymentOrderDto>> list(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Long partnerId,
                                                           @RequestParam(required = false) String gateway,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                           @RequestParam(required = false) String search,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.search(partnerId, status, gateway, from, to, search, page, size, true));
    }

    @GetMapping("/api/admin/payments/{uid}")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public ApiResponse<PaymentOrderDto> detail(@PathVariable String uid) {
        return ApiResponse.ok(service.adminDetail(uid));
    }

    @PostMapping("/api/admin/payments/{uid}/reconcile")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_RECONCILE')")
    public ApiResponse<PaymentOrderDto> reconcile(@PathVariable String uid) {
        PaymentOrderDto dto = service.reconcile(uid);
        auditService.log("PAYMENT_RECONCILE", "PAYMENT_ORDER", uid, null, Map.of("status", dto.status()));
        return ApiResponse.ok(dto, "Order checked with the payment gateway");
    }

    @PostMapping("/api/admin/payments/reconcile-pending")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_RECONCILE')")
    public ApiResponse<ReconcileSummary> reconcileAll() {
        ReconcileSummary s = service.reconcilePending();
        auditService.log("PAYMENT_RECONCILE_ALL", "PAYMENT_ORDER", null, null, s);
        return ApiResponse.ok(s, "Checked " + s.checked() + " pending orders");
    }

    /** ST5: checks the keys of the selected (or Razorpay) gateway without changing anything. */
    @PostMapping("/api/admin/settings/payment-gateway/test")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> test() {
        String active = settings.getString(in.codebasket.cbwa.common.settings.SettingKeys.PAYMENT, "active_gateway", "NONE");
        GatewayType type = "NONE".equalsIgnoreCase(active) ? GatewayType.RAZORPAY : GatewayType.valueOf(active.toUpperCase());
        PaymentGateway gateway = gateways.stream().filter(g -> g.type() == type).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gateway", type.name());
        try {
            gateway.testConnection();
            out.put("ok", true);
            out.put("message", type.name() + " keys are working");
        } catch (GatewayException e) {
            out.put("ok", false);
            out.put("message", e.getHttpStatus() == 401 ? "Key ID or Key Secret is wrong" : e.getMessage());
        }
        out.put("webhookUrl", "/api/webhooks/payments/razorpay");
        out.put("activeAndConfigured", registry.active().isPresent());
        return ApiResponse.ok(out);
    }
}
