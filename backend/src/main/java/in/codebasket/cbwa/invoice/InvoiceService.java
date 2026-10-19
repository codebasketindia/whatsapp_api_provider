package in.codebasket.cbwa.invoice;

import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.sequence.NumberSequenceService;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.payment.PaymentOrder;
import in.codebasket.cbwa.topup.TopupRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GST invoices for wallet top-ups (D3).
 *  - Online payment: GST is added on top (Q1) -> taxable = base amount, GST = order.gstAmount.
 *  - Manual top-up: the partner already paid `amount`; when GST is enabled we treat that amount as
 *    GST-inclusive (taxable = amount / (1 + gst%), GST = amount - taxable) and still credit the full amount.
 * Intra-state supply (partner state == company state, or partner state unknown) -> CGST + SGST, else IGST.
 * Numbering: {prefix}/{FY}/{000123}, gap-free per financial year (April-March, IST).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    public static final String DESCRIPTION = "WhatsApp messaging services - prepaid wallet credit";

    private final InvoiceRepository repository;
    private final PartnerRepository partnerRepository;
    private final NumberSequenceService sequences;
    private final SettingsService settings;
    private final FileStorageService storage;

    public record PartnerRef(Long id, String businessName, String gstin) {
    }

    public record InvoiceDto(Long id, String invoiceNo, LocalDate invoiceDate, PartnerRef partner, BigDecimal taxableAmount,
                             BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal total, String pdfUrl,
                             Long paymentOrderId, Long topupRequestId) {
    }

    public boolean gstEnabled() {
        return settings.getBool(SettingKeys.GST, "enabled", false);
    }

    public BigDecimal gstPercent() {
        return settings.getDecimal(SettingKeys.GST, "percent", BigDecimal.ZERO);
    }

    /** Invoice for a paid gateway order (idempotent). Runs inside the caller's settlement transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice forPayment(PaymentOrder order) {
        Optional<Invoice> existing = repository.findByPaymentOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Partner p = partner(order.getPartnerId());
        Invoice inv = build(p, MoneyUtil.scale(order.getBaseAmount()), MoneyUtil.scale(order.getGstAmount()));
        inv.setPaymentOrderId(order.getId());
        return saveWithPdf(inv);
    }

    /** Invoice for an approved manual top-up when GST + manual invoicing are enabled; empty otherwise. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Invoice> forTopup(TopupRequest req) {
        if (!gstEnabled() || !settings.getBool(SettingKeys.GST, "invoice_manual_topups", true)) {
            return Optional.empty();
        }
        Optional<Invoice> existing = repository.findByTopupRequestId(req.getId());
        if (existing.isPresent()) {
            return existing;
        }
        BigDecimal pct = gstPercent();
        BigDecimal total = MoneyUtil.rupees(req.getAmount());
        BigDecimal taxable = total.multiply(MoneyUtil.HUNDRED).divide(MoneyUtil.HUNDRED.add(pct), 2, RoundingMode.HALF_UP);
        Invoice inv = build(partner(req.getPartnerId()), MoneyUtil.scale(taxable), MoneyUtil.scale(total.subtract(taxable)));
        inv.setTopupRequestId(req.getId());
        return Optional.of(saveWithPdf(inv));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceDto> list(Long partnerId, LocalDate from, LocalDate to, Integer page, Integer size, boolean adminView) {
        Specification<Invoice> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (partnerId != null) {
                ps.add(cb.equal(root.get("partnerId"), partnerId));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), to));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Invoice> result = repository.findAll(spec, Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Partner> partners = partnerRepository.findAllById(result.getContent().stream().map(Invoice::getPartnerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Partner::getId, Function.identity()));
        return PageResponse.of(result, i -> toDto(i, partners.get(i.getPartnerId()), adminView));
    }

    @Transactional(readOnly = true)
    public InvoiceDto dto(Invoice inv, boolean adminView) {
        return toDto(inv, partnerRepository.findById(inv.getPartnerId()).orElse(null), adminView);
    }

    /** PDF bytes; regenerates (and stores) the file if it is missing. */
    @Transactional
    public byte[] pdf(Long invoiceId, Long partnerScope) {
        Invoice inv = repository.findById(invoiceId).filter(i -> partnerScope == null || partnerScope.equals(i.getPartnerId()))
                .orElseThrow(() -> AppException.notFound("Invoice"));
        if (inv.getFileId() != null) {
            try {
                return storage.readBytes(storage.get(inv.getFileId()));
            } catch (AppException e) {
                log.warn("Invoice {} file missing - regenerating", inv.getInvoiceNo());
            }
        }
        byte[] bytes = render(inv);
        inv.setFileId(store(inv, bytes).getId());
        repository.save(inv);
        return bytes;
    }

    public Optional<Invoice> find(Long id) {
        return id == null ? Optional.empty() : repository.findById(id);
    }

    private InvoiceDto toDto(Invoice i, Partner p, boolean adminView) {
        String url = (adminView ? "/api/admin/invoices/" : "/api/partner/invoices/") + i.getId() + "/pdf";
        return new InvoiceDto(i.getId(), i.getInvoiceNo(), i.getInvoiceDate(),
                new PartnerRef(i.getPartnerId(), p == null ? i.getBillingName() : p.getBusinessName(), i.getGstin()),
                i.getTaxableAmount(), i.getCgstAmount(), i.getSgstAmount(), i.getIgstAmount(), i.getTotalAmount(), url,
                i.getPaymentOrderId(), i.getTopupRequestId());
    }

    private Invoice build(Partner p, BigDecimal taxable, BigDecimal gst) {
        LocalDate today = TimeUtil.todayIst();
        String fy = financialYear(today);
        String prefix = settings.getString(SettingKeys.GST, "invoice_prefix", "CB");
        long n = sequences.next("INVOICE_FY" + fy, prefix);
        Invoice inv = new Invoice();
        inv.setInvoiceNo(prefix + "/" + fy + "/" + String.format("%06d", n));
        inv.setPartnerId(p.getId());
        inv.setInvoiceDate(today);
        inv.setBillingName(p.getBusinessName());
        inv.setGstin(p.getGstin());
        inv.setBillingAddress(address(p));
        String companyState = settings.getString(SettingKeys.COMPANY, "state_code");
        String partnerState = p.getStateCode();
        inv.setPlaceOfSupply(partnerState != null ? partnerState : companyState);
        inv.setTaxableAmount(taxable);
        BigDecimal zero = MoneyUtil.ZERO;
        inv.setCgstAmount(zero);
        inv.setSgstAmount(zero);
        inv.setIgstAmount(zero);
        if (gst.signum() > 0) {
            boolean intraState = partnerState == null || companyState == null || partnerState.equals(companyState);
            if (intraState && "IN".equalsIgnoreCase(p.getCountry() == null ? "IN" : p.getCountry())) {
                BigDecimal half = gst.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
                inv.setCgstAmount(MoneyUtil.scale(half));
                inv.setSgstAmount(MoneyUtil.scale(gst.subtract(half)));
            } else {
                inv.setIgstAmount(MoneyUtil.scale(gst));
            }
        }
        inv.setTotalAmount(MoneyUtil.scale(taxable.add(gst)));
        return inv;
    }

    private Invoice saveWithPdf(Invoice inv) {
        repository.save(inv);
        try {
            inv.setFileId(store(inv, render(inv)).getId());
            repository.save(inv);
        } catch (RuntimeException e) {
            // The invoice record is what matters for accounting; the PDF is regenerated on first download.
            log.warn("Could not render invoice {} PDF now: {}", inv.getInvoiceNo(), e.getMessage());
        }
        return inv;
    }

    private byte[] render(Invoice inv) {
        byte[] logo = null;
        String logoUid = settings.getString(SettingKeys.COMPANY, "logo_file_uid");
        if (logoUid != null) {
            try {
                logo = storage.readBytes(storage.get(logoUid));
            } catch (RuntimeException ignored) {
                // no logo
            }
        }
        InvoicePdfRenderer.Company company = new InvoicePdfRenderer.Company(
                settings.getString(SettingKeys.COMPANY, "legal_name", settings.getString(SettingKeys.COMPANY, "name", "CodeBasket IT Services & Co")),
                settings.getString(SettingKeys.COMPANY, "address"), settings.getString(SettingKeys.COMPANY, "gstin"),
                settings.getString(SettingKeys.COMPANY, "state_code"), settings.getString(SettingKeys.COMPANY, "email"),
                settings.getString(SettingKeys.COMPANY, "phone"), logo);
        return InvoicePdfRenderer.render(inv, company, settings.getString(SettingKeys.GST, "sac_code", "998319"), gstPercent(), DESCRIPTION);
    }

    private StoredFile store(Invoice inv, byte[] bytes) {
        return storage.store(bytes, inv.getInvoiceNo().replace('/', '-') + ".pdf", FileCategory.INVOICE, inv.getPartnerId(),
                Set.of("application/pdf"), 10L * 1024 * 1024);
    }

    private Partner partner(Long id) {
        return partnerRepository.findById(id).orElseThrow(() -> AppException.notFound("Partner"));
    }

    static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }

    private static String address(Partner p) {
        List<String> parts = new ArrayList<>();
        for (String s : new String[]{p.getAddressLine1(), p.getAddressLine2(), p.getCity(),
                p.getState() == null ? null : p.getState() + (p.getPincode() == null ? "" : " - " + p.getPincode())}) {
            if (s != null && !s.isBlank()) {
                parts.add(s.trim());
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }
}
