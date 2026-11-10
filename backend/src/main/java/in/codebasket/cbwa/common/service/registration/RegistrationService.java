package in.codebasket.cbwa.common.service.registration;

import in.codebasket.cbwa.common.constants.FormStatus;
import in.codebasket.cbwa.common.dto.FollowUp;
import in.codebasket.cbwa.common.dto.PassLayout;
import in.codebasket.cbwa.common.dto.RegistrationCreatedEvent;
import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.entity.RegistrationForm;

import in.codebasket.cbwa.common.constants.PassStatus;
import in.codebasket.cbwa.common.constants.PaymentStatus;
import in.codebasket.cbwa.common.repository.RegistrationFormRepository;
import in.codebasket.cbwa.common.repository.RegistrationRepository;

import in.codebasket.cbwa.exception.ErrorCode;
import in.codebasket.cbwa.common.dto.PageResponse;
import in.codebasket.cbwa.common.utils.Paging;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.common.constants.FileCategory;
import in.codebasket.cbwa.common.service.storage.FileStorageService;
import in.codebasket.cbwa.common.entity.StoredFile;
import in.codebasket.cbwa.common.repository.StoredFileRepository;
import in.codebasket.cbwa.common.utils.PhoneUtil;
import in.codebasket.cbwa.common.utils.Ulids;
import in.codebasket.cbwa.common.entity.Contact;
import in.codebasket.cbwa.common.repository.ContactRepository;
import in.codebasket.cbwa.common.constants.ContactSource;
import in.codebasket.cbwa.common.repository.OptOutRepository;
import in.codebasket.cbwa.common.repository.ConversationWindowRepository;
import in.codebasket.cbwa.common.entity.Message;
import in.codebasket.cbwa.common.repository.MessageRepository;
import in.codebasket.cbwa.common.constants.MessageSource;
import in.codebasket.cbwa.common.dto.OutboundCommand;
import in.codebasket.cbwa.common.service.messaging.OutboundMessageService;
import in.codebasket.cbwa.common.dto.SendMessageRequest;
import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.repository.PartnerRepository;
import in.codebasket.cbwa.common.constants.PartnerStatus;
import in.codebasket.cbwa.common.entity.PhoneNumber;
import in.codebasket.cbwa.common.repository.PhoneNumberRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Public registration (PR1–PR3) and the free pass: the pass image is generated while registering and
 * sent on WhatsApp right after the transaction commits - as the approved template when one is set,
 * else as a normal image when the chat window is open, else when the person taps "Get my pass" and
 * sends the claim code (which opens the window).
 */
@Slf4j
@Service
public class RegistrationService {

    public static final java.util.regex.Pattern CLAIM = java.util.regex.Pattern.compile("(?i)\\bPASS[\\s-]*([A-HJ-NP-Z2-9]{6})\\b");
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationFormRepository formRepository;
    private final RegistrationRepository repository;
    private final RegistrationFormService formService;
    private final PassRenderer renderer;
    private final FileStorageService storage;
    private final StoredFileRepository fileRepository;
    private final ContactRepository contactRepository;
    private final OptOutRepository optOutRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerRepository partnerRepository;
    private final ConversationWindowRepository windowRepository;
    private final MessageRepository messageRepository;
    private final OutboundMessageService outbound;
    private final in.codebasket.cbwa.common.service.scheduling.ScheduledMessageService scheduledService;
    private final PartnerPaymentService payments;
    private final BookingService booking;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public RegistrationService(RegistrationFormRepository formRepository, RegistrationRepository repository,
                               RegistrationFormService formService, PassRenderer renderer, FileStorageService storage,
                               StoredFileRepository fileRepository, ContactRepository contactRepository,
                               OptOutRepository optOutRepository, PhoneNumberRepository phoneNumberRepository,
                               PartnerRepository partnerRepository, ConversationWindowRepository windowRepository,
                               MessageRepository messageRepository, OutboundMessageService outbound,
                               in.codebasket.cbwa.common.service.scheduling.ScheduledMessageService scheduledService,
                               ApplicationEventPublisher events, JdbcTemplate jdbc, PlatformTransactionManager txManager,
                               PartnerPaymentService payments, BookingService booking) {
        this.payments = payments;
        this.booking = booking;
        this.formRepository = formRepository;
        this.repository = repository;
        this.formService = formService;
        this.renderer = renderer;
        this.storage = storage;
        this.fileRepository = fileRepository;
        this.contactRepository = contactRepository;
        this.optOutRepository = optOutRepository;
        this.phoneNumberRepository = phoneNumberRepository;
        this.partnerRepository = partnerRepository;
        this.windowRepository = windowRepository;
        this.messageRepository = messageRepository;
        this.outbound = outbound;
        this.scheduledService = scheduledService;
        this.events = events;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    public record PublicFormDto(String slug, String title, String description, String businessName, String consentText,
                                boolean collectPhoto, boolean passEnabled, boolean open, String closedReason,
                                java.math.BigDecimal feeAmount, String feeLabel, List<BookingService.PackageView> packages,
                                boolean hasTerms, boolean hasPrivacy, String heroImageUrl, String pageHeading, String pageNote,
                                String ctaLabel, BookingService.ComboView combo) {
    }

    public record LegalDto(String formTitle, String businessName, String title, String text) {
    }

    public record RegisterResult(String uid, String name, String passNo, String passUrl, PassStatus passStatus,
                                 String whatsappLink, String successMessage, PaymentStatus paymentStatus,
                                 java.math.BigDecimal amount, PartnerPaymentService.Checkout payment, String mobile,
                                 BookingService.BookingView booking) {
    }

    public record RegistrationDto(String uid, Long formId, String formTitle, String passNo, String name, String phone,
                                  PassStatus passStatus, String passError, String passUrl, String photoUrl, String messageUid,
                                  Long contactId, PaymentStatus paymentStatus, java.math.BigDecimal amount, String paymentId,
                                  String paymentMethod, Instant paidAt, Instant createdAt, BookingService.BookingView booking) {
    }

    // ---------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public PublicFormDto publicForm(String slug) {
        RegistrationForm f = formRepository.findBySlug(slug).orElseThrow(() -> AppException.notFound("Registration link"));
        Partner p = partnerRepository.findById(f.getPartnerId()).orElseThrow(() -> AppException.notFound("Registration link"));
        String closed = closedReason(f, p);
        if (closed == null && paid(f) && !payments.ready(f.getPartnerId())) {
            closed = "Online payment is not available for this registration right now";
        }
        List<BookingService.PackageView> packages = booking.publicPackages(f.getId());
        return new PublicFormDto(f.getSlug(), f.getTitle(), f.getDescription(), p.getBusinessName(), f.getConsentText(),
                f.isCollectPhoto(), f.isPassEnabled(), closed == null, closed,
                packages.isEmpty() && paid(f) ? f.getFeeAmount() : null, f.getFeeLabel(), packages,
                f.getTermsText() != null && !f.getTermsText().isBlank(), f.getPrivacyText() != null && !f.getPrivacyText().isBlank(),
                f.getHeroImageUrl(), f.getPageHeading(), f.getPageNote(), f.getCtaLabel(), booking.combo(f));
    }

    /** PR5: terms or privacy text of a link, shown on its own page. */
    @Transactional(readOnly = true)
    public LegalDto legal(String slug, String kind) {
        RegistrationForm f = formRepository.findBySlug(slug).orElseThrow(() -> AppException.notFound("Registration link"));
        String text = "privacy".equals(kind) ? f.getPrivacyText() : "terms".equals(kind) ? f.getTermsText() : null;
        if (text == null || text.isBlank()) {
            throw AppException.notFound("Page");
        }
        String business = partnerRepository.findById(f.getPartnerId()).map(Partner::getBusinessName).orElse(null);
        return new LegalDto(f.getTitle(), business, "privacy".equals(kind) ? "Privacy Policy" : "Terms & Conditions", text);
    }

    /**
     * PR2. Free link: the registration is completed at once (pass, contact, follow-ups). Paid link: the
     * registration is saved as PENDING, a Razorpay order is created in the partner's account and returned
     * for the browser checkout - nothing else happens until the payment is verified (PR4 / webhook / job).
     */
    public RegisterResult register(String slug, String rawName, String rawMobile, Boolean consent, MultipartFile photo,
                                   String ip, String userAgent) {
        return register(slug, rawName, rawMobile, consent, photo, ip, userAgent, null);
    }

    public RegisterResult register(String slug, String rawName, String rawMobile, Boolean consent, MultipartFile photo,
                                   String ip, String userAgent, List<Long> packageIds) {
        RegisterResult prepared = tx.execute(s -> prepare(slug, rawName, rawMobile, consent, photo, ip, userAgent, packageIds));
        if (prepared.paymentStatus() != PaymentStatus.PENDING) {
            return prepared;
        }
        Registration r = repository.findByUid(prepared.uid()).orElseThrow();
        RegistrationForm f = formRepository.findById(r.getFormId()).orElseThrow();
        long paise = r.getAmount().movePointRight(2).longValueExact();
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("registration", r.getUid());
        notes.put("form", f.getSlug());
        notes.put("mobile", "+" + r.getPhone());
        PartnerPaymentService.Checkout checkout = payments.createOrder(f.getPartnerId(), paise, "REG-" + r.getUid().substring(14), notes,
                partnerRepository.findById(f.getPartnerId()).map(Partner::getBusinessName).orElse(f.getTitle()),
                f.getFeeLabel() != null ? f.getFeeLabel() : f.getTitle());
        tx.executeWithoutResult(s -> repository.findById(r.getId()).ifPresent(x -> x.setRazorpayOrderId(checkout.orderId())));
        return new RegisterResult(prepared.uid(), prepared.name(), null, null, PassStatus.AWAITING_PAYMENT, null, f.getSuccessMessage(),
                PaymentStatus.PENDING, r.getAmount(), checkout, "+" + r.getPhone(), prepared.booking());
    }

    private RegisterResult prepare(String slug, String rawName, String rawMobile, Boolean consent, MultipartFile photo,
                                   String ip, String userAgent, List<Long> packageIds) {
        RegistrationForm found = formRepository.findBySlug(slug).orElseThrow(() -> AppException.notFound("Registration link"));
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 80) {
            throw AppException.field("name", "Enter your full name (2-80 letters)");
        }
        if (!name.matches("^[\\p{L}\\p{M} .'-]+$")) {
            throw AppException.field("name", "Use letters only in the name");
        }
        String phone = PhoneUtil.parse(rawMobile == null ? "" : rawMobile, "IN").map(PhoneUtil.Parsed::digits)
                .orElseThrow(() -> AppException.field("mobile", "Enter a valid WhatsApp mobile number"));
        if (!Boolean.TRUE.equals(consent)) {
            throw AppException.field("consent", "Please agree to receive your pass on WhatsApp");
        }
        // Lock the form: keeps the count, the seat limit and pass numbers exact under parallel sign-ups.
        RegistrationForm f = formRepository.lockById(found.getId()).orElseThrow();
        Partner partner = partnerRepository.findById(f.getPartnerId()).orElseThrow();
        String closed = closedReason(f, partner);
        boolean withPackages = !booking.activePackages(f.getId()).isEmpty();
        if (closed == null && (withPackages ? packagesCost(f) : paid(f)) && !payments.ready(f.getPartnerId())) {
            closed = "Online payment is not available for this registration right now";
        }
        if (closed != null) {
            throw new AppException(ErrorCode.CONFLICT, closed);
        }
        Registration r = repository.findByFormIdAndPhone(f.getId(), phone).orElse(null);
        if (r != null && (r.getPaymentStatus() == PaymentStatus.PAID || r.getPaymentStatus() == PaymentStatus.NOT_REQUIRED)) {
            throw new AppException(ErrorCode.CONFLICT, "This mobile number is already registered",
                    null, List.of(new in.codebasket.cbwa.common.dto.FieldErrorDto("mobile", "This mobile number is already registered")));
        }
        if (r == null) {
            r = new Registration();
            r.setUid(Ulids.next());
            r.setFormId(f.getId());
            r.setPartnerId(f.getPartnerId());
            r.setPhone(phone);
            r.setClaimCode(newClaimCode());
        }
        // An unpaid earlier attempt is reused: same person trying again with a new order.
        r.setName(name);
        r.setConsentAt(Instant.now());
        r.setIp(ip);
        r.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length())));
        if (photo != null && !photo.isEmpty()) {
            if (!f.isCollectPhoto()) {
                throw AppException.field("photo", "This form does not take a photo");
            }
            StoredFile pf = storage.store(photo, FileCategory.PROFILE_PHOTO, f.getPartnerId(), PHOTO_TYPES, 5L << 20);
            r.setPhotoFileId(pf.getId());
        }
        BookingService.Totals totals = null;
        if (withPackages) {
            if (r.getPassStatus() == null) {
                r.setPassStatus(PassStatus.AWAITING_PAYMENT);
            }
            try {
                repository.saveAndFlush(r);
            } catch (DataIntegrityViolationException e) {
                throw new AppException(ErrorCode.CONFLICT, "This mobile number is already registered");
            }
            totals = booking.selectItems(f, r, packageIds);
            r.setMrpTotal(totals.mrp());
            r.setDiscountAmount(totals.discount().signum() > 0 ? totals.discount() : null);
            r.setDiscountLabel(totals.discountLabel());
        }
        if (withPackages ? totals.total().signum() > 0 : paid(f)) {
            r.setPaymentStatus(PaymentStatus.PENDING);
            r.setPassStatus(PassStatus.AWAITING_PAYMENT);
            r.setAmount(withPackages ? totals.total() : f.getFeeAmount());
            r.setRazorpayOrderId(null);
            try {
                repository.saveAndFlush(r);
            } catch (DataIntegrityViolationException e) {
                throw new AppException(ErrorCode.CONFLICT, "This mobile number is already registered");
            }
            return new RegisterResult(r.getUid(), r.getName(), null, null, r.getPassStatus(), null, f.getSuccessMessage(),
                    PaymentStatus.PENDING, r.getAmount(), null, "+" + phone, booking.view(r));
        }
        if (withPackages) {
            r.setAmount(BigDecimal.ZERO);
        }
        r.setPaymentStatus(PaymentStatus.NOT_REQUIRED);
        r.setPassStatus(f.isPassEnabled() ? PassStatus.PENDING : PassStatus.DISABLED);
        try {
            repository.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.CONFLICT, "This mobile number is already registered");
        }
        complete(f, r);
        return result(f, r);
    }

    /**
     * Finishes a registration (inside the caller's transaction, form row locked): pass number, contact,
     * opt-in, pass image, follow-ups, counter - and the pass is sent once the transaction commits.
     */
    private void complete(RegistrationForm f, Registration r) {
        r.setPassNo(f.getPassPrefix() + "-" + String.format("%06d", f.getRegistrationsCount() + 1));
        r.setContactId(upsertContact(f, r.getName(), r.getPhone()));
        // Registering with consent is an explicit opt-in.
        optOutRepository.findByPartnerIdAndPhone(f.getPartnerId(), r.getPhone()).ifPresent(optOutRepository::delete);
        StoredFile bookingPass = booking.completeItems(f, r);
        if (bookingPass != null) {
            r.setPassFileId(bookingPass.getId()); // the first (mandatory) package pass is also sent on WhatsApp
            r.setPassStatus(PassStatus.PENDING);
        } else if (f.isPassEnabled()) {
            r.setPassFileId(renderPass(f, r).getId());
            r.setPassStatus(PassStatus.PENDING);
        } else {
            r.setPassStatus(PassStatus.DISABLED);
        }
        repository.saveAndFlush(r);
        f.setRegistrationsCount(f.getRegistrationsCount() + 1);
        formRepository.save(f);
        scheduleFollowUps(f, r);
        events.publishEvent(new RegistrationCreatedEvent(r.getId()));
    }

    private RegisterResult result(RegistrationForm f, Registration r) {
        PhoneNumber sender = phoneNumberRepository.findById(f.getPhoneNumberId()).orElse(null);
        boolean done = r.getPassNo() != null;
        return new RegisterResult(r.getUid(), r.getName(), r.getPassNo(), done && r.getPassFileId() != null ? passUrl(r) : null,
                r.getPassStatus(),
                !done || sender == null || sender.getWaNumber() == null ? null
                        : "https://wa.me/" + sender.getWaNumber() + "?text=" + java.net.URLEncoder.encode(
                        "Hi, please send my pass. PASS-" + r.getClaimCode(), java.nio.charset.StandardCharsets.UTF_8),
                f.getSuccessMessage(), r.getPaymentStatus(), r.getAmount(), null, "+" + r.getPhone(), booking.view(r));
    }

    /**
     * PR4: the browser's Razorpay success callback. The signature (HMAC of order|payment with the partner's
     * key secret) proves the payment; the order must be the one created for this registration.
     */
    public RegisterResult verifyPayment(String slug, String uid, String orderId, String paymentId, String signature) {
        Registration r = repository.findByUid(uid).orElseThrow(() -> AppException.notFound("Registration"));
        RegistrationForm f = formRepository.findById(r.getFormId()).filter(x -> x.getSlug().equals(slug))
                .orElseThrow(() -> AppException.notFound("Registration"));
        if (r.getPaymentStatus() == PaymentStatus.PAID) {
            return result(f, r);
        }
        if (orderId == null || !orderId.equals(r.getRazorpayOrderId())
                || !payments.verifyCheckoutSignature(f.getPartnerId(), orderId, paymentId, signature)) {
            throw new AppException(ErrorCode.PAYMENT_SIGNATURE_INVALID, "We could not confirm this payment. If money was taken, it will be confirmed in a few minutes");
        }
        markPaid(r.getId(), paymentId, null);
        return tx.execute(s -> result(formRepository.findById(f.getId()).orElseThrow(), repository.findById(r.getId()).orElseThrow()));
    }

    /** Idempotent: completes the registration the first time its payment is confirmed. Returns true if it did. */
    public boolean markPaid(Long registrationId, String paymentId, String method) {
        Long formId = repository.findById(registrationId).map(Registration::getFormId).orElseThrow();
        Boolean done = tx.execute(s -> {
            // Same lock order as registering (form first) so the two can never deadlock.
            RegistrationForm f = formRepository.lockById(formId).orElseThrow();
            Registration r = repository.lockById(registrationId).orElseThrow();
            if (r.getPaymentStatus() == PaymentStatus.PAID) {
                return false;
            }
            r.setPaymentStatus(PaymentStatus.PAID);
            r.setRazorpayPaymentId(paymentId);
            r.setPaymentMethod(method);
            r.setPaidAt(Instant.now());
            complete(f, r);
            return true;
        });
        if (Boolean.TRUE.equals(done)) {
            log.info("Registration {} paid ({})", registrationId, paymentId);
        }
        return Boolean.TRUE.equals(done);
    }

    /** Razorpay event from the partner's account (payment.captured / order.paid / payment.failed). */
    public String handlePartnerEvent(Long partnerId, JsonNode event) {
        String type = event.path("event").asText("");
        JsonNode pay = event.path("payload").path("payment").path("entity");
        String orderId = pay.path("order_id").asText(null);
        if (orderId == null) {
            orderId = event.path("payload").path("order").path("entity").path("id").asText(null);
        }
        if (orderId == null) {
            return "IGNORED";
        }
        Registration r = repository.findByRazorpayOrderId(orderId).filter(x -> x.getPartnerId().equals(partnerId)).orElse(null);
        if (r == null) {
            return "IGNORED";
        }
        switch (type) {
            case "payment.captured", "order.paid" -> {
                markPaid(r.getId(), pay.path("id").asText(null), pay.path("method").asText(null));
                return "PROCESSED";
            }
            case "payment.failed" -> {
                tx.executeWithoutResult(s -> repository.findById(r.getId()).filter(x -> x.getPaymentStatus() == PaymentStatus.PENDING)
                        .ifPresent(x -> x.setPaymentStatus(PaymentStatus.FAILED)));
                return "PROCESSED";
            }
            default -> {
                return "IGNORED";
            }
        }
    }

    /** Asks Razorpay about an unpaid registration's order; completes it if a payment was captured. */
    public PaymentStatus checkPayment(Registration r) {
        if (r.getPaymentStatus() == PaymentStatus.PAID || r.getRazorpayOrderId() == null) {
            return r.getPaymentStatus();
        }
        try {
            for (var p : payments.orderPayments(r.getPartnerId(), r.getRazorpayOrderId())) {
                if (p.state() == in.codebasket.cbwa.common.service.payment.PaymentGateway.PaymentState.CAPTURED) {
                    markPaid(r.getId(), p.id(), p.method());
                    return PaymentStatus.PAID;
                }
            }
        } catch (RuntimeException e) {
            log.info("Payment check for registration {} failed: {}", r.getId(), e.getMessage());
        }
        return repository.findById(r.getId()).map(Registration::getPaymentStatus).orElse(r.getPaymentStatus());
    }

    /** RG4: partner asks to re-check one registration's payment. */
    public RegistrationDto checkPayment(Long partnerId, String uid) {
        Registration r = repository.findByUidAndPartnerId(uid, partnerId).orElseThrow(() -> AppException.notFound("Registration"));
        if (r.getPaymentStatus() == PaymentStatus.NOT_REQUIRED) {
            throw AppException.conflict("This registration is free");
        }
        checkPayment(r);
        return tx.execute(s -> dto(repository.findById(r.getId()).orElseThrow()));
    }

    /** A link with packages costs money when any active package has a price. */
    private boolean packagesCost(RegistrationForm f) {
        return booking.activePackages(f.getId()).stream().anyMatch(p -> p.getSalePrice().signum() > 0);
    }

    private static boolean paid(RegistrationForm f) {
        return f.getFeeAmount() != null && f.getFeeAmount().signum() > 0;
    }

    /** PR3: the pass image, readable by whoever has the (unguessable) registration id. */
    @Transactional(readOnly = true)
    public byte[] passImage(String uid) {
        Registration r = repository.findByUid(uid).orElseThrow(() -> AppException.notFound("Pass"));
        if (r.getPassFileId() == null) {
            throw AppException.notFound("Pass");
        }
        return storage.readBytes(fileRepository.findById(r.getPassFileId()).orElseThrow(() -> AppException.notFound("Pass")));
    }

    // ---------------------------------------------------------------- sending

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistered(RegistrationCreatedEvent e) {
        try {
            sendPass(e.registrationId(), false);
        } catch (RuntimeException ex) {
            log.warn("Pass for registration {} not sent: {}", e.registrationId(), ex.getMessage());
        }
    }

    /**
     * Sends the pass. Template when configured; otherwise a plain image, which WhatsApp only allows while
     * the customer's 24-hour window is open. {@code windowOnly} = the customer just messaged us (claim code).
     */
    public PassStatus sendPass(Long registrationId, boolean windowOnly) {
        Registration r = repository.findById(registrationId).orElseThrow();
        if (r.getPassFileId() == null) {
            return r.getPassStatus();
        }
        RegistrationForm f = formRepository.findById(r.getFormId()).orElseThrow();
        StoredFile pass = fileRepository.findById(r.getPassFileId()).orElseThrow();
        Map<String, String> v = Placeholders.of(r.getName(), "+" + r.getPhone(), r.getPassNo());
        boolean open = windowRepository.findByPhoneNumberIdAndWaId(f.getPhoneNumberId(), r.getPhone())
                .map(w -> w.getExpiresAt() != null && w.getExpiresAt().isAfter(Instant.now())).orElse(false);
        SendMessageRequest req;
        if (open || windowOnly) {
            String caption = Placeholders.apply(f.getPassCaption() != null ? f.getPassCaption()
                    : "Assalamu Alaikum {name}, here is your pass {passNo}. Please show it at the entry.", v);
            req = new SendMessageRequest(f.getPhoneNumberId(), null, r.getPhone(), "image", null, null, null,
                    new SendMessageRequest.Media(pass.getUid(), null, null, caption.length() > 1024 ? caption.substring(0, 1024) : caption, null),
                    null, null, null, null, null, null);
        } else if (f.getPassTemplateId() != null) {
            req = RegistrationFormService.passTemplateMessage(f, f.getPhoneNumberId(), r.getPhone(), null, pass.getUid(), v);
        } else {
            tx.executeWithoutResult(s -> repository.findById(registrationId).ifPresent(x -> {
                if (x.getPassStatus() != PassStatus.SENT) {
                    x.setPassStatus(PassStatus.WAITING_FOR_CHAT);
                    x.setPassError(null);
                }
            }));
            return PassStatus.WAITING_FOR_CHAT;
        }
        try {
            Long messageId = tx.execute(s -> outbound.enqueue(OutboundCommand.of(f.getPartnerId(), f.getPhoneNumberId(), r.getPhone(),
                    req, MessageSource.REGISTRATION)).getId());
            tx.executeWithoutResult(s -> repository.findById(registrationId).ifPresent(x -> {
                x.setPassStatus(PassStatus.SENT);
                x.setPassMessageId(messageId);
                x.setPassError(null);
            }));
            return PassStatus.SENT;
        } catch (RuntimeException e) {
            String msg = e instanceof AppException ae ? ae.getMessage() : "Could not send: " + e.getMessage();
            log.info("Pass {} not sent: {}", r.getPassNo(), msg);
            tx.executeWithoutResult(s -> repository.findById(registrationId).ifPresent(x -> {
                x.setPassStatus(PassStatus.FAILED);
                x.setPassError(msg.length() > 500 ? msg.substring(0, 500) : msg);
            }));
            return PassStatus.FAILED;
        }
    }

    /** "PASS-XXXXXX" from the registered number: the window is now open, send the pass as a free image. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInbound(in.codebasket.cbwa.common.dto.PartnerEvent event) {
        if (!in.codebasket.cbwa.common.dto.PartnerEvent.MESSAGE_INBOUND.equals(event.type())) {
            return;
        }
        Object text = event.data().get("text");
        Object from = event.data().get("from");
        if (!(text instanceof String t) || !(from instanceof String phone)) {
            return;
        }
        Optional<Registration> r = claimed(event.partnerId(), phone, t);
        r.ifPresent(reg -> {
            try {
                sendPass(reg.getId(), true);
            } catch (RuntimeException e) {
                log.warn("Claimed pass {} not sent: {}", reg.getPassNo(), e.getMessage());
            }
        });
    }

    /** The registration a customer's message claims, if the code is theirs. Used by the AI to stay quiet. */
    public Optional<Registration> claimed(Long partnerId, String phone, String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = CLAIM.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        return repository.findByClaimCode(m.group(1).toUpperCase())
                .filter(x -> x.getPartnerId().equals(partnerId) && x.getPhone().equals(phone));
    }

    // ---------------------------------------------------------------- partner views

    @Transactional(readOnly = true)
    public PageResponse<RegistrationDto> list(Long partnerId, Long formId, String search, PassStatus status, Integer page, Integer size) {
        Specification<Registration> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("partnerId"), partnerId));
            if (formId != null) {
                ps.add(cb.equal(root.get("formId"), formId));
            }
            if (status != null) {
                ps.add(cb.equal(root.get("passStatus"), status));
            }
            if (search != null && !search.isBlank()) {
                String s = search.trim().toLowerCase();
                String digits = s.replaceAll("[^0-9]", "");
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("name")), "%" + s + "%"));
                or.add(cb.like(cb.lower(root.get("passNo")), "%" + s + "%"));
                if (!digits.isEmpty()) {
                    or.add(cb.like(root.get("phone"), "%" + digits + "%"));
                }
                ps.add(cb.or(or.toArray(Predicate[]::new)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Registration> result = repository.findAll(spec, Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, String> titles = new LinkedHashMap<>();
        formRepository.findAllById(result.map(Registration::getFormId).toSet()).forEach(f -> titles.put(f.getId(), f.getTitle()));
        Map<Long, String> messageUids = new LinkedHashMap<>();
        messageRepository.findAllById(result.stream().map(Registration::getPassMessageId).filter(java.util.Objects::nonNull).toList())
                .forEach(m -> messageUids.put(m.getId(), m.getUid()));
        Map<Long, String> photos = new LinkedHashMap<>();
        fileRepository.findAllById(result.stream().map(Registration::getPhotoFileId).filter(java.util.Objects::nonNull).toList())
                .forEach(x -> photos.put(x.getId(), "/api/files/" + x.getUid()));
        return PageResponse.of(result, r -> new RegistrationDto(r.getUid(), r.getFormId(), titles.get(r.getFormId()), r.getPassNo(),
                r.getName(), r.getPhone(), r.getPassStatus(), r.getPassError(), r.getPassFileId() == null ? null : passUrl(r),
                photos.get(r.getPhotoFileId()), messageUids.get(r.getPassMessageId()), r.getContactId(), r.getPaymentStatus(), r.getAmount(),
                r.getRazorpayPaymentId(), r.getPaymentMethod(), r.getPaidAt(), r.getCreatedAt(), booking.view(r)));
    }

    private RegistrationDto dto(Registration r) {
        String title = formRepository.findById(r.getFormId()).map(RegistrationForm::getTitle).orElse(null);
        String messageUid = r.getPassMessageId() == null ? null : messageRepository.findById(r.getPassMessageId()).map(Message::getUid).orElse(null);
        String photo = r.getPhotoFileId() == null ? null : fileRepository.findById(r.getPhotoFileId()).map(x -> "/api/files/" + x.getUid()).orElse(null);
        return new RegistrationDto(r.getUid(), r.getFormId(), title, r.getPassNo(), r.getName(), r.getPhone(), r.getPassStatus(),
                r.getPassError(), r.getPassFileId() == null ? null : passUrl(r), photo, messageUid, r.getContactId(), r.getPaymentStatus(),
                r.getAmount(), r.getRazorpayPaymentId(), r.getPaymentMethod(), r.getPaidAt(), r.getCreatedAt(), booking.view(r));
    }

    /** RG2: send the pass again (e.g. after it failed or the template was approved later). */
    public RegistrationDto resend(Long partnerId, String uid) {
        Registration r = repository.findByUidAndPartnerId(uid, partnerId).orElseThrow(() -> AppException.notFound("Registration"));
        if (r.getPassFileId() == null) {
            throw AppException.conflict("Passes are switched off for this link");
        }
        if (r.getPassNo() == null) {
            throw AppException.conflict("The registration fee is not paid yet");
        }
        sendPass(r.getId(), false);
        return tx.execute(s -> dto(repository.findById(r.getId()).orElseThrow()));
    }

    /** RG3: regenerate the pass image (after a design or name change) - the old file is kept for sent messages. */
    @Transactional
    public RegistrationDto regenerate(Long partnerId, String uid) {
        Registration r = repository.findByUidAndPartnerId(uid, partnerId).orElseThrow(() -> AppException.notFound("Registration"));
        if (r.getPassNo() == null) {
            throw AppException.conflict("The registration fee is not paid yet");
        }
        RegistrationForm f = formRepository.findById(r.getFormId()).orElseThrow();
        r.setPassFileId(renderPass(f, r).getId());
        if (r.getPassStatus() == PassStatus.DISABLED) {
            r.setPassStatus(PassStatus.PENDING);
        }
        repository.saveAndFlush(r);
        return dto(r);
    }

    @Transactional(readOnly = true)
    public List<Registration> export(Long partnerId, Long formId) {
        formService.find(partnerId, formId);
        return repository.findByFormIdOrderByIdAsc(formId);
    }

    // ---------------------------------------------------------------- helpers

    private StoredFile renderPass(RegistrationForm f, Registration r) {
        byte[] design = f.getPassDesignFileId() != null
                ? storage.readBytes(fileRepository.findById(f.getPassDesignFileId()).orElseThrow())
                : renderer.builtInDesign(PassRenderer.isBuiltIn(f.getPassDesign()) ? f.getPassDesign() : PassRenderer.BUILT_IN_HARMAIN);
        byte[] photo = r.getPhotoFileId() == null ? null
                : storage.readBytes(fileRepository.findById(r.getPhotoFileId()).orElseThrow());
        PassLayout layout = f.getPassLayout() != null ? f.getPassLayout() : PassLayout.harmainDefault();
        byte[] jpg = renderer.render(design, layout, new PassRenderer.PassData(r.getName(), formatMobile(r.getPhone()), r.getPassNo(), photo));
        return storage.store(jpg, r.getPassNo() + ".jpg", FileCategory.MEDIA_OUTBOUND, f.getPartnerId(), Set.of("image/jpeg"), 5L << 20);
    }

    private void scheduleFollowUps(RegistrationForm f, Registration r) {
        if (f.getFollowUps() == null || f.getFollowUps().isEmpty()) {
            return;
        }
        PhoneNumber sender = phoneNumberRepository.findById(f.getPhoneNumberId()).orElseThrow();
        Instant now = Instant.now();
        Map<String, String> v = Placeholders.of(r.getName(), "+" + r.getPhone(), r.getPassNo());
        for (FollowUp u : f.getFollowUps()) {
            Instant at = u.sendAt() != null ? u.sendAt() : now.plus(Duration.ofMinutes(u.delayMinutes() == null ? 1 : u.delayMinutes()));
            if (at.isBefore(now)) {
                continue; // a fixed-date message that already went out to earlier registrants
            }
            scheduledService.scheduleForRegistration(f.getPartnerId(), sender, r.getPhone(),
                    Placeholders.apply(u.message(), sender.getId(), r.getPhone(), v), at, r.getId());
        }
    }

    private Long upsertContact(RegistrationForm f, String name, String phone) {
        Contact c = contactRepository.findByPartnerIdAndPhone(f.getPartnerId(), phone).orElseGet(() -> {
            Contact n = new Contact();
            n.setPartnerId(f.getPartnerId());
            n.setPhone(phone);
            String region = PhoneUtil.regionOfDigits(phone);
            n.setCountryCode("ZZ".equals(region) ? null : region);
            n.setSource(ContactSource.REGISTRATION);
            n.setTags(new ArrayList<>());
            n.setAttributes(new LinkedHashMap<>());
            return n;
        });
        if (c.getName() == null || c.getName().isBlank()) {
            c.setName(name);
        }
        c.setOptedOut(false);
        List<String> tags = new ArrayList<>(c.getTags() == null ? List.of() : c.getTags());
        for (String t : f.getContactTags() == null ? List.<String>of() : f.getContactTags()) {
            if (!tags.contains(t)) {
                tags.add(t);
            }
        }
        c.setTags(tags);
        contactRepository.save(c);
        if (f.getContactGroupId() != null) {
            int added = jdbc.update("insert ignore into contact_group_members (group_id, contact_id, added_at) values (?, ?, ?)",
                    f.getContactGroupId(), c.getId(), java.sql.Timestamp.from(Instant.now()));
            if (added > 0) {
                jdbc.update("update contact_groups set member_count = member_count + 1 where id = ?", f.getContactGroupId());
            }
        }
        return c.getId();
    }

    private static String closedReason(RegistrationForm f, Partner p) {
        if (p.getStatus() != PartnerStatus.ACTIVE) {
            return "Registrations are closed";
        }
        if (f.getStatus() == FormStatus.CLOSED || (f.getClosesAt() != null && f.getClosesAt().isBefore(Instant.now()))) {
            return "Registrations are closed";
        }
        if (f.getStatus() == FormStatus.PAUSED) {
            return "Registrations are paused for now. Please try again later";
        }
        if (f.getMaxRegistrations() != null && f.getRegistrationsCount() >= f.getMaxRegistrations()) {
            return "All seats are booked";
        }
        return null;
    }

    private String newClaimCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (repository.existsByClaimCode(code));
        return code;
    }

    static String formatMobile(String digits) {
        if (digits.startsWith("91") && digits.length() == 12) {
            return "+91 " + digits.substring(2, 7) + " " + digits.substring(7);
        }
        return "+" + digits;
    }

    static String passUrl(Registration r) {
        return "/api/public/passes/" + r.getUid() + ".jpg";
    }
}
