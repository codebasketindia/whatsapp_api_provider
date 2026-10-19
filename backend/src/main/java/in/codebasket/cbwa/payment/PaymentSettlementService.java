package in.codebasket.cbwa.payment;

import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.invoice.Invoice;
import in.codebasket.cbwa.invoice.InvoiceService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.payment.PaymentGateway.GatewayPayment;
import in.codebasket.cbwa.payment.PaymentGateway.PaymentState;
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletService;
import in.codebasket.cbwa.wallet.WalletTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Credits the wallet for a verified gateway payment EXACTLY ONCE (D3). Checkout verification,
 * webhooks and the reconciliation job all end here; the payment_orders row is locked and its PAID
 * status + the UNIQUE gateway_payment_id make repeated callbacks harmless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    public enum Outcome { CREDITED, ALREADY_PAID, PENDING, FAILED, MISMATCH, IGNORED }

    private final PaymentOrderRepository repository;
    private final PartnerRepository partnerRepository;
    private final WalletService walletService;
    private final InvoiceService invoiceService;
    private final NotificationService notifications;

    @Transactional
    public Outcome apply(Long orderId, GatewayPayment payment, VerifiedVia via) {
        PaymentOrder o = repository.lockById(orderId).orElseThrow();
        if (o.getStatus() == PaymentOrderStatus.PAID) {
            return Outcome.ALREADY_PAID;
        }
        if (payment.id() != null) {
            Optional<PaymentOrder> other = repository.findByGatewayPaymentId(payment.id()).filter(x -> !x.getId().equals(o.getId()));
            if (other.isPresent()) {
                log.warn("Payment {} already belongs to order {} - ignoring for order {}", payment.id(), other.get().getUid(), o.getUid());
                return Outcome.IGNORED;
            }
        }
        long expectedPaise = MoneyUtil.toPaise(o.getTotalAmount());
        boolean orderMatches = payment.orderId() != null && payment.orderId().equals(o.getGatewayOrderId());
        if (!orderMatches || (payment.state() == PaymentState.CAPTURED && payment.amountPaise() != expectedPaise)) {
            o.setStatus(PaymentOrderStatus.ATTEMPTED);
            o.setFailureReason("Payment does not match this order (amount or order id). Under review");
            o.setRawPayload(JsonUtil.toJson(PaymentLogService.sanitize(payment.raw())));
            repository.save(o);
            notifications.notifyAdmins("PAYMENT_MISMATCH", NotificationService.ERROR, "Payment mismatch on order " + o.getUid(),
                    "Gateway payment " + payment.id() + " (" + payment.amountPaise() + " paise) does not match the order total of "
                            + expectedPaise + " paise. Please check manually.", "/admin/payments/" + o.getUid());
            return Outcome.MISMATCH;
        }
        o.setMethod(payment.method());
        o.setRawPayload(JsonUtil.toJson(PaymentLogService.sanitize(payment.raw())));
        switch (payment.state()) {
            case AUTHORIZED, PENDING -> {
                o.setStatus(PaymentOrderStatus.ATTEMPTED);
                o.setGatewayPaymentId(payment.id());
                repository.save(o);
                return Outcome.PENDING;
            }
            case FAILED, REFUNDED -> {
                o.setStatus(PaymentOrderStatus.FAILED);
                o.setFailureReason(payment.errorDescription() == null ? "Payment failed" : truncate(payment.errorDescription()));
                repository.save(o);
                return Outcome.FAILED;
            }
            default -> {
                // CAPTURED - credit below
            }
        }
        o.setGatewayPaymentId(payment.id());
        o.setStatus(PaymentOrderStatus.PAID);
        o.setFailureReason(null);
        o.setVerifiedVia(via);
        o.setPaidAt(payment.createdAt() != null ? payment.createdAt() : Instant.now());
        repository.saveAndFlush(o);

        String remarks = "Online payment " + o.getUid() + ": ₹" + MoneyUtil.rupees(o.getBaseAmount()).toPlainString()
                + (o.getBonusAmount().signum() > 0 ? " + bonus ₹" + MoneyUtil.rupees(o.getBonusAmount()).toPlainString() : "");
        WalletTransaction txn = walletService.credit(o.getPartnerId(), o.getCreditAmount(), WalletRefType.PAYMENT_ORDER, o.getUid(), remarks);
        o.setWalletTransactionId(txn.getId());
        if (o.getGstAmount().signum() > 0 || invoiceService.gstEnabled()) {
            Invoice inv = invoiceService.forPayment(o);
            o.setInvoiceId(inv.getId());
        }
        repository.save(o);

        Partner p = partnerRepository.findById(o.getPartnerId()).orElse(null);
        notifications.notifyPartner(o.getPartnerId(), "PAYMENT_SUCCESS", NotificationService.SUCCESS, "Payment successful",
                "₹" + MoneyUtil.rupees(o.getCreditAmount()).toPlainString() + " has been added to your wallet. New balance: ₹"
                        + MoneyUtil.rupees(txn.getBalanceAfter()).toPlainString(), "/partner/wallet/payment/" + o.getUid(),
                p == null ? null : p.getEmail());
        log.info("Payment order {} paid via {} - wallet credited {}", o.getUid(), via, o.getCreditAmount());
        return Outcome.CREDITED;
    }

    private static String truncate(String s) {
        return s.length() > 250 ? s.substring(0, 250) : s;
    }
}
