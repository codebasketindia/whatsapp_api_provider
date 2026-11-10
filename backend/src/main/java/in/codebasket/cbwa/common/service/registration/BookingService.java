package in.codebasket.cbwa.common.service.registration;

import in.codebasket.cbwa.common.constants.FileCategory;
import in.codebasket.cbwa.common.constants.PaymentStatus;
import in.codebasket.cbwa.common.dto.PassLayout;
import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.entity.RegistrationForm;
import in.codebasket.cbwa.common.entity.RegistrationItem;
import in.codebasket.cbwa.common.entity.RegistrationPackage;
import in.codebasket.cbwa.common.entity.StoredFile;
import in.codebasket.cbwa.common.repository.PartnerRepository;
import in.codebasket.cbwa.common.repository.RegistrationFormRepository;
import in.codebasket.cbwa.common.repository.RegistrationItemRepository;
import in.codebasket.cbwa.common.repository.RegistrationPackageRepository;
import in.codebasket.cbwa.common.repository.RegistrationRepository;
import in.codebasket.cbwa.common.repository.StoredFileRepository;
import in.codebasket.cbwa.common.service.storage.FileStorageService;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bookings with packages: price calculation from the database (never from the browser), one pass per paid
 * package with a signed QR code, invoice and pass PDFs, and one-time redemption at the entry.
 * QR content: {frontendUrl}/v/{itemId}.{signature}; the signature is an HMAC over the item, its pass id and the
 * booking, keyed from the server encryption key - it cannot be guessed or altered.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final RegistrationPackageRepository packageRepository;
    private final RegistrationItemRepository itemRepository;
    private final RegistrationRepository registrationRepository;
    private final RegistrationFormRepository formRepository;
    private final PartnerRepository partnerRepository;
    private final StoredFileRepository fileRepository;
    private final FileStorageService storage;
    private final PassRenderer renderer;
    private final BookingDocuments documents;
    private final AppProperties props;

    public record PackageView(Long id, String code, String name, String description, String details, String icon, String imageUrl,
                              BigDecimal mrp, BigDecimal salePrice, boolean mandatory, boolean issuePass) {
    }

    /** Combo offer shown on the page: all these packages together for one price. */
    public record ComboView(String title, String subtitle, List<String> codes, BigDecimal mrp, BigDecimal price, Instant validUntil) {
    }

    public record ItemView(Long id, String name, String passNo, BigDecimal mrp, BigDecimal price, String passUrl, Instant redeemedAt) {
    }

    public record BookingView(String bookingNo, String invoiceNo, BigDecimal mrpTotal, BigDecimal total, List<ItemView> items,
                              String invoiceUrl, String passesPdfUrl, BigDecimal discountAmount, String discountLabel) {
    }

    public record Totals(BigDecimal mrp, BigDecimal total, BigDecimal discount, String discountLabel) {
    }

    public enum VerifyStatus { VALID, ALREADY_REDEEMED, NOT_PAID, INVALID }

    public record VerifyResult(VerifyStatus status, String message, String passNo, String packageName, String name, String mobile,
                               String bookingNo, String formTitle, Instant redeemedAt) {
    }

    // ---------------------------------------------------------------- packages

    public List<RegistrationPackage> activePackages(Long formId) {
        return packageRepository.findByFormIdAndActiveTrueOrderBySortOrderAscIdAsc(formId);
    }

    public List<PackageView> publicPackages(Long formId) {
        return activePackages(formId).stream().map(p -> new PackageView(p.getId(), p.getCode(), p.getName(), p.getDescription(),
                p.getDetails(), p.getIcon(), p.getImageFileId() != null ? fileUrl(p.getImageFileId()) : p.getImageUrl(), p.getMrp(), p.getSalePrice(),
                p.isMandatory(), p.isIssuePass())).toList();
    }

    /** The link's combo offer while it is valid, else null. */
    public ComboView combo(RegistrationForm f) {
        List<String> codes = comboCodes(f);
        if (codes.isEmpty() || f.getComboPrice() == null || (f.getComboValidUntil() != null && f.getComboValidUntil().isBefore(Instant.now()))) {
            return null;
        }
        return new ComboView(f.getComboTitle(), f.getComboSubtitle(), codes, f.getComboMrp(), f.getComboPrice(), f.getComboValidUntil());
    }

    static List<String> comboCodes(RegistrationForm f) {
        if (f.getComboCodes() == null || f.getComboCodes().isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(f.getComboCodes().split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList();
    }

    /**
     * Replaces the (unpaid) booking's items with the mandatory packages plus the chosen ones and returns the
     * totals. Prices come only from the database.
     */
    public Totals selectItems(RegistrationForm f, Registration r, Collection<Long> chosenIds) {
        List<RegistrationPackage> all = activePackages(f.getId());
        Map<Long, RegistrationPackage> byId = all.stream().collect(Collectors.toMap(RegistrationPackage::getId, Function.identity()));
        Set<Long> chosen = new LinkedHashSet<>(chosenIds == null ? List.of() : chosenIds);
        for (Long id : chosen) {
            if (!byId.containsKey(id)) {
                throw AppException.field("packageIds", "One of the chosen packages is not available any more. Please refresh the page");
            }
        }
        List<RegistrationPackage> picked = all.stream().filter(p -> p.isMandatory() || chosen.contains(p.getId())).toList();
        if (picked.isEmpty()) {
            throw AppException.field("packageIds", "Choose at least one package");
        }
        itemRepository.deleteByRegistrationId(r.getId());
        itemRepository.flush();
        BigDecimal mrp = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (RegistrationPackage p : picked) {
            RegistrationItem it = new RegistrationItem();
            it.setRegistrationId(r.getId());
            it.setPackageId(p.getId());
            it.setPartnerId(r.getPartnerId());
            it.setName(p.getName());
            it.setMrp(p.getMrp());
            it.setPrice(p.getSalePrice());
            itemRepository.save(it);
            mrp = mrp.add(p.getMrp());
            total = total.add(p.getSalePrice());
        }
        // Combo offer: when every combo package is chosen, together they cost the combo price.
        BigDecimal discount = BigDecimal.ZERO;
        String label = null;
        ComboView combo = combo(f);
        if (combo != null) {
            java.util.Set<String> pickedCodes = picked.stream().map(RegistrationPackage::getCode).collect(Collectors.toSet());
            if (pickedCodes.containsAll(combo.codes())) {
                BigDecimal comboSum = picked.stream().filter(p -> combo.codes().contains(p.getCode()))
                        .map(RegistrationPackage::getSalePrice).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (comboSum.compareTo(combo.price()) > 0) {
                    discount = comboSum.subtract(combo.price());
                    label = "Combo Discount" + (combo.title() == null ? "" : " (" + combo.title() + ")");
                }
            }
        }
        return new Totals(mrp, total.subtract(discount), discount, label);
    }

    /**
     * Completes a booking (caller's transaction, after payment): invoice number, a pass id and pass image per item.
     * Returns the pass file of the first (mandatory) item, which is also sent on WhatsApp.
     */
    public StoredFile completeItems(RegistrationForm f, Registration r) {
        List<RegistrationItem> items = itemRepository.findByRegistrationIdOrderByIdAsc(r.getId());
        if (items.isEmpty()) {
            return null;
        }
        r.setInvoiceNo("INV-" + r.getPassNo());
        Map<Long, RegistrationPackage> pk = packageRepository.findAllById(items.stream().map(RegistrationItem::getPackageId).toList())
                .stream().collect(Collectors.toMap(RegistrationPackage::getId, Function.identity()));
        Partner partner = partnerRepository.findById(f.getPartnerId()).orElseThrow();
        StoredFile first = null;
        for (RegistrationItem it : items) {
            RegistrationPackage p = pk.get(it.getPackageId());
            if (p != null && !p.isIssuePass()) {
                continue; // fee-only line (e.g. registration fee): on the invoice, no pass
            }
            it.setPassNo(r.getPassNo() + "-" + (p == null ? "P" + it.getPackageId() : p.getCode()));
            itemRepository.saveAndFlush(it); // id + pass id are part of the QR signature
            byte[] jpg = renderItemPass(f, r, it, p, partner);
            StoredFile file = storage.store(jpg, it.getPassNo() + ".jpg", FileCategory.MEDIA_OUTBOUND, f.getPartnerId(), Set.of("image/jpeg"), 5L << 20);
            it.setPassFileId(file.getId());
            itemRepository.save(it);
            if (first == null) {
                first = file;
            }
        }
        return first;
    }

    private byte[] renderItemPass(RegistrationForm f, Registration r, RegistrationItem it, RegistrationPackage p, Partner partner) {
        String qr = qrUrl(it, r);
        String mobile = RegistrationService.formatMobile(r.getPhone());
        if (p != null && "ZIYARAT_CARD".equals(p.getPassStyle())) {
            return renderer.renderZiyaratCard(new PassRenderer.TicketData(partner.getBusinessName(), f.getTitle(),
                    p.getPassTitle() != null ? p.getPassTitle() : passTitle(it.getName()), r.getName(), mobile, r.getPassNo(),
                    it.getPassNo(), qr, f.getPassSupportLine()));
        }
        boolean useFormDesign = p != null && ("FORM_DESIGN".equals(p.getPassStyle()) || (p.isMandatory() && f.isPassEnabled()));
        Long designId = p == null ? null : p.getPassDesignFileId();
        if (designId != null || useFormDesign) {
            PassLayout layout = f.getPassLayout() != null ? f.getPassLayout() : PassLayout.harmainDefault();
            if (layout.qr() == null && f.getPassDesignFileId() == null && designId == null) {
                layout = PassLayout.harmainDefault();
            }
            if (layout.qr() != null) {
                byte[] design = designId != null ? storage.readBytes(fileRepository.findById(designId).orElseThrow())
                        : f.getPassDesignFileId() != null ? storage.readBytes(fileRepository.findById(f.getPassDesignFileId()).orElseThrow())
                        : renderer.builtInDesign(PassRenderer.isBuiltIn(f.getPassDesign()) ? f.getPassDesign() : PassRenderer.BUILT_IN_HARMAIN);
                return renderer.render(design, layout, new PassRenderer.PassData(r.getName(), mobile, it.getPassNo(), null, qr));
            }
        }
        return renderer.renderTicket(new PassRenderer.TicketData(partner.getBusinessName(), f.getTitle(),
                p != null && p.getPassTitle() != null ? p.getPassTitle() : passTitle(it.getName()), r.getName(), mobile, r.getPassNo(),
                it.getPassNo(), qr));
    }

    // ---------------------------------------------------------------- views & downloads

    public BookingView view(Registration r) {
        List<RegistrationItem> items = itemRepository.findByRegistrationIdOrderByIdAsc(r.getId());
        if (items.isEmpty()) {
            return null;
        }
        boolean done = r.getPassNo() != null && r.getPaymentStatus() != PaymentStatus.PENDING && r.getPaymentStatus() != PaymentStatus.FAILED;
        String base = "/api/public/bookings/" + r.getUid();
        List<ItemView> views = new ArrayList<>();
        BigDecimal mrp = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (RegistrationItem it : items) {
            if (it.getPassNo() == null && done && it.getPrice().signum() == 0) {
                continue;
            }
            views.add(new ItemView(it.getId(), it.getName(), it.getPassNo(), it.getMrp(), it.getPrice(),
                    done && it.getPassFileId() != null ? base + "/passes/" + it.getId() + ".jpg" : null, it.getRedeemedAt()));
            mrp = mrp.add(it.getMrp());
            total = total.add(it.getPrice());
        }
        BigDecimal discount = r.getDiscountAmount() == null ? BigDecimal.ZERO : r.getDiscountAmount();
        boolean anyPass = views.stream().anyMatch(v -> v.passUrl() != null);
        return new BookingView(r.getPassNo(), r.getInvoiceNo(), mrp, total.subtract(discount), views,
                done ? base + "/invoice.pdf" : null, done && anyPass ? base + "/passes.pdf" : null,
                discount.signum() > 0 ? discount : null, r.getDiscountLabel());
    }

    @Transactional(readOnly = true)
    public byte[] itemPass(String uid, Long itemId) {
        Registration r = paidBooking(uid);
        RegistrationItem it = itemRepository.findById(itemId).filter(x -> x.getRegistrationId().equals(r.getId()) && x.getPassFileId() != null)
                .orElseThrow(() -> AppException.notFound("Pass"));
        return storage.readBytes(fileRepository.findById(it.getPassFileId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public byte[] invoicePdf(String uid) {
        Registration r = paidBooking(uid);
        RegistrationForm f = formRepository.findById(r.getFormId()).orElseThrow();
        return documents.invoice(partnerRepository.findById(r.getPartnerId()).orElseThrow(), f, r,
                itemRepository.findByRegistrationIdOrderByIdAsc(r.getId()));
    }

    @Transactional(readOnly = true)
    public byte[] passesPdf(String uid) {
        Registration r = paidBooking(uid);
        List<byte[]> jpgs = itemRepository.findByRegistrationIdOrderByIdAsc(r.getId()).stream().filter(i -> i.getPassFileId() != null)
                .map(i -> storage.readBytes(fileRepository.findById(i.getPassFileId()).orElseThrow())).toList();
        if (jpgs.isEmpty()) {
            throw AppException.notFound("Pass");
        }
        return documents.passes(jpgs);
    }

    private Registration paidBooking(String uid) {
        Registration r = registrationRepository.findByUid(uid).orElseThrow(() -> AppException.notFound("Booking"));
        if (r.getPassNo() == null || r.getPaymentStatus() == PaymentStatus.PENDING || r.getPaymentStatus() == PaymentStatus.FAILED) {
            throw AppException.notFound("Booking");
        }
        return r;
    }

    // ---------------------------------------------------------------- QR verification

    public String qrUrl(RegistrationItem it, Registration r) {
        String base = props.getFrontendUrl() == null ? "" : props.getFrontendUrl().replaceAll("/+$", "");
        return base + "/v/" + token(it, r);
    }

    String token(RegistrationItem it, Registration r) {
        return it.getId() + "." + sign(it.getId() + "|" + it.getPassNo() + "|" + r.getUid());
    }

    /** Public check (scanning with any phone camera): validity and the package, never redeems. */
    @Transactional(readOnly = true)
    public VerifyResult check(String token) {
        return verify(token, null);
    }

    /** Partner scanner: full details, only for the partner's own passes. */
    @Transactional(readOnly = true)
    public VerifyResult verify(String token, Long partnerId) {
        Resolved x = resolve(token, partnerId);
        if (x == null) {
            return new VerifyResult(VerifyStatus.INVALID, "This pass is not valid", null, null, null, null, null, null, null);
        }
        return result(x, x.item().getRedeemedAt() != null ? VerifyStatus.ALREADY_REDEEMED : x.paid() ? VerifyStatus.VALID : VerifyStatus.NOT_PAID,
                partnerId == null);
    }

    /** Marks the pass used. A second scan answers ALREADY_REDEEMED with the first time it was used. */
    @Transactional
    public VerifyResult redeem(String token, Long partnerId, Long userId) {
        Resolved x = resolve(token, partnerId);
        if (x == null) {
            return new VerifyResult(VerifyStatus.INVALID, "This pass is not valid", null, null, null, null, null, null, null);
        }
        if (!x.paid()) {
            return result(x, VerifyStatus.NOT_PAID, false);
        }
        int n = itemRepository.redeem(x.item().getId(), Instant.now(), userId);
        RegistrationItem fresh = itemRepository.findById(x.item().getId()).orElseThrow();
        Resolved y = new Resolved(fresh, x.registration(), x.form(), true);
        return result(y, n == 1 ? VerifyStatus.VALID : VerifyStatus.ALREADY_REDEEMED, false);
    }

    private record Resolved(RegistrationItem item, Registration registration, RegistrationForm form, boolean paid) {
    }

    private Resolved resolve(String token, Long partnerId) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        int slash = t.lastIndexOf("/v/");
        if (slash >= 0) {
            t = t.substring(slash + 3);
        }
        int dot = t.indexOf('.');
        if (dot <= 0 || t.length() > 120) {
            return null;
        }
        long id;
        try {
            id = Long.parseLong(t.substring(0, dot));
        } catch (NumberFormatException e) {
            return null;
        }
        RegistrationItem it = itemRepository.findById(id).orElse(null);
        if (it == null || it.getPassNo() == null || (partnerId != null && !partnerId.equals(it.getPartnerId()))) {
            return null;
        }
        Registration r = registrationRepository.findById(it.getRegistrationId()).orElse(null);
        if (r == null || !MessageDigest.isEqual(token(it, r).getBytes(StandardCharsets.UTF_8), t.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        RegistrationForm f = formRepository.findById(r.getFormId()).orElse(null);
        boolean paid = r.getPaymentStatus() == PaymentStatus.PAID || r.getPaymentStatus() == PaymentStatus.NOT_REQUIRED;
        return new Resolved(it, r, f, paid);
    }

    private VerifyResult result(Resolved x, VerifyStatus status, boolean publicView) {
        String msg = switch (status) {
            case VALID -> "Valid pass";
            case ALREADY_REDEEMED -> "This pass was already used";
            case NOT_PAID -> "Payment for this booking is not complete";
            case INVALID -> "This pass is not valid";
        };
        Registration r = x.registration();
        String name = publicView ? mask(r.getName()) : r.getName();
        String mobile = publicView ? null : "+" + r.getPhone();
        return new VerifyResult(status, msg, x.item().getPassNo(), x.item().getName(), name, mobile, r.getPassNo(),
                x.form() == null ? null : x.form().getTitle(), x.item().getRedeemedAt());
    }

    private static String mask(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (!w.isEmpty()) {
                sb.append(sb.length() == 0 ? "" : " ").append(w.charAt(0)).append("*".repeat(Math.max(1, Math.min(5, w.length() - 1))));
            }
        }
        return sb.toString();
    }

    private String sign(String data) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(("cbwa-pass-qr:" + props.getSecurity().getEncryptionKey())
                    .getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, 22);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record Image(byte[] bytes, String mimeType) {
    }

    /** Package pictures are public, but only files actually used by a package are served. */
    @Transactional(readOnly = true)
    public Image packageImage(String fileUid) {
        StoredFile f = fileRepository.findByUid(fileUid).filter(x -> packageRepository.existsByImageFileId(x.getId())
                        || packageRepository.existsByImageUrlContaining(fileUid) || formRepository.existsByHeroImageUrlContaining(fileUid))
                .orElseThrow(() -> AppException.notFound("Image"));
        return new Image(storage.readBytes(f), f.getMimeType());
    }

    private String fileUrl(Long fileId) {
        return fileRepository.findById(fileId).map(f -> "/api/public/package-images/" + f.getUid()).orElse(null);
    }

    /** "X" -> "X Pass", but a name that already ends in "Pass" stays as it is. */
    static String passTitle(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith("pass") ? name : name + " Pass";
    }
}
