package in.codebasket.cbwa.payment;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.invoice.Invoice;
import in.codebasket.cbwa.invoice.InvoiceService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.payment.PaymentGateway.GatewayException;
import in.codebasket.cbwa.payment.PaymentGateway.GatewayOrder;
import in.codebasket.cbwa.payment.PaymentGateway.GatewayPayment;
import in.codebasket.cbwa.payment.PaymentGateway.PaymentState;
import in.codebasket.cbwa.payment.dto.PaymentDtos.CheckoutOrderDto;
import in.codebasket.cbwa.payment.dto.PaymentDtos.PaymentConfigDto;
import in.codebasket.cbwa.payment.dto.PaymentDtos.PaymentLogDto;
import in.codebasket.cbwa.payment.dto.PaymentDtos.PaymentOrderDto;
import in.codebasket.cbwa.payment.dto.PaymentDtos.ReconcileSummary;
import in.codebasket.cbwa.pricing.CreditPack;
import in.codebasket.cbwa.pricing.CreditPackRepository;
import in.codebasket.cbwa.pricing.CreditPackService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * D3 UPI gateway top-ups: create order -> checkout -> server-side signature check + payment fetch ->
 * credit once (PaymentSettlementService). Webhooks and the reconciliation job use the same path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    static final Duration RECONCILE_AFTER = Duration.ofMinutes(15);
    static final Duration EXPIRE_AFTER = Duration.ofHours(24);

    private final PaymentOrderRepository repository;
    private final PaymentGatewayRegistry registry;
    private final PaymentSettlementService settlement;
    private final PaymentLogService logService;
    private final PaymentGatewayLogRepository logRepository;
    private final CreditPackService creditPackService;
    private final CreditPackRepository creditPackRepository;
    private final PartnerRepository partnerRepository;
    private final InvoiceService invoiceService;
    private final SettingsService settings;

    // ------------------------------------------------------------------ partner

    public PaymentConfigDto config() {
        var gateway = registry.active();
        String logoUid = settings.getString(SettingKeys.COMPANY, "logo_file_uid");
        return new PaymentConfigDto(gateway.isPresent(), gateway.map(g -> g.type().name()).orElse(null),
                gateway.map(PaymentGateway::publicKeyId).orElse(null), invoiceService.gstEnabled(),
                invoiceService.gstEnabled() ? invoiceService.gstPercent() : BigDecimal.ZERO, minAmount(), maxAmount(),
                settings.getString(SettingKeys.COMPANY, "name", "CB WhatsApp Gateway"),
                logoUid == null ? null : "/api/public/branding/logo?v=" + logoUid);
    }

    public CheckoutOrderDto createOrder(Long partnerId, Long creditPackId, BigDecimal amount) {
        PaymentGateway gateway = registry.requireActive();
        Partner partner = partnerRepository.findById(partnerId).orElseThrow(() -> AppException.notFound("Partner"));
        BigDecimal base;
        BigDecimal bonus = MoneyUtil.ZERO;
        Long packId = null;
        if (creditPackId != null) {
            CreditPack pack = creditPackService.requireActive(creditPackId);
            base = MoneyUtil.scale(pack.getPrice());
            bonus = CreditPackService.bonusOf(pack);
            packId = pack.getId();
        } else {
            if (amount == null) {
                throw AppException.field("amount", "Enter an amount or choose a pack");
            }
            if (amount.stripTrailingZeros().scale() > 2) {
                throw AppException.field("amount", "Amount can have at most 2 decimals");
            }
            if (amount.compareTo(minAmount()) < 0 || amount.compareTo(maxAmount()) > 0) {
                throw new AppException(ErrorCode.PAYMENT_AMOUNT_OUT_OF_RANGE, "Amount must be between ₹"
                        + MoneyUtil.rupees(minAmount()).toPlainString() + " and ₹" + MoneyUtil.rupees(maxAmount()).toPlainString());
            }
            base = MoneyUtil.scale(amount);
        }
        // Q1: GST is added on top of the wallet value; the wallet is credited base + bonus.
        BigDecimal gstPercent = invoiceService.gstEnabled() ? invoiceService.gstPercent() : BigDecimal.ZERO;
        BigDecimal gst = MoneyUtil.scale(MoneyUtil.rupees(MoneyUtil.percentOf(base, gstPercent)));
        BigDecimal total = MoneyUtil.scale(MoneyUtil.rupees(base.add(gst)));

        PaymentOrder o = new PaymentOrder();
        o.setUid(Ulids.next());
        o.setPartnerId(partnerId);
        o.setGateway(gateway.type());
        o.setCreditPackId(packId);
        o.setBaseAmount(base);
        o.setBonusAmount(bonus);
        o.setGstPercent(gstPercent.setScale(2, java.math.RoundingMode.HALF_UP));
        o.setGstAmount(gst);
        o.setTotalAmount(total);
        o.setCreditAmount(MoneyUtil.scale(base.add(bonus)));
        o.setCurrency("INR");
        o.setStatus(PaymentOrderStatus.CREATED);
        repository.save(o);

        long paise = MoneyUtil.toPaise(total);
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("partner_code", partner.getPartnerCode());
        notes.put("order_uid", o.getUid());
        try {
            GatewayOrder go = gateway.createOrder(paise, "INR", o.getUid(), notes);
            logService.log(o.getId(), gateway.type(), "RESPONSE", "order.create", 200, go.raw());
            o.setGatewayOrderId(go.id());
            repository.save(o);
        } catch (GatewayException e) {
            logService.log(o.getId(), gateway.type(), "RESPONSE", "order.create.failed", e.getHttpStatus(), Map.of("error", e.getMessage()));
            o.setStatus(PaymentOrderStatus.FAILED);
            o.setFailureReason("Could not start payment: " + e.getMessage());
            repository.save(o);
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED, "Online payment could not be started. Please try again");
        }
        return new CheckoutOrderDto(o.getUid(), gateway.type().name(), o.getGatewayOrderId(), gateway.publicKeyId(), paise, "INR",
                base, bonus, gst, total, o.getCreditAmount(),
                new CheckoutOrderDto.Prefill(partner.getContactName(), partner.getEmail(), partner.getMobile()),
                settings.getString(SettingKeys.COMPANY, "name", "CB WhatsApp Gateway"),
                "Wallet top-up " + o.getUid());
    }

    /** PG3: checkout success callback. The signature is checked and the payment fetched from the gateway. */
    public PaymentOrderDto verify(Long partnerId, String gatewayOrderId, String gatewayPaymentId, String signature) {
        PaymentOrder o = repository.findByGatewayOrderId(gatewayOrderId).filter(x -> x.getPartnerId().equals(partnerId))
                .orElseThrow(() -> AppException.notFound("Payment order"));
        PaymentGateway gateway = registry.of(o.getGateway());
        Map<String, Object> cb = new LinkedHashMap<>();
        cb.put("gatewayOrderId", gatewayOrderId);
        cb.put("gatewayPaymentId", gatewayPaymentId);
        if (!gateway.verifyCheckoutSignature(gatewayOrderId, gatewayPaymentId, signature)) {
            cb.put("signatureValid", false);
            logService.log(o.getId(), o.getGateway(), "CALLBACK", "checkout.signature_invalid", null, cb);
            throw new AppException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
        }
        cb.put("signatureValid", true);
        logService.log(o.getId(), o.getGateway(), "CALLBACK", "checkout.success", null, cb);
        try {
            GatewayPayment payment = gateway.fetchPayment(gatewayPaymentId);
            logService.log(o.getId(), o.getGateway(), "RESPONSE", "payment.fetch", 200, payment.raw());
            settlement.apply(o.getId(), payment, VerifiedVia.CHECKOUT);
        } catch (GatewayException e) {
            // The signature proves the checkout succeeded; the webhook / reconciliation job will credit it.
            logService.log(o.getId(), o.getGateway(), "RESPONSE", "payment.fetch.failed", e.getHttpStatus(), Map.of("error", e.getMessage()));
            markAttempted(o.getId());
        }
        return get(partnerId, o.getUid());
    }

    /** PG4: checkout failure information (informational - the order may still be paid later). */
    @Transactional
    public PaymentOrderDto failed(Long partnerId, String uid, String code, String description, String reason) {
        PaymentOrder o = repository.findByUid(uid).filter(x -> x.getPartnerId().equals(partnerId))
                .orElseThrow(() -> AppException.notFound("Payment order"));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("code", code);
        info.put("description", description);
        info.put("reason", reason);
        logService.log(o.getId(), o.getGateway(), "CALLBACK", "checkout.failed", null, info);
        if (o.getStatus() == PaymentOrderStatus.CREATED) {
            o.setStatus(PaymentOrderStatus.ATTEMPTED);
        }
        if (o.getStatus() != PaymentOrderStatus.PAID) {
            String msg = description == null || description.isBlank() ? "Payment was not completed" : description;
            o.setFailureReason(msg.length() > 250 ? msg.substring(0, 250) : msg);
            repository.save(o);
        }
        return toDtos(List.of(o), false).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderDto> listForPartner(Long partnerId, String status, LocalDate from, LocalDate to,
                                                        Integer page, Integer size) {
        return search(partnerId, status, null, from, to, null, page, size, false);
    }

    @Transactional(readOnly = true)
    public PaymentOrderDto get(Long partnerId, String uid) {
        PaymentOrder o = repository.findByUid(uid).filter(x -> partnerId == null || x.getPartnerId().equals(partnerId))
                .orElseThrow(() -> AppException.notFound("Payment order"));
        return toDtos(List.of(o), partnerId == null).get(0);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderDto> search(Long partnerId, String status, String gateway, LocalDate from, LocalDate to,
                                                String search, Integer page, Integer size, boolean adminView) {
        Specification<PaymentOrder> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (partnerId != null) {
                ps.add(cb.equal(root.get("partnerId"), partnerId));
            }
            if (status != null && !status.isBlank()) {
                try {
                    ps.add(cb.equal(root.get("status"), PaymentOrderStatus.valueOf(status.toUpperCase())));
                } catch (IllegalArgumentException e) {
                    throw AppException.field("status", "Unknown status");
                }
            }
            if (gateway != null && !gateway.isBlank()) {
                try {
                    ps.add(cb.equal(root.get("gateway"), GatewayType.valueOf(gateway.toUpperCase())));
                } catch (IllegalArgumentException e) {
                    throw AppException.field("gateway", "Unknown gateway");
                }
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeUtil.startOfDayIst(from)));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), TimeUtil.endOfDayIstExclusive(to)));
            }
            if (search != null && !search.isBlank()) {
                String s = search.trim();
                ps.add(cb.or(cb.equal(root.get("uid"), s), cb.equal(root.get("gatewayOrderId"), s),
                        cb.equal(root.get("gatewayPaymentId"), s)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<PaymentOrder> result = repository.findAll(spec, Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse<>(toDtos(result.getContent(), adminView), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PaymentOrderDto adminDetail(String uid) {
        PaymentOrder o = repository.findByUid(uid).orElseThrow(() -> AppException.notFound("Payment order"));
        PaymentOrderDto dto = toDtos(List.of(o), true).get(0);
        List<PaymentLogDto> logs = logRepository.findByPaymentOrderIdOrderByIdAsc(o.getId()).stream()
                .map(l -> new PaymentLogDto(l.getId(), l.getDirection(), l.getEvent(), l.getHttpStatus(),
                        l.getPayload() == null ? null : JsonUtil.readTree(l.getPayload()), l.getCreatedAt())).toList();
        return dto.withLogs(logs);
    }

    /** PG11 / job: brings one order in line with the gateway. */
    public PaymentOrderDto reconcile(String uid) {
        PaymentOrder o = repository.findByUid(uid).orElseThrow(() -> AppException.notFound("Payment order"));
        reconcileOne(o);
        return adminDetail(uid);
    }

    /** PG12 / job: every CREATED/ATTEMPTED order older than 15 minutes. */
    public ReconcileSummary reconcilePending() {
        List<PaymentOrder> stuck = repository.findTop200ByStatusInAndCreatedAtBeforeOrderByIdAsc(
                List.of(PaymentOrderStatus.CREATED, PaymentOrderStatus.ATTEMPTED), Instant.now().minus(RECONCILE_AFTER));
        int paid = 0;
        int expired = 0;
        int failed = 0;
        for (PaymentOrder o : stuck) {
            try {
                PaymentOrderStatus s = reconcileOne(o);
                if (s == PaymentOrderStatus.PAID) {
                    paid++;
                } else if (s == PaymentOrderStatus.EXPIRED) {
                    expired++;
                } else if (s == PaymentOrderStatus.FAILED) {
                    failed++;
                }
            } catch (RuntimeException e) {
                log.warn("Reconciliation of order {} failed: {}", o.getUid(), e.getMessage());
            }
        }
        return new ReconcileSummary(stuck.size(), paid, expired, failed);
    }

    PaymentOrderStatus reconcileOne(PaymentOrder o) {
        if (o.getStatus() == PaymentOrderStatus.PAID) {
            return o.getStatus();
        }
        boolean old = o.getCreatedAt().isBefore(Instant.now().minus(EXPIRE_AFTER));
        if (o.getGatewayOrderId() == null) {
            if (old && o.getStatus() != PaymentOrderStatus.FAILED) {
                setStatus(o.getId(), PaymentOrderStatus.EXPIRED, null);
                return PaymentOrderStatus.EXPIRED;
            }
            return o.getStatus();
        }
        PaymentGateway gateway = registry.of(o.getGateway());
        List<GatewayPayment> payments;
        try {
            payments = gateway.fetchOrderPayments(o.getGatewayOrderId());
        } catch (GatewayException e) {
            logService.log(o.getId(), o.getGateway(), "RESPONSE", "reconcile.failed", e.getHttpStatus(), Map.of("error", e.getMessage()));
            return o.getStatus();
        }
        logService.log(o.getId(), o.getGateway(), "RESPONSE", "reconcile.payments", 200,
                Map.of("count", payments.size(), "states", payments.stream().map(p -> p.state().name()).toList()));
        GatewayPayment captured = payments.stream().filter(p -> p.state() == PaymentState.CAPTURED).findFirst().orElse(null);
        if (captured != null) {
            settlement.apply(o.getId(), captured, VerifiedVia.RECONCILE);
            return repository.findById(o.getId()).map(PaymentOrder::getStatus).orElse(o.getStatus());
        }
        if (payments.stream().anyMatch(p -> p.state() == PaymentState.AUTHORIZED || p.state() == PaymentState.PENDING)) {
            if (o.getStatus() == PaymentOrderStatus.CREATED) {
                setStatus(o.getId(), PaymentOrderStatus.ATTEMPTED, null);
            }
            return PaymentOrderStatus.ATTEMPTED;
        }
        if (!payments.isEmpty() && old) {
            GatewayPayment last = payments.get(payments.size() - 1);
            setStatus(o.getId(), PaymentOrderStatus.FAILED, last.errorDescription() == null ? "Payment failed" : last.errorDescription());
            return PaymentOrderStatus.FAILED;
        }
        if (payments.isEmpty() && old) {
            setStatus(o.getId(), PaymentOrderStatus.EXPIRED, "No payment was made");
            return PaymentOrderStatus.EXPIRED;
        }
        return o.getStatus();
    }

    // ------------------------------------------------------------------ webhook

    /** Handles a verified Razorpay webhook event (payment.captured / order.paid / payment.failed). Returns the outcome label. */
    public String handleRazorpayEvent(JsonNode event) {
        String type = event.path("event").asText("");
        JsonNode paymentNode = event.path("payload").path("payment").path("entity");
        if (paymentNode.isMissingNode() || paymentNode.isNull()) {
            return "IGNORED";
        }
        GatewayPayment payment = RazorpayGateway.toPayment(paymentNode);
        PaymentOrder o = payment.orderId() == null ? null : repository.findByGatewayOrderId(payment.orderId()).orElse(null);
        if (o == null) {
            log.info("Razorpay webhook {} for unknown order {} - ignored", type, payment.orderId());
            return "IGNORED";
        }
        logService.log(o.getId(), GatewayType.RAZORPAY, "WEBHOOK", type, null, event);
        switch (type) {
            case "payment.captured", "order.paid", "payment.failed", "payment.authorized" -> {
                return settlement.apply(o.getId(), payment, VerifiedVia.WEBHOOK).name();
            }
            default -> {
                return "IGNORED";
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void markAttempted(Long orderId) {
        repository.findById(orderId).ifPresent(o -> {
            if (o.getStatus() == PaymentOrderStatus.CREATED) {
                o.setStatus(PaymentOrderStatus.ATTEMPTED);
                repository.save(o);
            }
        });
    }

    private void setStatus(Long orderId, PaymentOrderStatus status, String reason) {
        repository.findById(orderId).ifPresent(o -> {
            if (o.getStatus() != PaymentOrderStatus.PAID) {
                o.setStatus(status);
                if (reason != null) {
                    o.setFailureReason(reason.length() > 250 ? reason.substring(0, 250) : reason);
                }
                repository.save(o);
            }
        });
    }

    private BigDecimal minAmount() {
        return settings.getDecimal(SettingKeys.PAYMENT, "min_amount", new BigDecimal("100"));
    }

    private BigDecimal maxAmount() {
        return settings.getDecimal(SettingKeys.PAYMENT, "max_amount", new BigDecimal("200000"));
    }

    private List<PaymentOrderDto> toDtos(List<PaymentOrder> orders, boolean adminView) {
        Set<Long> packIds = new HashSet<>();
        Set<Long> invoiceIds = new HashSet<>();
        Set<Long> partnerIds = new HashSet<>();
        orders.forEach(o -> {
            if (o.getCreditPackId() != null) {
                packIds.add(o.getCreditPackId());
            }
            if (o.getInvoiceId() != null) {
                invoiceIds.add(o.getInvoiceId());
            }
            partnerIds.add(o.getPartnerId());
        });
        Map<Long, CreditPack> packs = creditPackRepository.findAllById(packIds).stream()
                .collect(Collectors.toMap(CreditPack::getId, Function.identity()));
        Map<Long, Invoice> invoices = new java.util.HashMap<>();
        invoiceIds.forEach(id -> invoiceService.find(id).ifPresent(i -> invoices.put(id, i)));
        Map<Long, Partner> partners = adminView ? partnerRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(Partner::getId, Function.identity())) : Map.of();
        return orders.stream().map(o -> PaymentOrderDto.of(o, packs.get(o.getCreditPackId()), invoices.get(o.getInvoiceId()),
                adminView ? partners.get(o.getPartnerId()) : null)).toList();
    }
}
