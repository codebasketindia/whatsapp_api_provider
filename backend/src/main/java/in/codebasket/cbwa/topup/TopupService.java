package in.codebasket.cbwa.topup;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.sequence.NumberSequenceService;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.storage.StoredFileRepository;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.invoice.Invoice;
import in.codebasket.cbwa.invoice.InvoiceService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserRepository;
import in.codebasket.cbwa.wallet.WalletQueryService;
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletService;
import in.codebasket.cbwa.wallet.WalletTransaction;
import in.codebasket.cbwa.wallet.dto.WalletDtos.WalletTxnDto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * D2 manual top-up requests: PENDING -> APPROVED (wallet credited atomically, once) / REJECTED.
 * Duplicate UTRs are blocked by a unique index; approval locks the request row so two admins
 * clicking at the same time can never credit twice.
 */
@Service
@RequiredArgsConstructor
public class TopupService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    private static final DateTimeFormatter YYMM = DateTimeFormatter.ofPattern("yyMM");

    private final TopupRequestRepository repository;
    private final PartnerRepository partnerRepository;
    private final UserRepository userRepository;
    private final StoredFileRepository fileRepository;
    private final FileStorageService storage;
    private final WalletService walletService;
    private final WalletQueryService walletQueryService;
    private final InvoiceService invoiceService;
    private final NumberSequenceService sequences;
    private final SettingsService settings;
    private final AuditService auditService;
    private final NotificationService notifications;

    public record PartnerRef(Long id, String businessName) {
    }

    public record ReviewerRef(String uid, String name) {
    }

    public record InvoiceRef(Long id, String invoiceNo) {
    }

    public record TopupRequestDto(Long id, String requestNo, PartnerRef partner, BigDecimal amount, String paymentMode, String utr,
                                  LocalDate paymentDate, String screenshotUrl, String screenshotMimeType, String note,
                                  String status, String rejectReason, ReviewerRef reviewedBy, Instant reviewedAt,
                                  InvoiceRef invoice, Instant createdAt, WalletTxnDto walletTransaction) {
    }

    // ------------------------------------------------------------------ partner

    @Transactional
    public TopupRequestDto submit(Long partnerId, BigDecimal amount, String paymentMode, String utr, LocalDate paymentDate,
                                  String note, MultipartFile screenshot) {
        if (amount == null || amount.signum() <= 0) {
            throw AppException.field("amount", "Amount must be greater than zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw AppException.field("amount", "Amount is too large");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw AppException.field("amount", "Amount can have at most 2 decimals");
        }
        PaymentMode mode;
        try {
            mode = PaymentMode.valueOf(paymentMode == null ? "" : paymentMode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw AppException.field("paymentMode", "Choose UPI, NEFT, IMPS, Cash or Other");
        }
        LocalDate today = TimeUtil.todayIst();
        if (paymentDate == null) {
            throw AppException.field("paymentDate", "Please enter the payment date");
        }
        if (paymentDate.isAfter(today)) {
            throw AppException.field("paymentDate", "Payment date cannot be in the future");
        }
        if (paymentDate.isBefore(today.minusDays(90))) {
            throw AppException.field("paymentDate", "Payments older than 90 days cannot be submitted. Please contact support");
        }
        String normalizedUtr = utr == null ? "" : utr.trim().toUpperCase().replaceAll("\\s+", "");
        if (mode != PaymentMode.CASH) {
            if (!normalizedUtr.matches("^[A-Z0-9/-]{6,64}$")) {
                throw AppException.field("utr", "Enter the UTR / reference number (6-64 letters or digits)");
            }
            if (screenshot == null || screenshot.isEmpty()) {
                throw AppException.field("screenshot", "Please attach the payment screenshot or receipt");
            }
        } else if (!normalizedUtr.isEmpty() && !normalizedUtr.matches("^[A-Z0-9/-]{3,64}$")) {
            throw AppException.field("utr", "Reference can have letters, digits, / and - only");
        }
        if (!normalizedUtr.isEmpty() && repository.existsByUtr(normalizedUtr)) {
            throw new AppException(ErrorCode.TOPUP_DUPLICATE_UTR);
        }
        if (note != null && note.length() > 500) {
            throw AppException.field("note", "Note can be at most 500 characters");
        }

        TopupRequest t = new TopupRequest();
        t.setRequestNo(sequences.nextFormatted("TOPUP", "TR" + YYMM.format(today), 6));
        t.setPartnerId(partnerId);
        t.setAmount(MoneyUtil.scale(amount));
        t.setPaymentMode(mode);
        t.setUtr(normalizedUtr.isEmpty() ? "CASH-" + t.getRequestNo() : normalizedUtr);
        t.setPaymentDate(paymentDate);
        t.setNote(note == null || note.isBlank() ? null : note.trim());
        t.setStatus(TopupStatus.PENDING);
        if (screenshot != null && !screenshot.isEmpty()) {
            long max = settings.getInt(SettingKeys.STORAGE, SettingKeys.MAX_UPLOAD_MB, 2) * 1024L * 1024L;
            StoredFile f = storage.store(screenshot, FileCategory.TOPUP_PROOF, partnerId, FileStorageService.IMAGES_PDF, max);
            t.setScreenshotFileId(f.getId());
        }
        try {
            repository.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.TOPUP_DUPLICATE_UTR);
        }
        Partner p = partnerRepository.findById(partnerId).orElseThrow();
        notifications.notifyAdmins("TOPUP_REQUEST", NotificationService.INFO, "New top-up request " + t.getRequestNo(),
                p.getBusinessName() + " submitted ₹" + MoneyUtil.rupees(t.getAmount()).toPlainString() + " via " + mode
                        + " (UTR " + t.getUtr() + ")", "/admin/topups?status=PENDING");
        return toDtos(List.of(t), false).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<TopupRequestDto> listForPartner(Long partnerId, String status, Integer page, Integer size) {
        return search(partnerId, status, null, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public TopupRequestDto getForPartner(Long partnerId, Long id) {
        TopupRequest t = repository.findById(id).filter(x -> x.getPartnerId().equals(partnerId))
                .orElseThrow(() -> AppException.notFound("Top-up request"));
        return toDtos(List.of(t), false).get(0);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<TopupRequestDto> search(Long partnerId, String status, String paymentMode, LocalDate from, LocalDate to,
                                                String search, Integer page, Integer size) {
        Specification<TopupRequest> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (partnerId != null) {
                ps.add(cb.equal(root.get("partnerId"), partnerId));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), parse(TopupStatus.class, status, "status")));
            }
            if (paymentMode != null && !paymentMode.isBlank()) {
                ps.add(cb.equal(root.get("paymentMode"), parse(PaymentMode.class, paymentMode, "paymentMode")));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeUtil.startOfDayIst(from)));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), TimeUtil.endOfDayIstExclusive(to)));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toUpperCase() + "%";
                ps.add(cb.or(cb.like(root.get("utr"), like), cb.like(cb.upper(root.get("requestNo")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<TopupRequest> result = repository.findAll(spec, Paging.of(page, size, null, Set.of(),
                Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse<>(toDtos(result.getContent(), false), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TopupRequestDto get(Long id) {
        return toDtos(List.of(repository.findById(id).orElseThrow(() -> AppException.notFound("Top-up request"))), false).get(0);
    }

    @Transactional
    public TopupRequestDto approve(Long id, String remarks) {
        TopupRequest t = repository.lockById(id).orElseThrow(() -> AppException.notFound("Top-up request"));
        if (t.getStatus() != TopupStatus.PENDING) {
            throw new AppException(ErrorCode.TOPUP_ALREADY_PROCESSED);
        }
        String note = "Top-up " + t.getRequestNo() + " (" + t.getPaymentMode() + " " + t.getUtr() + ")"
                + (remarks == null || remarks.isBlank() ? "" : " - " + remarks.trim());
        WalletTransaction txn = walletService.credit(t.getPartnerId(), t.getAmount(), WalletRefType.TOPUP_REQUEST, t.getRequestNo(), note);
        t.setStatus(TopupStatus.APPROVED);
        t.setWalletTransactionId(txn.getId());
        t.setReviewedBy(SecurityUtils.currentUserIdOrNull());
        t.setReviewedAt(Instant.now());
        invoiceService.forTopup(t).ifPresent(inv -> t.setInvoiceId(inv.getId()));
        repository.save(t);
        auditService.log("TOPUP_APPROVE", "TOPUP_REQUEST", t.getRequestNo(), Map.of("status", "PENDING"),
                Map.of("status", "APPROVED", "amount", MoneyUtil.str(t.getAmount()), "txnUid", txn.getUid(),
                        "remarks", remarks == null ? "" : remarks), t.getPartnerId());
        Partner p = partnerRepository.findById(t.getPartnerId()).orElseThrow();
        notifications.notifyPartner(p.getId(), "TOPUP_APPROVED", NotificationService.SUCCESS, "Top-up approved",
                "₹" + MoneyUtil.rupees(t.getAmount()).toPlainString() + " has been added to your wallet (request " + t.getRequestNo()
                        + "). New balance: ₹" + MoneyUtil.rupees(txn.getBalanceAfter()).toPlainString(), "/partner/wallet", p.getEmail());
        TopupRequestDto dto = toDtos(List.of(t), false).get(0);
        return withTxn(dto, walletQueryService.toDtos(List.of(txn)).get(0));
    }

    @Transactional
    public TopupRequestDto reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw AppException.field("reason", "Please give a reason");
        }
        TopupRequest t = repository.lockById(id).orElseThrow(() -> AppException.notFound("Top-up request"));
        if (t.getStatus() != TopupStatus.PENDING) {
            throw new AppException(ErrorCode.TOPUP_ALREADY_PROCESSED);
        }
        t.setStatus(TopupStatus.REJECTED);
        t.setRejectReason(reason.trim());
        t.setReviewedBy(SecurityUtils.currentUserIdOrNull());
        t.setReviewedAt(Instant.now());
        repository.save(t);
        auditService.log("TOPUP_REJECT", "TOPUP_REQUEST", t.getRequestNo(), Map.of("status", "PENDING"),
                Map.of("status", "REJECTED", "reason", reason.trim()), t.getPartnerId());
        Partner p = partnerRepository.findById(t.getPartnerId()).orElseThrow();
        notifications.notifyPartner(p.getId(), "TOPUP_REJECTED", NotificationService.ERROR, "Top-up request rejected",
                "Request " + t.getRequestNo() + " for ₹" + MoneyUtil.rupees(t.getAmount()).toPlainString() + " was rejected. Reason: "
                        + reason.trim(), "/partner/wallet?tab=requests", p.getEmail());
        return toDtos(List.of(t), false).get(0);
    }

    // ------------------------------------------------------------------ mapping

    private List<TopupRequestDto> toDtos(List<TopupRequest> list, boolean unused) {
        Set<Long> partnerIds = list.stream().map(TopupRequest::getPartnerId).collect(Collectors.toSet());
        Map<Long, Partner> partners = partnerRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(Partner::getId, Function.identity()));
        Set<Long> userIds = new HashSet<>();
        Set<Long> fileIds = new HashSet<>();
        list.forEach(t -> {
            if (t.getReviewedBy() != null) {
                userIds.add(t.getReviewedBy());
            }
            if (t.getScreenshotFileId() != null) {
                fileIds.add(t.getScreenshotFileId());
            }
        });
        Map<Long, User> users = userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, StoredFile> files = fileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        return list.stream().map(t -> {
            Partner p = partners.get(t.getPartnerId());
            User r = t.getReviewedBy() == null ? null : users.get(t.getReviewedBy());
            StoredFile f = t.getScreenshotFileId() == null ? null : files.get(t.getScreenshotFileId());
            Invoice inv = invoiceService.find(t.getInvoiceId()).orElse(null);
            return new TopupRequestDto(t.getId(), t.getRequestNo(), p == null ? null : new PartnerRef(p.getId(), p.getBusinessName()),
                    t.getAmount(), t.getPaymentMode().name(), t.getUtr(), t.getPaymentDate(),
                    f == null ? null : "/api/files/" + f.getUid(), f == null ? null : f.getMimeType(), t.getNote(),
                    t.getStatus().name(), t.getRejectReason(), r == null ? null : new ReviewerRef(r.getUid(), r.getName()),
                    t.getReviewedAt(), inv == null ? null : new InvoiceRef(inv.getId(), inv.getInvoiceNo()), t.getCreatedAt(), null);
        }).toList();
    }

    private static TopupRequestDto withTxn(TopupRequestDto d, WalletTxnDto txn) {
        return new TopupRequestDto(d.id(), d.requestNo(), d.partner(), d.amount(), d.paymentMode(), d.utr(), d.paymentDate(),
                d.screenshotUrl(), d.screenshotMimeType(), d.note(), d.status(), d.rejectReason(), d.reviewedBy(), d.reviewedAt(),
                d.invoice(), d.createdAt(), txn);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw AppException.field(field, "Unknown value");
        }
    }
}
