package in.codebasket.cbwa.common.service.registration;

import in.codebasket.cbwa.common.entity.RegistrationPackage;

import in.codebasket.cbwa.common.constants.FormStatus;
import in.codebasket.cbwa.common.dto.FollowUp;
import in.codebasket.cbwa.common.dto.PassLayout;
import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.entity.RegistrationForm;
import in.codebasket.cbwa.common.repository.RegistrationFormRepository;
import in.codebasket.cbwa.common.repository.RegistrationRepository;

import in.codebasket.cbwa.exception.ErrorCode;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.common.constants.FileCategory;
import in.codebasket.cbwa.common.service.storage.FileStorageService;
import in.codebasket.cbwa.common.entity.StoredFile;
import in.codebasket.cbwa.common.repository.StoredFileRepository;
import in.codebasket.cbwa.common.utils.Ulids;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.common.repository.ContactGroupRepository;
import in.codebasket.cbwa.common.service.messaging.MessagePayloadBuilder;
import in.codebasket.cbwa.common.dto.SendMessageRequest;
import in.codebasket.cbwa.common.service.scheduling.ScheduledMessageService;
import in.codebasket.cbwa.common.entity.PhoneNumber;
import in.codebasket.cbwa.common.repository.PhoneNumberRepository;
import in.codebasket.cbwa.common.service.sender.SenderAccessService;
import in.codebasket.cbwa.common.constants.ComponentType;
import in.codebasket.cbwa.common.entity.Template;
import in.codebasket.cbwa.common.repository.TemplateComponentRepository;
import in.codebasket.cbwa.common.repository.TemplateRepository;
import in.codebasket.cbwa.common.constants.TemplateStatus;
import in.codebasket.cbwa.common.dto.ItineraryStop;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** RF1–RF9: the partner's public registration links (forms), pass design and follow-up messages. */
@Service
@RequiredArgsConstructor
public class RegistrationFormService {

    static final String SAMPLE_TO = "919999999999";
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationFormRepository formRepository;
    private final RegistrationRepository registrationRepository;
    private final SenderAccessService senderAccess;
    private final PhoneNumberRepository phoneNumberRepository;
    private final TemplateRepository templateRepository;
    private final TemplateComponentRepository componentRepository;
    private final ContactGroupRepository groupRepository;
    private final MessagePayloadBuilder payloadBuilder;
    private final FileStorageService storage;
    private final StoredFileRepository fileRepository;
    private final PassRenderer renderer;
    private final ScheduledMessageService scheduledService;
    private final AppProperties props;
    private final PartnerPaymentService payments;
    private final in.codebasket.cbwa.common.repository.RegistrationPackageRepository packageRepository;
    private final in.codebasket.cbwa.common.repository.RegistrationItemRepository itemRepository;

    public record SenderRef(Long id, String displayPhoneNumber) {
    }

    public record FileRef(String fileUid, String url) {
    }

    public record FormDto(Long id, String uid, String slug, String publicUrl, String title, String description, SenderRef sender,
                          FormStatus status, Instant closesAt, Integer maxRegistrations, String consentText, String successMessage,
                          boolean collectPhoto, boolean passEnabled, String passDesign, FileRef passDesignFile, PassLayout passLayout,
                          String passPrefix, Long passTemplateId, List<String> passTemplateBody, String passCaption,
                          Long contactGroupId, List<String> contactTags, List<FollowUp> followUps, int registrationsCount,
                          java.math.BigDecimal feeAmount, String feeLabel, boolean paymentsReady, Instant createdAt, Instant updatedAt,
                          List<PackageDto> packages, String termsText, String privacyText, String heroImageUrl, String pageHeading,
                          String pageNote, String ctaLabel, String comboTitle, String comboSubtitle, String comboCodes,
                          java.math.BigDecimal comboMrp, java.math.BigDecimal comboPrice, Instant comboValidUntil, String invoiceName, String passSupportLine, String shareTitle,
                          String shareDescription, String shareImageUrl, String invoiceStyle, String invoiceContact) {
    }

    public record FormRequest(@NotBlank @Size(max = 160) String title, @Size(max = 2000) String description,
                              @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,62}$", message = "Use 3-63 lowercase letters, digits or -") String slug,
                              @NotNull Long senderId, FormStatus status, Instant closesAt, @Min(1) @Max(1_000_000) Integer maxRegistrations,
                              @Size(max = 500) String consentText, @Size(max = 1000) String successMessage, Boolean collectPhoto,
                              Boolean passEnabled, @Size(max = 40) String passDesign, String passDesignFileUid, PassLayout passLayout,
                              @Pattern(regexp = "^[A-Z0-9]{1,10}$", message = "Use up to 10 capital letters or digits") String passPrefix,
                              Long passTemplateId, @Size(max = 10) List<@Size(max = 200) String> passTemplateBody,
                              @Size(max = 1000) String passCaption, Long contactGroupId, @Size(max = 10) List<@Size(max = 40) String> contactTags,
                              @Size(max = 20) List<@Valid FollowUp> followUps,
                              @jakarta.validation.constraints.DecimalMin(value = "0") @jakarta.validation.constraints.DecimalMax("500000")
                              @jakarta.validation.constraints.Digits(integer = 6, fraction = 2) java.math.BigDecimal feeAmount,
                              @Size(max = 120) String feeLabel,
                              @Size(max = 12) List<@Valid PackageRequest> packages,
                              @Size(max = 50000) String termsText, @Size(max = 50000) String privacyText,
                              @Size(max = 500) String heroImageUrl, @Size(max = 160) String pageHeading, @Size(max = 300) String pageNote,
                              @Size(max = 60) String ctaLabel, @Size(max = 160) String comboTitle, @Size(max = 200) String comboSubtitle,
                              @Size(max = 200) String comboCodes, @jakarta.validation.constraints.DecimalMin("0") java.math.BigDecimal comboMrp,
                              @jakarta.validation.constraints.DecimalMin("0") java.math.BigDecimal comboPrice, Instant comboValidUntil,
                              @Size(max = 160) String invoiceName, @Size(max = 120) String passSupportLine,
                              @Size(max = 160) String shareTitle, @Size(max = 300) String shareDescription,
                              @Size(max = 500) String shareImageUrl,
                              @Pattern(regexp = "HFHU", message = "HFHU or empty") String invoiceStyle, @Size(max = 160) String invoiceContact) {
    }

    public record PreviewRequest(String passDesign, String passDesignFileUid, PassLayout passLayout, String name, String mobile,
                                 String passNo) {
    }

    public record BroadcastRequest(@NotNull Instant scheduledAt, @NotNull SendMessageRequest message) {
    }

    public record PackageDto(Long id, String code, String name, String description, String details, String icon, FileRef image,
                             java.math.BigDecimal mrp, java.math.BigDecimal salePrice, boolean mandatory, boolean active, int sortOrder,
                             String passTitle, FileRef passDesign, boolean hasBookings, String imageUrl, boolean issuePass, String passStyle,
                             List<ItineraryStop> itinerary) {
    }

    /** id null = new package. Packages left out of the list are deleted (or switched off when already booked). */
    public record PackageRequest(Long id,
                                 @NotBlank @Pattern(regexp = "^[A-Z0-9]{2,12}$", message = "2-12 capital letters or digits") String code,
                                 @NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
                                 @Size(max = 8000) String details, @Size(max = 40) String icon, String imageFileUid,
                                 @NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("500000") java.math.BigDecimal mrp,
                                 @NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("500000") java.math.BigDecimal salePrice,
                                 Boolean mandatory, Boolean active, Integer sortOrder, @Size(max = 160) String passTitle, String passDesignFileUid,
                                 @Size(max = 500) String imageUrl, Boolean issuePass,
                                 @Pattern(regexp = "TICKET|FORM_DESIGN|ZIYARAT_CARD|HFHU_[1-4]", message = "TICKET, FORM_DESIGN, ZIYARAT_CARD or HFHU_1-4") String passStyle,
                                 @Size(max = 60) List<@Valid ItineraryStop> itinerary) {
    }

    @Transactional(readOnly = true)
    public List<FormDto> list(Long partnerId) {
        return formRepository.findByPartnerIdOrderByIdDesc(partnerId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public FormDto get(Long partnerId, Long id) {
        return toDto(find(partnerId, id));
    }

    @Transactional
    public FormDto create(Long partnerId, FormRequest req) {
        RegistrationForm f = new RegistrationForm();
        f.setUid(Ulids.next());
        f.setPartnerId(partnerId);
        apply(partnerId, f, req);
        formRepository.save(f);
        savePackages(partnerId, f, req.packages());
        return toDto(f);
    }

    @Transactional
    public FormDto update(Long partnerId, Long id, FormRequest req) {
        RegistrationForm f = find(partnerId, id);
        apply(partnerId, f, req);
        formRepository.save(f);
        savePackages(partnerId, f, req.packages());
        return toDto(f);
    }

    private void savePackages(Long partnerId, RegistrationForm f, List<PackageRequest> list) {
        if (list == null) {
            return; // older clients: packages unchanged
        }
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            PackageRequest p = list.get(i);
            if (!codes.add(p.code())) {
                throw AppException.field("packages[" + i + "].code", "Each package needs its own code");
            }
            if (p.salePrice().compareTo(p.mrp()) > 0) {
                throw AppException.field("packages[" + i + "].salePrice", "The sale price cannot be more than the MRP");
            }
        }
        boolean anyPaid = list.stream().anyMatch(p -> p.salePrice().signum() > 0 && (p.active() == null || p.active()));
        if (anyPaid && !payments.ready(partnerId)) {
            throw AppException.field("packages", "Connect your Razorpay account first (Registrations → Online payments)");
        }
        List<RegistrationPackage> existing = packageRepository.findByFormIdOrderBySortOrderAscIdAsc(f.getId());
        java.util.Map<Long, RegistrationPackage> byId = new java.util.HashMap<>();
        existing.forEach(e -> byId.put(e.getId(), e));
        java.util.Set<Long> kept = new java.util.HashSet<>();
        // free the codes of packages that are being renamed or removed before saving, so the unique key never clashes
        for (RegistrationPackage e : existing) {
            boolean stays = list.stream().anyMatch(p -> e.getId().equals(p.id()));
            if (!stays || list.stream().noneMatch(p -> e.getId().equals(p.id()) && p.code().equals(e.getCode()))) {
                e.setCode("~" + e.getId());
                packageRepository.saveAndFlush(e);
            }
        }
        int order = 1;
        for (PackageRequest p : list) {
            RegistrationPackage e = p.id() == null ? null : byId.get(p.id());
            if (p.id() != null && e == null) {
                throw AppException.field("packages", "A package was not found. Please reload the page");
            }
            if (e == null) {
                e = new RegistrationPackage();
                e.setFormId(f.getId());
                e.setPartnerId(partnerId);
            }
            e.setCode(p.code());
            e.setName(p.name().trim());
            e.setDescription(trimToNull(p.description()));
            e.setDetails(trimToNull(p.details()));
            e.setIcon(trimToNull(p.icon()));
            e.setImageFileId(ownFile(partnerId, p.imageFileUid(), "packages"));
            e.setPassDesignFileId(ownFile(partnerId, p.passDesignFileUid(), "packages"));
            e.setMrp(p.mrp().setScale(2, java.math.RoundingMode.HALF_UP));
            e.setSalePrice(p.salePrice().setScale(2, java.math.RoundingMode.HALF_UP));
            e.setMandatory(Boolean.TRUE.equals(p.mandatory()));
            e.setActive(p.active() == null || p.active());
            e.setSortOrder(p.sortOrder() == null ? order : p.sortOrder());
            e.setPassTitle(trimToNull(p.passTitle()));
            e.setImageUrl(safeUrl(p.imageUrl(), "packages[" + (order - 1) + "].imageUrl"));
            e.setIssuePass(p.issuePass() == null || p.issuePass());
            e.setPassStyle(p.passStyle() == null ? "TICKET" : p.passStyle());
            e.setItinerary(cleanItinerary(p.itinerary()));
            kept.add(packageRepository.saveAndFlush(e).getId());
            order++;
        }
        for (RegistrationPackage e : existing) {
            if (!kept.contains(e.getId())) {
                if (itemRepository.existsByPackageId(e.getId())) {
                    e.setActive(false); // already booked: keep for history, hide from the page
                    e.setCode("~X" + e.getId());
                    packageRepository.save(e);
                } else {
                    packageRepository.delete(e);
                }
            }
        }
    }

    /** Images by URL: our own paths (/ziyarat/…, /api/public/…) or https only - never javascript: or data: URLs. */
    /** Trimmed stops; empty rows dropped; a stop without a round keeps the round of the stop before it. */
    private static List<ItineraryStop> cleanItinerary(List<ItineraryStop> in) {
        List<ItineraryStop> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        String round = null;
        for (var s : in) {
            if (s == null || s.place() == null || s.place().isBlank()) {
                continue;
            }
            String r = trimToNull(s.round());
            round = r != null ? r : round;
            out.add(new ItineraryStop(round, s.place().trim(), trimToNull(s.time()), trimToNull(s.note())));
        }
        return out;
    }

    private static String safeUrl(String url, String field) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = url.trim();
        if (!(u.startsWith("/") && !u.startsWith("//")) && !u.startsWith("https://")) {
            throw AppException.field(field, "Use an https:// link or upload the image");
        }
        return u;
    }

    private Long ownFile(Long partnerId, String uid, String field) {
        if (uid == null || uid.isBlank()) {
            return null;
        }
        return fileRepository.findByUid(uid).filter(x -> partnerId.equals(x.getPartnerId())).map(StoredFile::getId)
                .orElseThrow(() -> AppException.field(field, "Uploaded image not found"));
    }

    @Transactional
    public void delete(Long partnerId, Long id) {
        RegistrationForm f = find(partnerId, id);
        if (f.getRegistrationsCount() > 0) {
            throw AppException.conflict("This link already has registrations. Close it instead of deleting");
        }
        formRepository.delete(f);
    }

    @Transactional
    public FileRef uploadDesign(Long partnerId, MultipartFile file) {
        StoredFile f = storage.store(file, FileCategory.TEMPLATE_HEADER, partnerId, IMAGE_TYPES, 5L << 20);
        try {
            BufferedImageCheck.require(PassRenderer.read(storage.readBytes(f)));
        } catch (IllegalArgumentException e) {
            storage.delete(f);
            throw AppException.field("file", "This image could not be opened. Please upload a JPG or PNG");
        }
        return new FileRef(f.getUid(), "/api/files/" + f.getUid());
    }

    /** RF7: renders a sample pass with the given design/layout (used by the layout editor). */
    @Transactional(readOnly = true)
    public byte[] preview(Long partnerId, PreviewRequest req) {
        byte[] design = designBytes(partnerId, req.passDesign(), req.passDesignFileUid());
        PassLayout layout = req.passLayout() != null ? req.passLayout() : PassLayout.harmainDefault();
        return renderer.render(design, layout, new PassRenderer.PassData(
                blankTo(req.name(), "Mohammed Shahid Ansari"), blankTo(req.mobile(), "+91 98765 43210"), blankTo(req.passNo(), "PASS-000001"), null));
    }

    /** RF9: schedules one message for everyone already registered on this link. */
    @Transactional
    public Map<String, Object> broadcast(Long partnerId, Long id, BroadcastRequest req) {
        RegistrationForm f = find(partnerId, id);
        if (req.scheduledAt().isBefore(Instant.now().plusSeconds(55)) || req.scheduledAt().isAfter(Instant.now().plus(java.time.Duration.ofDays(90)))) {
            throw AppException.field("scheduledAt", "Choose a time between 1 minute and 90 days from now");
        }
        PhoneNumber sender = senderAccess.requireUsableSender(partnerId, f.getPhoneNumberId());
        validateMessage(partnerId, sender, req.message(), "message");
        int scheduled = 0;
        int skipped = 0;
        for (Registration r : registrationRepository.findByFormIdOrderByIdAsc(f.getId())) {
            if (r.getPassNo() == null) {
                continue; // unpaid: not registered yet
            }
            var v = Placeholders.of(r.getName(), "+" + r.getPhone(), r.getPassNo());
            if (scheduledService.scheduleForRegistration(partnerId, sender, r.getPhone(),
                    Placeholders.apply(req.message(), sender.getId(), r.getPhone(), v), req.scheduledAt(), r.getId())) {
                scheduled++;
            } else {
                skipped++;
            }
        }
        return Map.of("scheduled", scheduled, "skipped", skipped);
    }

    /** Very large or tiny designs make unreadable passes (and huge files for WhatsApp). */
    private static final class BufferedImageCheck {
        static void require(java.awt.image.BufferedImage img) {
            if (img.getWidth() < 300 || img.getHeight() < 300 || (long) img.getWidth() * img.getHeight() > 25_000_000L) {
                throw new IllegalArgumentException("size");
            }
        }
    }

    byte[] designBytes(Long partnerId, String builtIn, String fileUid) {
        if (fileUid != null && !fileUid.isBlank()) {
            StoredFile f = fileRepository.findByUid(fileUid).filter(x -> partnerId.equals(x.getPartnerId()))
                    .orElseThrow(() -> AppException.field("passDesignFileUid", "Design file not found"));
            return storage.readBytes(f);
        }
        return renderer.builtInDesign(PassRenderer.isBuiltIn(builtIn) ? builtIn : PassRenderer.BUILT_IN_HARMAIN);
    }

    private void apply(Long partnerId, RegistrationForm f, FormRequest req) {
        PhoneNumber sender = senderAccess.requireUsableSender(partnerId, req.senderId());
        f.setPhoneNumberId(sender.getId());
        f.setTitle(req.title().trim());
        f.setDescription(trimToNull(req.description()));
        String slug = req.slug() == null || req.slug().isBlank() ? null : req.slug().trim();
        if (slug == null && f.getSlug() == null) {
            slug = uniqueSlug(req.title());
        }
        if (slug != null && !slug.equals(f.getSlug())) {
            if (formRepository.existsBySlug(slug)) {
                throw AppException.field("slug", "This link name is already taken");
            }
            f.setSlug(slug);
        }
        f.setStatus(req.status() == null ? FormStatus.ACTIVE : req.status());
        f.setClosesAt(req.closesAt());
        f.setMaxRegistrations(req.maxRegistrations());
        f.setConsentText(blankTo(req.consentText(), "I agree to receive my pass and updates on WhatsApp from " + f.getTitle()));
        f.setSuccessMessage(trimToNull(req.successMessage()));
        f.setCollectPhoto(Boolean.TRUE.equals(req.collectPhoto()));
        f.setPassEnabled(req.passEnabled() == null || req.passEnabled());
        f.setPassPrefix(blankTo(req.passPrefix(), "PASS"));
        f.setPassCaption(trimToNull(req.passCaption()));

        if (req.passDesignFileUid() != null && !req.passDesignFileUid().isBlank()) {
            StoredFile d = fileRepository.findByUid(req.passDesignFileUid()).filter(x -> partnerId.equals(x.getPartnerId()))
                    .orElseThrow(() -> AppException.field("passDesignFileUid", "Design file not found"));
            if (req.passLayout() == null || req.passLayout().name() == null || req.passLayout().mobile() == null) {
                throw AppException.field("passLayout", "Mark where the name and mobile number go on your design");
            }
            f.setPassDesignFileId(d.getId());
            f.setPassDesign(null);
        } else {
            f.setPassDesignFileId(null);
            f.setPassDesign(PassRenderer.isBuiltIn(req.passDesign()) ? req.passDesign() : PassRenderer.BUILT_IN_HARMAIN);
        }
        PassLayout layout = req.passLayout() != null ? req.passLayout() : PassLayout.harmainDefault();
        validateLayout(layout);
        f.setPassLayout(layout);

        if (req.contactGroupId() != null) {
            groupRepository.findByIdAndPartnerId(req.contactGroupId(), partnerId)
                    .orElseThrow(() -> AppException.field("contactGroupId", "Contact group not found"));
        }
        f.setContactGroupId(req.contactGroupId());
        f.setContactTags(req.contactTags() == null ? new ArrayList<>() : new ArrayList<>(req.contactTags().stream()
                .map(t -> t.trim().toLowerCase(Locale.ROOT)).filter(t -> !t.isEmpty()).distinct().toList()));

        f.setPassTemplateId(null);
        f.setPassTemplateBody(req.passTemplateBody() == null ? new ArrayList<>() : new ArrayList<>(req.passTemplateBody()));
        if (req.passTemplateId() != null) {
            Template t = templateRepository.findById(req.passTemplateId())
                    .filter(x -> x.getWhatsappAccountId().equals(sender.getWhatsappAccountId())
                            && (x.getPartnerId() == null || x.getPartnerId().equals(partnerId)))
                    .orElseThrow(() -> AppException.field("passTemplateId", "Template not found for this number"));
            if (t.getStatus() != TemplateStatus.APPROVED) {
                throw AppException.field("passTemplateId", "Choose an approved template");
            }
            boolean imageHeader = componentRepository.findByTemplateIdOrderByPositionAsc(t.getId()).stream()
                    .anyMatch(c -> c.getType() == ComponentType.HEADER && "IMAGE".equalsIgnoreCase(c.getFormat()));
            if (!imageHeader) {
                throw AppException.field("passTemplateId", "The pass template needs an IMAGE header (the pass is sent as that image)");
            }
            f.setPassTemplateId(t.getId());
            validateMessage(partnerId, sender, passTemplateMessage(f, sender.getId(), SAMPLE_TO, "https://example.com/pass.jpg", null,
                    Placeholders.of("Sample", "+919999999999", "PASS-000001")), "passTemplateBody");
        }

        List<FollowUp> ups = req.followUps() == null ? List.of() : req.followUps();
        for (int i = 0; i < ups.size(); i++) {
            FollowUp u = ups.get(i);
            if (u.message() == null) {
                throw AppException.field("followUps[" + i + "].message", "Write the message");
            }
            if (u.sendAt() == null && (u.delayMinutes() == null || u.delayMinutes() < 1 || u.delayMinutes() > 90 * 24 * 60)) {
                throw AppException.field("followUps[" + i + "].delayMinutes", "Send between 1 minute and 90 days after registering, or pick a date");
            }
            if ("reaction".equals(u.message().type())) {
                throw AppException.field("followUps[" + i + "].message.type", "Reactions cannot be scheduled");
            }
            validateMessage(partnerId, sender, u.message(), "followUps[" + i + "].message");
        }
        f.setFollowUps(new ArrayList<>(ups));

        if (req.feeAmount() != null && req.feeAmount().signum() > 0) {
            if (req.feeAmount().compareTo(java.math.BigDecimal.ONE) < 0) {
                throw AppException.field("feeAmount", "The fee must be at least ₹1");
            }
            if (!payments.ready(partnerId)) {
                throw AppException.field("feeAmount", "Connect your Razorpay account first (Registrations → Online payments)");
            }
            f.setFeeAmount(req.feeAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        } else {
            f.setFeeAmount(null);
        }
        f.setFeeLabel(trimToNull(req.feeLabel()));
        if (req.termsText() != null) {
            f.setTermsText(trimToNull(req.termsText()));
        }
        if (req.privacyText() != null) {
            f.setPrivacyText(trimToNull(req.privacyText()));
        }
        f.setHeroImageUrl(safeUrl(req.heroImageUrl(), "heroImageUrl"));
        f.setPageHeading(trimToNull(req.pageHeading()));
        f.setPageNote(trimToNull(req.pageNote()));
        f.setCtaLabel(trimToNull(req.ctaLabel()));
        f.setComboTitle(trimToNull(req.comboTitle()));
        f.setComboSubtitle(trimToNull(req.comboSubtitle()));
        String codes = req.comboCodes() == null ? null : String.join(",", java.util.Arrays.stream(req.comboCodes().split(","))
                .map(c -> c.trim().toUpperCase(Locale.ROOT)).filter(c -> !c.isEmpty()).distinct().toList());
        f.setComboCodes(codes == null || codes.isEmpty() ? null : codes);
        f.setComboMrp(req.comboMrp());
        f.setComboPrice(req.comboPrice());
        f.setComboValidUntil(req.comboValidUntil());
        f.setInvoiceName(trimToNull(req.invoiceName()));
        f.setPassSupportLine(trimToNull(req.passSupportLine()));
        f.setShareTitle(trimToNull(req.shareTitle()));
        f.setShareDescription(trimToNull(req.shareDescription()));
        f.setShareImageUrl(safeUrl(req.shareImageUrl(), "shareImageUrl"));
        f.setInvoiceStyle(trimToNull(req.invoiceStyle()));
        f.setInvoiceContact(trimToNull(req.invoiceContact()));
        if (f.getComboCodes() != null && f.getComboPrice() == null) {
            throw AppException.field("comboPrice", "Enter the combo price");
        }
    }

    /** Builds the message for a sample recipient so template/variable/media mistakes show up while saving. */
    private void validateMessage(Long partnerId, PhoneNumber sender, SendMessageRequest m, String field) {
        if (m.type() == null || m.type().isBlank()) {
            throw AppException.field(field + ".type", "Choose the message type");
        }
        try {
            payloadBuilder.build(partnerId, sender, SAMPLE_TO,
                    Placeholders.apply(m, sender.getId(), SAMPLE_TO, Placeholders.of("Sample", "+919999999999", "PASS-000001")));
        } catch (AppException e) {
            if (e.getCode() == ErrorCode.VALIDATION_FAILED || e.getCode() == ErrorCode.BAD_REQUEST) {
                throw AppException.field(field, e.getMessage());
            }
            throw e;
        }
    }

    /** The pass as a template message: image header = the pass, body variables from passTemplateBody. */
    static SendMessageRequest passTemplateMessage(RegistrationForm f, Long senderId, String to, String link, String fileUid,
                                                  Map<String, String> v) {
        var header = new SendMessageRequest.HeaderVariable("image", null, fileUid, null, link, null, null, null, null, null);
        var vars = new SendMessageRequest.TemplateVariables(Placeholders.apply(f.getPassTemplateBody(), v), header, null);
        return new SendMessageRequest(senderId, null, to, "template", null, null, null, null, null, null, null, null,
                new SendMessageRequest.TemplateRef(f.getPassTemplateId(), null, null, vars, null), null);
    }

    private static void validateLayout(PassLayout l) {
        for (PassLayout.Box b : new PassLayout.Box[] {l.name(), l.mobile(), l.photo(), l.passNo()}) {
            if (b != null && (b.x() < 0 || b.y() < 0 || b.w() <= 0 || b.h() <= 0 || b.x() + b.w() > 1.0001 || b.y() + b.h() > 1.0001)) {
                throw AppException.field("passLayout", "A box is outside the design");
            }
        }
        if (l.name() == null || l.mobile() == null) {
            throw AppException.field("passLayout", "Mark where the name and mobile number go on your design");
        }
    }

    private String uniqueSlug(String title) {
        String base = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (base.length() > 40) {
            base = base.substring(0, 40).replaceAll("-+$", "");
        }
        if (base.length() < 3) {
            base = "register";
        }
        String candidate;
        do {
            candidate = base + "-" + Integer.toString(1000 + RANDOM.nextInt(9000));
        } while (formRepository.existsBySlug(candidate));
        return candidate;
    }

    RegistrationForm find(Long partnerId, Long id) {
        return formRepository.findByIdAndPartnerId(id, partnerId).orElseThrow(() -> AppException.notFound("Registration link"));
    }

    FormDto toDto(RegistrationForm f) {
        PhoneNumber pn = phoneNumberRepository.findById(f.getPhoneNumberId()).orElse(null);
        FileRef design = f.getPassDesignFileId() == null ? null : fileRepository.findById(f.getPassDesignFileId())
                .map(x -> new FileRef(x.getUid(), "/api/files/" + x.getUid())).orElse(null);
        return new FormDto(f.getId(), f.getUid(), f.getSlug(), publicUrl(f.getSlug()), f.getTitle(), f.getDescription(),
                pn == null ? null : new SenderRef(pn.getId(), pn.getDisplayPhoneNumber()), f.getStatus(), f.getClosesAt(),
                f.getMaxRegistrations(), f.getConsentText(), f.getSuccessMessage(), f.isCollectPhoto(), f.isPassEnabled(),
                f.getPassDesign(), design, f.getPassLayout(), f.getPassPrefix(), f.getPassTemplateId(), f.getPassTemplateBody(),
                f.getPassCaption(), f.getContactGroupId(), f.getContactTags(), f.getFollowUps(), f.getRegistrationsCount(),
                f.getFeeAmount(), f.getFeeLabel(), payments.ready(f.getPartnerId()), f.getCreatedAt(), f.getUpdatedAt(),
                packageDtos(f), f.getTermsText(), f.getPrivacyText(), f.getHeroImageUrl(), f.getPageHeading(), f.getPageNote(),
                f.getCtaLabel(), f.getComboTitle(), f.getComboSubtitle(), f.getComboCodes(), f.getComboMrp(), f.getComboPrice(),
                f.getComboValidUntil(), f.getInvoiceName(), f.getPassSupportLine(),
                f.getShareTitle(), f.getShareDescription(), f.getShareImageUrl(), f.getInvoiceStyle(), f.getInvoiceContact());
    }

    private List<PackageDto> packageDtos(RegistrationForm f) {
        if (f.getId() == null) {
            return List.of();
        }
        return packageRepository.findByFormIdOrderBySortOrderAscIdAsc(f.getId()).stream()
                .filter(p -> !p.getCode().startsWith("~"))
                .map(p -> new PackageDto(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getDetails(), p.getIcon(),
                        fileRef(p.getImageFileId()), p.getMrp(), p.getSalePrice(), p.isMandatory(), p.isActive(), p.getSortOrder(),
                        p.getPassTitle(), fileRef(p.getPassDesignFileId()), itemRepository.existsByPackageId(p.getId()),
                        p.getImageUrl(), p.isIssuePass(), p.getPassStyle(), p.getItinerary() == null ? List.of() : p.getItinerary()))
                .toList();
    }

    private FileRef fileRef(Long id) {
        return id == null ? null : fileRepository.findById(id).map(x -> new FileRef(x.getUid(), "/api/files/" + x.getUid())).orElse(null);
    }

    String publicUrl(String slug) {
        String base = props.getFrontendUrl() == null ? "" : props.getFrontendUrl().replaceAll("/+$", "");
        return base + "/r/" + slug;
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }
}
