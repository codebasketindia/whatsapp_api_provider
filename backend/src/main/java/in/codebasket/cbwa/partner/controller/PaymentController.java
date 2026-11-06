package in.codebasket.cbwa.partner.controller;

import in.codebasket.cbwa.common.service.payment.PaymentGateway;
import in.codebasket.cbwa.common.service.payment.PaymentGatewayRegistry;
import in.codebasket.cbwa.common.service.payment.PaymentService;

import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.common.dto.PageResponse;
import in.codebasket.cbwa.common.service.audit.AuditService;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.exception.ErrorCode;
import in.codebasket.cbwa.common.service.payment.PaymentGateway.GatewayException;
import in.codebasket.cbwa.common.dto.PaymentDtos.CheckoutOrderDto;
import in.codebasket.cbwa.common.dto.PaymentDtos.CreateOrderRequest;
import in.codebasket.cbwa.common.dto.PaymentDtos.FailedRequest;
import in.codebasket.cbwa.common.dto.PaymentDtos.PaymentConfigDto;
import in.codebasket.cbwa.common.dto.PaymentDtos.PaymentOrderDto;
import in.codebasket.cbwa.common.dto.PaymentDtos.ReconcileSummary;
import in.codebasket.cbwa.common.dto.PaymentDtos.VerifyRequest;
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
    private final in.codebasket.cbwa.common.service.settings.SettingsService settings;
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
}
