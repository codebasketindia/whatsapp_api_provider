package in.codebasket.cbwa.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.invoice.Invoice;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.payment.PaymentOrder;
import in.codebasket.cbwa.pricing.CreditPack;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentConfigDto(boolean enabled, String gateway, String keyId, boolean gstEnabled, BigDecimal gstPercent,
                                   BigDecimal minAmount, BigDecimal maxAmount, String companyName, String logoUrl) {
    }

    public record CreateOrderRequest(Long creditPackId, @DecimalMin(value = "1", message = "Enter a valid amount") BigDecimal amount) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 64) String gatewayOrderId, @NotBlank @Size(max = 64) String gatewayPaymentId,
                                @NotBlank @Size(max = 256) String signature) {
    }

    public record FailedRequest(@Size(max = 100) String code, @Size(max = 500) String description, @Size(max = 200) String reason) {
    }

    public record CheckoutOrderDto(String orderUid, String gateway, String gatewayOrderId, String keyId, long amountPaise,
                                   String currency, BigDecimal baseAmount, BigDecimal bonusAmount, BigDecimal gstAmount,
                                   BigDecimal totalAmount, BigDecimal creditAmount, Prefill prefill, String companyName,
                                   String description) {
        public record Prefill(String name, String email, String contact) {
        }
    }

    public record PackRef(Long id, String name) {
    }

    public record InvoiceRef(Long id, String invoiceNo) {
    }

    public record PartnerRef(Long id, String businessName) {
    }

    public record PaymentLogDto(Long id, String direction, String event, Integer httpStatus, JsonNode payload, Instant createdAt) {
    }

    public record PaymentOrderDto(String uid, String gateway, String gatewayOrderId, String gatewayPaymentId, PackRef creditPack,
                                  BigDecimal baseAmount, BigDecimal bonusAmount, BigDecimal gstPercent, BigDecimal gstAmount,
                                  BigDecimal totalAmount, BigDecimal creditAmount, String status, String method,
                                  String failureReason, String verifiedVia, Instant paidAt, InvoiceRef invoice,
                                  Instant createdAt, @JsonInclude(JsonInclude.Include.NON_NULL) PartnerRef partner,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) List<PaymentLogDto> logs) {

        public static PaymentOrderDto of(PaymentOrder o, CreditPack pack, Invoice inv, Partner partner) {
            return new PaymentOrderDto(o.getUid(), o.getGateway().name(), o.getGatewayOrderId(), o.getGatewayPaymentId(),
                    pack == null ? null : new PackRef(pack.getId(), pack.getName()), o.getBaseAmount(), o.getBonusAmount(),
                    o.getGstPercent(), o.getGstAmount(), o.getTotalAmount(), o.getCreditAmount(), o.getStatus().name(), o.getMethod(),
                    o.getFailureReason(), o.getVerifiedVia() == null ? null : o.getVerifiedVia().name(), o.getPaidAt(),
                    inv == null ? null : new InvoiceRef(inv.getId(), inv.getInvoiceNo()), o.getCreatedAt(),
                    partner == null ? null : new PartnerRef(partner.getId(), partner.getBusinessName()), null);
        }

        public PaymentOrderDto withLogs(List<PaymentLogDto> l) {
            return new PaymentOrderDto(uid, gateway, gatewayOrderId, gatewayPaymentId, creditPack, baseAmount, bonusAmount, gstPercent,
                    gstAmount, totalAmount, creditAmount, status, method, failureReason, verifiedVia, paidAt, invoice, createdAt,
                    partner, l);
        }
    }

    public record ReconcileSummary(int checked, int paid, int expired, int failed) {
    }
}
