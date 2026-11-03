package in.codebasket.cbwa.travel;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.travel.TravelService.BookingDto;
import in.codebasket.cbwa.travel.TravelService.BookingRequest;
import in.codebasket.cbwa.travel.TravelService.PackageDto;
import in.codebasket.cbwa.travel.TravelService.PackageRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Partner - Travel packages & bookings")
@RestController
@RequiredArgsConstructor
public class TravelController {

    private final TravelService service;

    @GetMapping("/api/partner/travel/packages")
    public ApiResponse<List<PackageDto>> packages(@RequestParam(required = false) TravelPackageStatus status) {
        return ApiResponse.ok(service.packages(SecurityUtils.currentPartnerId(), status));
    }

    @PostMapping("/api/partner/travel/packages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PackageDto> createPackage(@Valid @RequestBody PackageRequest req) {
        return ApiResponse.created(service.createPackage(SecurityUtils.currentPartnerId(), req), "Package added");
    }

    @PutMapping("/api/partner/travel/packages/{id}")
    public ApiResponse<PackageDto> updatePackage(@PathVariable Long id, @Valid @RequestBody PackageRequest req) {
        return ApiResponse.ok(service.updatePackage(SecurityUtils.currentPartnerId(), id, req), "Package saved");
    }

    @DeleteMapping("/api/partner/travel/packages/{id}")
    public ApiResponse<Void> deletePackage(@PathVariable Long id) {
        service.deletePackage(SecurityUtils.currentPartnerId(), id);
        return ApiResponse.ok(null, "Package deleted");
    }

    @GetMapping("/api/partner/travel/bookings")
    public ApiResponse<PageResponse<BookingDto>> bookings(@RequestParam(required = false) Long packageId,
                                                          @RequestParam(required = false) BookingStatus status,
                                                          @RequestParam(required = false) String search,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.bookings(SecurityUtils.currentPartnerId(), packageId, status, search, page, size));
    }

    @PostMapping("/api/partner/travel/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingDto> createBooking(@Valid @RequestBody BookingRequest req) {
        return ApiResponse.created(service.createBooking(SecurityUtils.currentPartnerId(), req), "Booking added");
    }

    @PutMapping("/api/partner/travel/bookings/{uid}")
    public ApiResponse<BookingDto> updateBooking(@PathVariable String uid, @Valid @RequestBody BookingRequest req) {
        return ApiResponse.ok(service.updateBooking(SecurityUtils.currentPartnerId(), uid, req), "Booking saved");
    }

    @DeleteMapping("/api/partner/travel/bookings/{uid}")
    public ApiResponse<Void> deleteBooking(@PathVariable String uid) {
        service.deleteBooking(SecurityUtils.currentPartnerId(), uid);
        return ApiResponse.ok(null, "Booking deleted");
    }
}
