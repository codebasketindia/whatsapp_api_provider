package in.codebasket.cbwa.wallet;

import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.partner.PartnerSettings;
import in.codebasket.cbwa.partner.PartnerSettingsRepository;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserRepository;
import in.codebasket.cbwa.wallet.dto.WalletDtos.CreatedByRef;
import in.codebasket.cbwa.wallet.dto.WalletDtos.WalletDto;
import in.codebasket.cbwa.wallet.dto.WalletDtos.WalletTxnDto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wallet reads (W1, W2, W12, W14, W15), threshold update (W13) and admin manual adjustment (W3).
 * Passbook: message-level rows (reference MESSAGE / CAMPAIGN: holds, finalised charges, releases,
 * refunds) are hidden unless includeMessageEntries=true, so the default view shows money movements
 * (top-ups, payments, adjustments, platform fees) with running balances.
 */
@Service
@RequiredArgsConstructor
public class WalletQueryService {

    public static final int EXPORT_MAX_ROWS = 100_000;
    private static final Set<WalletRefType> MESSAGE_REFS = Set.of(WalletRefType.MESSAGE, WalletRefType.CAMPAIGN);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txnRepository;
    private final WalletService walletService;
    private final LowBalanceMonitor lowBalanceMonitor;
    private final PartnerRepository partnerRepository;
    private final PartnerSettingsRepository partnerSettingsRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public WalletDto wallet(Long partnerId) {
        Wallet w = walletRepository.findByPartnerId(partnerId).orElseThrow(() -> AppException.notFound("Wallet"));
        BigDecimal threshold = MoneyUtil.scale(lowBalanceMonitor.thresholdFor(partnerId));
        boolean low = threshold.signum() > 0 && w.getBalance().compareTo(threshold) < 0;
        return new WalletDto(w.getBalance(), w.getHeldAmount(), threshold, low, w.getCurrency());
    }

    @Transactional
    public WalletDto updateThreshold(Long partnerId, BigDecimal threshold) {
        if (threshold == null || threshold.signum() < 0) {
            throw AppException.field("threshold", "Must be zero or more");
        }
        PartnerSettings ps = partnerSettingsRepository.findById(partnerId).orElseGet(() -> {
            PartnerSettings n = new PartnerSettings();
            n.setPartnerId(partnerId);
            return n;
        });
        ps.setLowBalanceThreshold(MoneyUtil.scale(threshold));
        ps.setLowBalanceAlertedAt(null);
        partnerSettingsRepository.save(ps);
        return wallet(partnerId);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTxnDto> transactions(Long partnerId, String type, LocalDate from, LocalDate to,
                                                   boolean includeMessageEntries, Integer page, Integer size) {
        Wallet w = walletRepository.findByPartnerId(partnerId).orElseThrow(() -> AppException.notFound("Wallet"));
        Page<WalletTransaction> result = txnRepository.findAll(spec(w.getId(), type, from, to, includeMessageEntries),
                Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id")));
        List<WalletTxnDto> dtos = toDtos(result.getContent());
        return new PageResponse<>(dtos, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Rows for export, oldest first (max EXPORT_MAX_ROWS). */
    @Transactional(readOnly = true)
    public List<WalletTxnDto> exportRows(Long partnerId, String type, LocalDate from, LocalDate to, boolean includeMessageEntries) {
        Wallet w = walletRepository.findByPartnerId(partnerId).orElseThrow(() -> AppException.notFound("Wallet"));
        LocalDate t = to != null ? to : TimeUtil.todayIst();
        LocalDate f = from != null ? from : t.minusDays(30);
        return toDtos(txnRepository.findAll(spec(w.getId(), type, f, t, includeMessageEntries),
                PageRequest.of(0, EXPORT_MAX_ROWS, Sort.by(Sort.Direction.ASC, "id"))).getContent());
    }

    /** W3: manual credit/debit by admin with mandatory remark; audited and notified. */
    @Transactional
    public WalletTxnDto adjust(Long partnerId, String type, BigDecimal amount, String remarks) {
        Partner p = partnerRepository.findById(partnerId).orElseThrow(() -> AppException.notFound("Partner"));
        if (amount == null || amount.signum() <= 0) {
            throw AppException.field("amount", "Amount must be greater than zero");
        }
        String note = remarks.trim();
        WalletTransaction t = "CREDIT".equals(type)
                ? walletService.credit(partnerId, amount, WalletRefType.ADMIN_ADJUSTMENT, null, note)
                : walletService.debit(partnerId, amount, WalletRefType.ADMIN_ADJUSTMENT, null, note);
        auditService.log("WALLET_ADJUST", "WALLET", t.getWalletId(), Map.of("balance", MoneyUtil.str(t.getBalanceBefore())),
                Map.of("type", type, "amount", MoneyUtil.str(amount), "balance", MoneyUtil.str(t.getBalanceAfter()),
                        "remarks", note, "txnUid", t.getUid()), partnerId);
        String verb = "CREDIT".equals(type) ? "added to" : "deducted from";
        notifications.notifyPartner(partnerId, "WALLET_ADJUSTMENT", NotificationService.INFO,
                "₹" + MoneyUtil.rupees(amount).toPlainString() + " " + verb + " your wallet",
                "Remark: " + note + ". New balance: ₹" + MoneyUtil.rupees(t.getBalanceAfter()).toPlainString(),
                "/partner/wallet", p.getEmail());
        return toDtos(List.of(t)).get(0);
    }

    public List<WalletTxnDto> toDtos(List<WalletTransaction> txns) {
        Set<Long> userIds = new HashSet<>();
        txns.forEach(t -> {
            if (t.getCreatedBy() != null) {
                userIds.add(t.getCreatedBy());
            }
        });
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return txns.stream().map(t -> {
            User u = t.getCreatedBy() == null ? null : users.get(t.getCreatedBy());
            return new WalletTxnDto(t.getUid(), t.getType().name(), t.getAmount(), t.getBalanceBefore(), t.getBalanceAfter(),
                    t.getHeldBefore(), t.getHeldAfter(), t.getReferenceType().name(), t.getReferenceId(), t.getRemarks(),
                    u == null ? null : new CreatedByRef(u.getUid(), u.getName()), t.getCreatedAt());
        }).toList();
    }

    private static Specification<WalletTransaction> spec(Long walletId, String type, LocalDate from, LocalDate to,
                                                         boolean includeMessageEntries) {
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("walletId"), walletId));
            if (type != null && !type.isBlank()) {
                try {
                    ps.add(cb.equal(root.get("type"), WalletTxnType.valueOf(type)));
                } catch (IllegalArgumentException e) {
                    throw AppException.field("type", "Unknown transaction type");
                }
            }
            if (!includeMessageEntries) {
                ps.add(cb.not(root.get("referenceType").in(MESSAGE_REFS)));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeUtil.startOfDayIst(from)));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), TimeUtil.endOfDayIstExclusive(to)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
