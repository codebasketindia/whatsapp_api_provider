package in.codebasket.cbwa.travel;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.storage.StoredFileRepository;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.common.util.Ulids;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** TP1–TP5 travel packages and TB1–TB5 bookings: the facts the AI assistant answers from. */
@Service
@RequiredArgsConstructor
public class TravelService {

    static final int MAX_PACKAGES = 300;

    private final TravelPackageRepository packageRepository;
    private final TravelBookingRepository bookingRepository;
    private final StoredFileRepository fileRepository;

    public record MediaRef(String fileUid, String url, String mimeType, String filename) {
    }

    public record PackageDto(Long id, String code, String name, String destination, String duration, BigDecimal price, String priceNote,
                             LocalDate startDate, LocalDate endDate, Integer seatsTotal, long seatsBooked, Integer seatsAvailable,
                             String inclusions, String exclusions, String itinerary, String details, MediaRef media,
                             TravelPackageStatus status, int sortOrder, Instant updatedAt) {
    }

    public record PackageRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use letters, digits, - or _") String code,
                                 @NotBlank @Size(max = 160) String name, @Size(max = 160) String destination,
                                 @Size(max = 60) String duration, @DecimalMin("0") BigDecimal price, @Size(max = 255) String priceNote,
                                 LocalDate startDate, LocalDate endDate, @Min(0) @Max(100_000) Integer seatsTotal,
                                 @Size(max = 4000) String inclusions, @Size(max = 4000) String exclusions,
                                 @Size(max = 8000) String itinerary, @Size(max = 8000) String details, String fileUid,
                                 TravelPackageStatus status, Integer sortOrder) {
    }

    public record PackageRef(Long id, String code, String name) {
    }

    public record BookingDto(String uid, PackageRef travelPackage, String phone, String customerName, int travellers, BigDecimal amount,
                             BigDecimal paidAmount, BookingStatus status, LocalDate travelDate, String notes, Instant createdAt,
                             Instant updatedAt) {
    }

    public record BookingRequest(@NotNull Long packageId, @NotBlank String phone, @Size(max = 120) String customerName,
                                 @Min(1) @Max(500) Integer travellers, @DecimalMin("0") BigDecimal amount,
                                 @DecimalMin("0") BigDecimal paidAmount, BookingStatus status, LocalDate travelDate,
                                 @Size(max = 1000) String notes) {
    }

    // ---------------------------------------------------------------- packages

    @Transactional(readOnly = true)
    public List<PackageDto> packages(Long partnerId, TravelPackageStatus status) {
        List<TravelPackage> list = status == null ? packageRepository.findByPartnerIdOrderBySortOrderAscIdAsc(partnerId)
                : packageRepository.findByPartnerIdAndStatusOrderBySortOrderAscIdAsc(partnerId, status);
        return toDtos(list);
    }

    @Transactional
    public PackageDto createPackage(Long partnerId, PackageRequest req) {
        if (packageRepository.countByPartnerId(partnerId) >= MAX_PACKAGES) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "You can add at most " + MAX_PACKAGES + " packages");
        }
        if (packageRepository.existsByPartnerIdAndCode(partnerId, req.code().trim())) {
            throw AppException.field("code", "This package code is already used");
        }
        TravelPackage p = new TravelPackage();
        p.setPartnerId(partnerId);
        return savePackage(p, req);
    }

    @Transactional
    public PackageDto updatePackage(Long partnerId, Long id, PackageRequest req) {
        TravelPackage p = packageRepository.findByIdAndPartnerId(id, partnerId).orElseThrow(() -> AppException.notFound("Package"));
        if (packageRepository.existsByPartnerIdAndCodeAndIdNot(partnerId, req.code().trim(), id)) {
            throw AppException.field("code", "This package code is already used");
        }
        return savePackage(p, req);
    }

    @Transactional
    public void deletePackage(Long partnerId, Long id) {
        TravelPackage p = packageRepository.findByIdAndPartnerId(id, partnerId).orElseThrow(() -> AppException.notFound("Package"));
        if (bookingRepository.existsByPackageId(id)) {
            throw AppException.conflict("This package has bookings. Mark it Inactive instead of deleting");
        }
        packageRepository.delete(p);
    }

    private PackageDto savePackage(TravelPackage p, PackageRequest req) {
        if (req.startDate() != null && req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw AppException.field("endDate", "End date is before the start date");
        }
        p.setCode(req.code().trim());
        p.setName(req.name().trim());
        p.setDestination(trim(req.destination()));
        p.setDuration(trim(req.duration()));
        p.setPrice(req.price());
        p.setPriceNote(trim(req.priceNote()));
        p.setStartDate(req.startDate());
        p.setEndDate(req.endDate());
        p.setSeatsTotal(req.seatsTotal());
        p.setInclusions(trim(req.inclusions()));
        p.setExclusions(trim(req.exclusions()));
        p.setItinerary(trim(req.itinerary()));
        p.setDetails(trim(req.details()));
        p.setStatus(req.status() == null ? TravelPackageStatus.ACTIVE : req.status());
        p.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        if (req.fileUid() == null || req.fileUid().isBlank()) {
            p.setFileId(null);
        } else {
            StoredFile f = fileRepository.findByUid(req.fileUid()).filter(x -> p.getPartnerId().equals(x.getPartnerId()))
                    .orElseThrow(() -> AppException.field("fileUid", "Uploaded file not found"));
            p.setFileId(f.getId());
        }
        return toDtos(List.of(packageRepository.save(p))).get(0);
    }

    // ---------------------------------------------------------------- bookings

    @Transactional(readOnly = true)
    public PageResponse<BookingDto> bookings(Long partnerId, Long packageId, BookingStatus status, String search, Integer page, Integer size) {
        Specification<TravelBooking> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("partnerId"), partnerId));
            if (packageId != null) {
                ps.add(cb.equal(root.get("packageId"), packageId));
            }
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String s = search.trim().toLowerCase();
                String digits = s.replaceAll("[^0-9]", "");
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("customerName")), "%" + s + "%"));
                if (!digits.isEmpty()) {
                    or.add(cb.like(root.get("phone"), "%" + digits + "%"));
                }
                ps.add(cb.or(or.toArray(Predicate[]::new)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<TravelBooking> result = bookingRepository.findAll(spec, Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, TravelPackage> pk = packageRepository.findAllById(result.map(TravelBooking::getPackageId).toSet()).stream()
                .collect(Collectors.toMap(TravelPackage::getId, Function.identity()));
        return PageResponse.of(result, b -> toDto(b, pk.get(b.getPackageId())));
    }

    @Transactional
    public BookingDto createBooking(Long partnerId, BookingRequest req) {
        TravelBooking b = new TravelBooking();
        b.setUid(Ulids.next());
        b.setPartnerId(partnerId);
        return saveBooking(b, req);
    }

    @Transactional
    public BookingDto updateBooking(Long partnerId, String uid, BookingRequest req) {
        TravelBooking b = bookingRepository.findByUidAndPartnerId(uid, partnerId).orElseThrow(() -> AppException.notFound("Booking"));
        return saveBooking(b, req);
    }

    @Transactional
    public void deleteBooking(Long partnerId, String uid) {
        bookingRepository.delete(bookingRepository.findByUidAndPartnerId(uid, partnerId).orElseThrow(() -> AppException.notFound("Booking")));
    }

    private BookingDto saveBooking(TravelBooking b, BookingRequest req) {
        TravelPackage p = packageRepository.findByIdAndPartnerId(req.packageId(), b.getPartnerId())
                .orElseThrow(() -> AppException.field("packageId", "Package not found"));
        String phone = PhoneUtil.parse(req.phone(), "IN").map(PhoneUtil.Parsed::digits)
                .orElseThrow(() -> AppException.field("phone", "Enter a valid mobile number"));
        int travellers = req.travellers() == null ? 1 : req.travellers();
        BookingStatus status = req.status() == null ? BookingStatus.ENQUIRY : req.status();
        if ((status == BookingStatus.CONFIRMED || status == BookingStatus.COMPLETED) && p.getSeatsTotal() != null) {
            long taken = bookingRepository.seatsBookedExcluding(p.getId(), b.getId() == null ? -1L : b.getId());
            if (taken + travellers > p.getSeatsTotal()) {
                throw AppException.field("travellers", "Only " + Math.max(0, p.getSeatsTotal() - taken) + " seats are left in this package");
            }
        }
        b.setPackageId(p.getId());
        b.setPhone(phone);
        b.setCustomerName(trim(req.customerName()));
        b.setTravellers(travellers);
        b.setAmount(req.amount());
        b.setPaidAmount(req.paidAmount());
        b.setStatus(status);
        b.setTravelDate(req.travelDate());
        b.setNotes(trim(req.notes()));
        return toDto(bookingRepository.save(b), p);
    }

    // ---------------------------------------------------------------- AI context (read live on every answer)

    /** Active packages with live seat availability. */
    @Transactional(readOnly = true)
    public List<PackageDto> activeForAi(Long partnerId) {
        return toDtos(packageRepository.findByPartnerIdAndStatusOrderBySortOrderAscIdAsc(partnerId, TravelPackageStatus.ACTIVE));
    }

    /** This customer's own bookings only (tenant + phone scoped). */
    @Transactional(readOnly = true)
    public List<BookingDto> bookingsOf(Long partnerId, String phone) {
        List<TravelBooking> list = bookingRepository.findTop10ByPartnerIdAndPhoneOrderByIdDesc(partnerId, phone);
        Map<Long, TravelPackage> pk = packageRepository.findAllById(list.stream().map(TravelBooking::getPackageId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(TravelPackage::getId, Function.identity()));
        return list.stream().map(b -> toDto(b, pk.get(b.getPackageId()))).toList();
    }

    // ---------------------------------------------------------------- mapping

    private List<PackageDto> toDtos(Collection<TravelPackage> list) {
        Set<Long> ids = list.stream().map(TravelPackage::getId).collect(Collectors.toSet());
        Map<Long, Long> booked = new HashMap<>();
        if (!ids.isEmpty()) {
            bookingRepository.seatsBooked(ids).forEach(r -> booked.put((Long) r[0], ((Number) r[1]).longValue()));
        }
        Set<Long> fileIds = list.stream().map(TravelPackage::getFileId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, StoredFile> files = fileIds.isEmpty() ? Map.of() : fileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        return list.stream().map(p -> {
            long b = booked.getOrDefault(p.getId(), 0L);
            StoredFile f = p.getFileId() == null ? null : files.get(p.getFileId());
            return new PackageDto(p.getId(), p.getCode(), p.getName(), p.getDestination(), p.getDuration(), p.getPrice(), p.getPriceNote(),
                    p.getStartDate(), p.getEndDate(), p.getSeatsTotal(), b,
                    p.getSeatsTotal() == null ? null : (int) Math.max(0, p.getSeatsTotal() - b),
                    p.getInclusions(), p.getExclusions(), p.getItinerary(), p.getDetails(),
                    f == null ? null : new MediaRef(f.getUid(), "/api/files/" + f.getUid(), f.getMimeType(), f.getOriginalName()),
                    p.getStatus(), p.getSortOrder(), p.getUpdatedAt());
        }).toList();
    }

    private static BookingDto toDto(TravelBooking b, TravelPackage p) {
        return new BookingDto(b.getUid(), p == null ? null : new PackageRef(p.getId(), p.getCode(), p.getName()), b.getPhone(),
                b.getCustomerName(), b.getTravellers(), b.getAmount(), b.getPaidAmount(), b.getStatus(), b.getTravelDate(), b.getNotes(),
                b.getCreatedAt(), b.getUpdatedAt());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
