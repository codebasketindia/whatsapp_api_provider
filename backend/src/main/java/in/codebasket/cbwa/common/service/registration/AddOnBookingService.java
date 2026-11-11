package in.codebasket.cbwa.common.service.registration;

import in.codebasket.cbwa.common.dto.FieldErrorDto;
import in.codebasket.cbwa.common.entity.BookingOrder;
import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.entity.RegistrationForm;
import in.codebasket.cbwa.common.entity.RegistrationItem;
import in.codebasket.cbwa.common.entity.RegistrationPackage;
import in.codebasket.cbwa.common.entity.StoredFile;
import in.codebasket.cbwa.common.repository.BookingOrderRepository;
import in.codebasket.cbwa.common.repository.RegistrationFormRepository;
import in.codebasket.cbwa.common.repository.RegistrationItemRepository;
import in.codebasket.cbwa.common.repository.RegistrationRepository;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Add-on purchases: a mobile number that already has a paid booking on a link with packages can buy the passes it
 * does not have yet. They join the same booking (same booking no., pass ids, downloads); the registration fee is not
 * charged again. Each add-on is a {@link BookingOrder} with its own Razorpay order and invoice.
 * <p>
 * The number counts as "already registered" once it owns the whole combo (or every optional package).
 * All prices come from the database; a payment is accepted only for the order's current Razorpay order and amount.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddOnBookingService {

    /** Passes created by a paid add-on, to be sent on WhatsApp after commit. */
    public record AddOnPaidEvent(Long registrationId, List<Long> passFileIds) {
    }

    private final BookingOrderRepository orderRepository;
    private final RegistrationRepository registrationRepository;
    private final RegistrationFormRepository formRepository;
    private final RegistrationItemRepository itemRepository;
    private final BookingService booking;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;

    /**
     * Inside the registering transaction, with the form and the registration locked. Creates the add-on order
     * (PENDING), or completes it at once when it costs nothing. Throws when there is nothing left to buy.
     */
    public BookingOrder start(RegistrationForm f, Registration r, List<Long> packageIds) {
        List<RegistrationPackage> active = booking.activePackages(f.getId());
        Map<Long, RegistrationPackage> byId = active.stream().collect(Collectors.toMap(RegistrationPackage::getId, Function.identity()));
        Set<Long> owned = itemRepository.findByRegistrationIdOrderByIdAsc(r.getId()).stream()
                .map(RegistrationItem::getPackageId).collect(Collectors.toSet());
        Set<String> ownedCodes = active.stream().filter(p -> owned.contains(p.getId())).map(RegistrationPackage::getCode).collect(Collectors.toSet());
        List<RegistrationPackage> optional = active.stream().filter(p -> !p.isMandatory()).toList();
        BookingService.ComboView combo = booking.combo(f);
        List<String> comboCodes = BookingService.comboCodes(f);
        boolean ownsCombo = !comboCodes.isEmpty() && ownedCodes.containsAll(comboCodes);
        boolean ownsAll = optional.stream().allMatch(p -> owned.contains(p.getId()));
        if (ownsCombo || ownsAll) {
            throw new AppException(ErrorCode.CONFLICT, "This mobile number is already registered", null,
                    List.of(new FieldErrorDto("mobile", "This mobile number is already registered")));
        }
        Set<Long> chosen = new LinkedHashSet<>(packageIds == null ? List.of() : packageIds);
        for (Long id : chosen) {
            if (!byId.containsKey(id)) {
                throw AppException.field("packageIds", "One of the chosen packages is not available any more. Please refresh the page");
            }
        }
        // A free pass it already has (often still ticked on the page) is simply skipped; a paid one is refused by name.
        List<RegistrationPackage> already = optional.stream()
                .filter(p -> chosen.contains(p.getId()) && owned.contains(p.getId()) && p.getSalePrice().signum() > 0).toList();
        if (!already.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Aap ye pass pehle hi le chuke hain: " + already.stream().map(RegistrationPackage::getName).collect(Collectors.joining(", ")),
                    null, List.of(
                    new FieldErrorDto("packageIds", "Aap ye pass pehle hi le chuke hain: "
                            + already.stream().map(RegistrationPackage::getName).collect(Collectors.joining(", "))
                            + ". Inhe hata diya gaya hai, baaki ke liye dobara tap karein."),
                    new FieldErrorDto("ownedPackageIds", already.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.joining(",")))));
        }
        List<RegistrationPackage> picked = optional.stream().filter(p -> chosen.contains(p.getId()) && !owned.contains(p.getId())).toList();
        if (picked.isEmpty()) {
            throw AppException.field("packageIds", "Koi naya pass chuniye");
        }
        // A newer attempt replaces an unpaid earlier one.
        for (BookingOrder old : orderRepository.findByRegistrationIdOrderBySeqAsc(r.getId())) {
            if (BookingOrder.PENDING.equals(old.getStatus())) {
                old.setStatus(BookingOrder.CANCELLED);
                orderRepository.save(old);
            }
        }
        BookingService.Totals totals = booking.totalsFor(f, picked);
        BookingOrder o = new BookingOrder();
        o.setRegistrationId(r.getId());
        o.setPartnerId(r.getPartnerId());
        o.setSeq(orderRepository.maxSeq(r.getId()) + 1);
        o.setPackageIds(picked.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.joining(",")));
        o.setAmount(totals.total());
        o.setMrpTotal(totals.mrp());
        o.setDiscountAmount(totals.discount().signum() > 0 ? totals.discount() : null);
        o.setDiscountLabel(totals.discountLabel());
        o.setStatus(BookingOrder.PENDING);
        orderRepository.saveAndFlush(o);
        if (o.getAmount().signum() == 0) {
            fulfil(f, r, o, null, null);
        }
        return o;
    }

    /** The latest unpaid add-on of a booking that has no Razorpay order yet. */
    public BookingOrder pendingWithoutCheckout(Long registrationId) {
        List<BookingOrder> all = orderRepository.findByRegistrationIdOrderBySeqAsc(registrationId);
        for (int i = all.size() - 1; i >= 0; i--) {
            BookingOrder o = all.get(i);
            if (BookingOrder.PENDING.equals(o.getStatus()) && o.getRazorpayOrderId() == null) {
                return o;
            }
        }
        return null;
    }

    /** Attaches the Razorpay order only while the add-on is still unpaid, unreplaced and costs exactly this much. */
    public boolean attach(Long orderId, String razorpayOrderId, long paise) {
        return Boolean.TRUE.equals(tx.execute(s -> orderRepository.lockById(orderId)
                .filter(o -> BookingOrder.PENDING.equals(o.getStatus()) && o.getRazorpayOrderId() == null
                        && o.getAmount().movePointRight(2).longValueExact() == paise)
                .map(o -> {
                    o.setRazorpayOrderId(razorpayOrderId);
                    return true;
                }).orElse(false)));
    }

    public BookingOrder byRazorpayOrder(String razorpayOrderId) {
        return razorpayOrderId == null ? null : orderRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
    }

    /**
     * Idempotent. Checked under locks (form, registration, order): the order must still be PENDING, the payment must
     * be for its Razorpay order and (when known) cover exactly its amount. Returns true when this call completed it.
     */
    public boolean markPaid(Long orderId, String razorpayOrderId, String paymentId, String method, Long paidPaise) {
        BookingOrder found = orderRepository.findById(orderId).orElse(null);
        if (found == null) {
            return false;
        }
        Long formId = registrationRepository.findById(found.getRegistrationId()).map(Registration::getFormId).orElse(null);
        if (formId == null) {
            return false;
        }
        Boolean done = tx.execute(s -> {
            RegistrationForm f = formRepository.lockById(formId).orElseThrow();
            Registration r = registrationRepository.lockById(found.getRegistrationId()).orElseThrow();
            BookingOrder o = orderRepository.lockById(orderId).orElseThrow();
            if (!BookingOrder.PENDING.equals(o.getStatus())) {
                if (!BookingOrder.PAID.equals(o.getStatus())) {
                    log.warn("Add-on order {} ({}) got payment {} but is {}", o.getId(), razorpayOrderId, paymentId, o.getStatus());
                }
                return false;
            }
            if (razorpayOrderId == null || !razorpayOrderId.equals(o.getRazorpayOrderId())) {
                log.warn("Add-on order {}: payment {} is for order {}, not {}", o.getId(), paymentId, razorpayOrderId, o.getRazorpayOrderId());
                return false;
            }
            if (paidPaise != null && o.getAmount().movePointRight(2).longValueExact() != paidPaise) {
                log.warn("Add-on order {}: payment {} of {} paise does not match {}", o.getId(), paymentId, paidPaise, o.getAmount());
                return false;
            }
            fulfil(f, r, o, paymentId, method);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    public void markFailed(Long orderId) {
        tx.executeWithoutResult(s -> orderRepository.findById(orderId).filter(o -> BookingOrder.PENDING.equals(o.getStatus()))
                .ifPresent(o -> o.setStatus(BookingOrder.FAILED)));
    }

    /** Items + passes for the order's packages (skipping any the booking already has), invoice no., PAID. */
    private void fulfil(RegistrationForm f, Registration r, BookingOrder o, String paymentId, String method) {
        Set<Long> owned = itemRepository.findByRegistrationIdOrderByIdAsc(r.getId()).stream()
                .map(RegistrationItem::getPackageId).collect(Collectors.toSet());
        Collection<Long> ids = Arrays.stream(o.getPackageIds().split(",")).map(Long::valueOf).toList();
        Map<Long, RegistrationPackage> pk = booking.packagesById(ids);
        List<RegistrationItem> items = new ArrayList<>();
        for (Long id : ids) {
            RegistrationPackage p = pk.get(id);
            if (p == null || owned.contains(id)) {
                log.warn("Add-on order {}: package {} skipped (missing or already in booking {})", o.getId(), id, r.getPassNo());
                continue;
            }
            RegistrationItem it = new RegistrationItem();
            it.setRegistrationId(r.getId());
            it.setPackageId(id);
            it.setPartnerId(r.getPartnerId());
            it.setName(p.getName());
            it.setMrp(p.getMrp());
            it.setPrice(p.getSalePrice());
            it.setOrderId(o.getId());
            items.add(itemRepository.save(it));
        }
        itemRepository.flush();
        List<StoredFile> files = booking.completeAddOnItems(f, r, items);
        o.setStatus(BookingOrder.PAID);
        o.setPaidAt(Instant.now());
        o.setRazorpayPaymentId(paymentId);
        o.setPaymentMethod(method);
        if (o.getAmount().signum() > 0) {
            o.setInvoiceNo("INV-" + r.getPassNo() + "-" + o.getSeq());
        }
        orderRepository.save(o);
        log.info("Add-on {} of booking {} completed ({} passes)", o.getSeq(), r.getPassNo(), files.size());
        events.publishEvent(new AddOnPaidEvent(r.getId(), files.stream().map(StoredFile::getId).toList()));
    }

    /** Unpaid add-on orders 2 minutes to 24 hours old, for the reconciliation job. */
    public List<BookingOrder> pendingForCheck() {
        Instant now = Instant.now();
        return orderRepository.findTop50ByStatusAndRazorpayOrderIdIsNotNullAndUpdatedAtBetweenOrderByIdAsc(BookingOrder.PENDING,
                now.minus(java.time.Duration.ofHours(24)), now.minus(java.time.Duration.ofMinutes(2)));
    }
}
