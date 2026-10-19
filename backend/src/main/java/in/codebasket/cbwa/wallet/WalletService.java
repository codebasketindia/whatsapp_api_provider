package in.codebasket.cbwa.wallet;

import in.codebasket.cbwa.auth.AuthPrincipal;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.Ulids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * The only place that changes wallet balances (D1). Every method locks the wallet row with
 * PESSIMISTIC_WRITE, writes one immutable ledger row with before/after values and never lets
 * balance or held amount go negative. Callers are responsible for idempotency of their reference
 * (e.g. a message's charge_status, a top-up's status) and must call these methods inside their
 * own transaction so the reference update and the ledger row commit together.
 *
 * Semantics: balance = available money; held = money reserved for in-flight messages.
 *   CREDIT  balance += a              DEBIT (direct)  balance -= a
 *   HOLD    balance -= a, held += a   DEBIT (finalize hold) held -= a
 *   RELEASE held -= a, balance += a   REFUND (of a hold) held -= a, balance += a
 *   REFUND (of a finalized charge) balance += a
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txnRepository;
    private final LowBalanceMonitor lowBalanceMonitor;

    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet createWallet(Long partnerId) {
        Wallet w = new Wallet();
        w.setPartnerId(partnerId);
        w.setBalance(MoneyUtil.ZERO);
        w.setHeldAmount(MoneyUtil.ZERO);
        w.setCurrency("INR");
        return walletRepository.save(w);
    }

    @Transactional(readOnly = true)
    public Wallet get(Long partnerId) {
        return walletRepository.findByPartnerId(partnerId).orElseThrow(() -> AppException.notFound("Wallet"));
    }

    @Transactional
    public WalletTransaction credit(Long partnerId, BigDecimal amount, WalletRefType ref, String refId, String remarks) {
        Wallet w = lock(partnerId);
        return apply(w, WalletTxnType.CREDIT, amount, amount, BigDecimal.ZERO, ref, refId, remarks);
    }

    /** Direct debit from available balance (admin debit, monthly platform fee). */
    @Transactional
    public WalletTransaction debit(Long partnerId, BigDecimal amount, WalletRefType ref, String refId, String remarks) {
        Wallet w = lock(partnerId);
        requireAvailable(w, amount, ErrorCode.WALLET_NEGATIVE_NOT_ALLOWED);
        return apply(w, WalletTxnType.DEBIT, amount, amount.negate(), BigDecimal.ZERO, ref, refId, remarks);
    }

    /** Reserves money for a message before it is sent. Throws WALLET_INSUFFICIENT_BALANCE. */
    @Transactional
    public WalletTransaction hold(Long partnerId, BigDecimal amount, WalletRefType ref, String refId, String remarks) {
        Wallet w = lock(partnerId);
        requireAvailable(w, amount, ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        return apply(w, WalletTxnType.HOLD, amount, amount.negate(), amount, ref, refId, remarks);
    }

    /** Turns a hold into a final charge (message SENT/DELIVERED). */
    @Transactional
    public WalletTransaction finalizeHold(Long partnerId, BigDecimal amount, WalletRefType ref, String refId, String remarks) {
        Wallet w = lock(partnerId);
        requireHeld(w, amount);
        return apply(w, WalletTxnType.DEBIT, amount, BigDecimal.ZERO, amount.negate(), ref, refId, remarks);
    }

    /** Returns a hold to the balance because the message was never sent (Meta rejected it). */
    @Transactional
    public WalletTransaction release(Long partnerId, BigDecimal amount, WalletRefType ref, String refId, String remarks) {
        Wallet w = lock(partnerId);
        requireHeld(w, amount);
        return apply(w, WalletTxnType.RELEASE, amount, amount, amount.negate(), ref, refId, remarks);
    }

    /** Refund of a still-open hold (e.g. charge-on-DELIVERED and the message FAILED). */
    @Transactional
    public WalletTransaction refundHold(Long partnerId, BigDecimal amount, WalletRefType ref, String refId, String remarks) {
        Wallet w = lock(partnerId);
        requireHeld(w, amount);
        return apply(w, WalletTxnType.REFUND, amount, amount, amount.negate(), ref, refId, remarks);
    }

    /** Refund of money that was already finally charged. */
    @Transactional
    public WalletTransaction refund(Long partnerId, BigDecimal amount, WalletRefType ref, String refId, String remarks) {
        Wallet w = lock(partnerId);
        return apply(w, WalletTxnType.REFUND, amount, amount, BigDecimal.ZERO, ref, refId, remarks);
    }

    private Wallet lock(Long partnerId) {
        return walletRepository.lockByPartnerId(partnerId).orElseThrow(() -> AppException.notFound("Wallet"));
    }

    private static void requireAvailable(Wallet w, BigDecimal amount, ErrorCode code) {
        validateAmount(amount);
        if (w.getBalance().compareTo(amount) < 0) {
            throw new AppException(code, code.getDefaultMessage(),
                    Map.of("required", MoneyUtil.str(amount), "available", MoneyUtil.str(w.getBalance())));
        }
    }

    private static void requireHeld(Wallet w, BigDecimal amount) {
        validateAmount(amount);
        if (w.getHeldAmount().compareTo(amount) < 0) {
            // Should never happen: indicates a double finalize/release bug in the caller.
            throw new IllegalStateException("Held amount " + w.getHeldAmount() + " is less than " + amount + " for wallet " + w.getId());
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw AppException.field("amount", "Amount must be greater than zero");
        }
        if (amount.scale() > 4 && amount.stripTrailingZeros().scale() > 4) {
            throw AppException.field("amount", "Amount can have at most 4 decimals");
        }
    }

    private WalletTransaction apply(Wallet w, WalletTxnType type, BigDecimal amount, BigDecimal balanceDelta,
                                    BigDecimal heldDelta, WalletRefType ref, String refId, String remarks) {
        validateAmount(amount);
        BigDecimal balanceBefore = w.getBalance();
        BigDecimal heldBefore = w.getHeldAmount();
        BigDecimal balanceAfter = MoneyUtil.scale(balanceBefore.add(balanceDelta));
        BigDecimal heldAfter = MoneyUtil.scale(heldBefore.add(heldDelta));
        if (balanceAfter.signum() < 0 || heldAfter.signum() < 0) {
            throw new AppException(ErrorCode.WALLET_NEGATIVE_NOT_ALLOWED);
        }
        w.setBalance(balanceAfter);
        w.setHeldAmount(heldAfter);
        walletRepository.save(w);

        WalletTransaction t = new WalletTransaction();
        t.setUid(Ulids.next());
        t.setWalletId(w.getId());
        t.setPartnerId(w.getPartnerId());
        t.setType(type);
        t.setAmount(MoneyUtil.scale(amount));
        t.setBalanceBefore(balanceBefore);
        t.setBalanceAfter(balanceAfter);
        t.setHeldBefore(heldBefore);
        t.setHeldAfter(heldAfter);
        t.setReferenceType(ref);
        t.setReferenceId(refId);
        t.setRemarks(remarks != null && remarks.length() > 500 ? remarks.substring(0, 500) : remarks);
        AuthPrincipal p = SecurityUtils.currentOrNull();
        if (p != null) {
            t.setCreatedBy(p.userId());
            t.setImpersonatorUserId(p.impersonatorUserId());
        }
        t.setCreatedAt(Instant.now());
        txnRepository.save(t);

        if (balanceDelta.signum() != 0) {
            lowBalanceMonitor.onBalanceChanged(w.getPartnerId(), balanceBefore, balanceAfter);
        }
        return t;
    }
}
