package in.codebasket.cbwa.topup;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.topup.TopupService.TopupRequestDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

/** W4-W7 (admin queue) and W17-W19 (partner requests). */
@Tag(name = "Top-up requests")
@RestController
@RequiredArgsConstructor
public class TopupController {

    private final TopupService service;

    public record ApproveRequest(@Size(max = 255) String remarks) {
    }

    public record RejectRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
    }

    // ---------------- partner

    @PostMapping(value = "/api/partner/topup-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TopupRequestDto> submit(@RequestParam BigDecimal amount,
                                               @RequestParam String paymentMode,
                                               @RequestParam(required = false) String utr,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
                                               @RequestParam(required = false) String note,
                                               @RequestPart(value = "screenshot", required = false) MultipartFile screenshot) {
        return ApiResponse.created(service.submit(SecurityUtils.currentPartnerId(), amount, paymentMode, utr, paymentDate, note,
                screenshot), "Request submitted. We will credit your wallet after verification");
    }

    @GetMapping("/api/partner/topup-requests")
    public ApiResponse<PageResponse<TopupRequestDto>> mine(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.listForPartner(SecurityUtils.currentPartnerId(), status, page, size));
    }

    @GetMapping("/api/partner/topup-requests/{id}")
    public ApiResponse<TopupRequestDto> mineOne(@PathVariable Long id) {
        return ApiResponse.ok(service.getForPartner(SecurityUtils.currentPartnerId(), id));
    }

    // ---------------- admin

    @GetMapping("/api/admin/topup-requests")
    @PreAuthorize("hasAuthority('PERM_TOPUP_VIEW')")
    public ApiResponse<PageResponse<TopupRequestDto>> list(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Long partnerId,
                                                           @RequestParam(required = false) String paymentMode,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                           @RequestParam(required = false) String search,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.search(partnerId, status, paymentMode, from, to, search, page, size));
    }

    @GetMapping("/api/admin/topup-requests/{id}")
    @PreAuthorize("hasAuthority('PERM_TOPUP_VIEW')")
    public ApiResponse<TopupRequestDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/api/admin/topup-requests/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_TOPUP_APPROVE')")
    public ApiResponse<TopupRequestDto> approve(@PathVariable Long id, @Valid @RequestBody(required = false) ApproveRequest req) {
        return ApiResponse.ok(service.approve(id, req == null ? null : req.remarks()), "Approved and wallet credited");
    }

    @PostMapping("/api/admin/topup-requests/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_TOPUP_APPROVE')")
    public ApiResponse<TopupRequestDto> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return ApiResponse.ok(service.reject(id, req.reason()), "Request rejected");
    }
}
