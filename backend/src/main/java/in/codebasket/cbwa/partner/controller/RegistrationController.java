package in.codebasket.cbwa.partner.controller;

import in.codebasket.cbwa.common.constants.PassStatus;
import in.codebasket.cbwa.common.constants.PaymentStatus;

import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.service.registration.PartnerPaymentService;
import in.codebasket.cbwa.common.service.registration.RegistrationFormService;
import in.codebasket.cbwa.common.service.registration.RegistrationService;
import in.codebasket.cbwa.common.service.registration.BookingService;

import in.codebasket.cbwa.security.LoginRateLimiter;
import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.common.dto.PageResponse;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.common.utils.ClientIp;
import in.codebasket.cbwa.common.service.registration.RegistrationFormService.BroadcastRequest;
import in.codebasket.cbwa.common.service.registration.RegistrationFormService.FileRef;
import in.codebasket.cbwa.common.service.registration.RegistrationFormService.FormDto;
import in.codebasket.cbwa.common.service.registration.RegistrationFormService.FormRequest;
import in.codebasket.cbwa.common.service.registration.RegistrationFormService.PreviewRequest;
import in.codebasket.cbwa.common.service.registration.RegistrationService.PublicFormDto;
import in.codebasket.cbwa.common.service.registration.RegistrationService.RegisterResult;
import in.codebasket.cbwa.common.service.registration.RegistrationService.RegistrationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Tag(name = "Registration links & free passes")
@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationFormService formService;
    private final RegistrationService service;
    private final LoginRateLimiter rateLimiter;
    private final PartnerPaymentService payments;
    private final BookingService booking;

    public record VerifyRequest(@jakarta.validation.constraints.NotBlank String registrationUid,
                                @jakarta.validation.constraints.NotBlank String razorpayOrderId,
                                @jakarta.validation.constraints.NotBlank String razorpayPaymentId,
                                @jakarta.validation.constraints.NotBlank String razorpaySignature) {
    }

    // ---------------------------------------------------------------- partner: Razorpay account (PA1–PA3)

    @GetMapping("/api/partner/payment-account")
    public ApiResponse<PartnerPaymentService.AccountDto> paymentAccount() {
        return ApiResponse.ok(payments.get(SecurityUtils.currentPartnerId()));
    }

    @PutMapping("/api/partner/payment-account")
    public ApiResponse<PartnerPaymentService.AccountDto> savePaymentAccount(@Valid @RequestBody PartnerPaymentService.AccountRequest req) {
        return ApiResponse.ok(payments.save(SecurityUtils.currentPartnerId(), req), "Razorpay settings saved");
    }

    @PostMapping("/api/partner/payment-account/test")
    public ApiResponse<PartnerPaymentService.AccountDto> testPaymentAccount() {
        return ApiResponse.ok(payments.testNow(SecurityUtils.currentPartnerId()), "Razorpay keys are working");
    }

    // ---------------------------------------------------------------- partner: links (RF1–RF9)

    @GetMapping("/api/partner/registration-forms")
    public ApiResponse<List<FormDto>> list() {
        return ApiResponse.ok(formService.list(SecurityUtils.currentPartnerId()));
    }

    @PostMapping("/api/partner/registration-forms")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FormDto> create(@Valid @RequestBody FormRequest req) {
        return ApiResponse.created(formService.create(SecurityUtils.currentPartnerId(), req), "Registration link created");
    }

    @GetMapping("/api/partner/registration-forms/{id}")
    public ApiResponse<FormDto> get(@PathVariable Long id) {
        return ApiResponse.ok(formService.get(SecurityUtils.currentPartnerId(), id));
    }

    @PutMapping("/api/partner/registration-forms/{id}")
    public ApiResponse<FormDto> update(@PathVariable Long id, @Valid @RequestBody FormRequest req) {
        return ApiResponse.ok(formService.update(SecurityUtils.currentPartnerId(), id, req), "Registration link saved");
    }

    @DeleteMapping("/api/partner/registration-forms/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        formService.delete(SecurityUtils.currentPartnerId(), id);
        return ApiResponse.ok(null, "Registration link deleted");
    }

    @PostMapping("/api/partner/registration-forms/design")
    public ApiResponse<FileRef> uploadDesign(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(formService.uploadDesign(SecurityUtils.currentPartnerId(), file), "Design uploaded");
    }

    @PostMapping(value = "/api/partner/registration-forms/preview", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> preview(@RequestBody PreviewRequest req) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
                .body(formService.preview(SecurityUtils.currentPartnerId(), req));
    }

    @PostMapping("/api/partner/registration-forms/{id}/broadcast")
    public ApiResponse<Map<String, Object>> broadcast(@PathVariable Long id, @Valid @RequestBody BroadcastRequest req) {
        return ApiResponse.ok(formService.broadcast(SecurityUtils.currentPartnerId(), id, req), "Message scheduled for everyone registered");
    }

    @GetMapping("/api/partner/registration-forms/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        StringBuilder sb = new StringBuilder("Pass No,Name,Mobile,Pass status,Payment,Amount,Razorpay payment id,Registered at\n");
        for (Registration r : service.export(SecurityUtils.currentPartnerId(), id)) {
            sb.append(csv(r.getPassNo())).append(',').append(csv(r.getName())).append(',').append(csv("+" + r.getPhone())).append(',')
                    .append(r.getPassStatus()).append(',').append(r.getPaymentStatus()).append(',')
                    .append(r.getAmount() == null ? "" : r.getAmount().toPlainString()).append(',').append(csv(r.getRazorpayPaymentId()))
                    .append(',').append(r.getCreatedAt()).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"registrations-" + id + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("﻿" + sb).getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- partner: registrations (RG1–RG3)

    @GetMapping("/api/partner/registrations")
    public ApiResponse<PageResponse<RegistrationDto>> registrations(@RequestParam(required = false) Long formId,
                                                                    @RequestParam(required = false) String search,
                                                                    @RequestParam(required = false) PassStatus passStatus,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(SecurityUtils.currentPartnerId(), formId, search, passStatus, page, size));
    }

    @PostMapping("/api/partner/registrations/{uid}/resend")
    public ApiResponse<RegistrationDto> resend(@PathVariable String uid) {
        RegistrationDto dto = service.resend(SecurityUtils.currentPartnerId(), uid);
        return ApiResponse.ok(dto, switch (dto.passStatus()) {
            case SENT -> "Pass sent on WhatsApp";
            case WAITING_FOR_CHAT -> "The customer must message you first (or set a pass template)";
            default -> "Pass could not be sent";
        });
    }

    @PostMapping("/api/partner/registrations/{uid}/check-payment")
    public ApiResponse<RegistrationDto> checkPayment(@PathVariable String uid) {
        RegistrationDto dto = service.checkPayment(SecurityUtils.currentPartnerId(), uid);
        return ApiResponse.ok(dto, dto.paymentStatus() == PaymentStatus.PAID ? "Payment received - pass created" : "No completed payment yet");
    }

    @PostMapping("/api/partner/registrations/{uid}/regenerate")
    public ApiResponse<RegistrationDto> regenerate(@PathVariable String uid) {
        return ApiResponse.ok(service.regenerate(SecurityUtils.currentPartnerId(), uid), "Pass image created again");
    }

    // ---------------------------------------------------------------- public (PR1–PR3)

    @GetMapping("/api/public/register/{slug}")
    public ApiResponse<PublicFormDto> publicForm(@PathVariable String slug) {
        return ApiResponse.ok(service.publicForm(slug));
    }

    @PostMapping(value = "/api/public/register/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResult> register(@PathVariable String slug, @RequestParam(required = false) String name,
                                                @RequestParam(required = false) String mobile,
                                                @RequestParam(required = false) Boolean consent,
                                                @RequestParam(required = false) MultipartFile photo,
                                                @RequestParam(required = false) String website,
                                                @RequestParam(required = false) java.util.List<Long> packageIds,
                                                HttpServletRequest request) {
        String ip = ClientIp.of(request);
        rateLimiter.check("register", ip);
        if (website != null && !website.isBlank()) {
            throw AppException.badRequest("Request rejected"); // honeypot field filled in by bots
        }
        return ApiResponse.created(service.register(slug, name, mobile, consent, photo, ip, request.getHeader(HttpHeaders.USER_AGENT), packageIds),
                "Registered");
    }

    @PostMapping("/api/public/register/{slug}/verify-payment")
    public ApiResponse<RegisterResult> verifyPayment(@PathVariable String slug, @Valid @RequestBody VerifyRequest req, HttpServletRequest request) {
        rateLimiter.check("register-verify", ClientIp.of(request));
        return ApiResponse.ok(service.verifyPayment(slug, req.registrationUid(), req.razorpayOrderId(), req.razorpayPaymentId(),
                req.razorpaySignature()), "Payment received");
    }

    @GetMapping("/api/public/register/{slug}/legal/{kind}")
    public ApiResponse<RegistrationService.LegalDto> legal(@PathVariable String slug, @PathVariable String kind) {
        return ApiResponse.ok(service.legal(slug, kind));
    }

    // ---------------------------------------------------------------- bookings: downloads (unguessable booking id) and QR checks

    @GetMapping(value = "/api/public/bookings/{uid}/passes/{itemId}.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> bookingPass(@PathVariable String uid, @PathVariable Long itemId,
                                              @RequestParam(defaultValue = "false") boolean download) {
        return file(booking.itemPass(uid, itemId), MediaType.IMAGE_JPEG, "pass-" + itemId + ".jpg", download);
    }

    @GetMapping(value = "/api/public/bookings/{uid}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> invoice(@PathVariable String uid, @RequestParam(defaultValue = "true") boolean download) {
        return file(booking.invoicePdf(uid), MediaType.APPLICATION_PDF, "invoice.pdf", download);
    }

    @GetMapping(value = "/api/public/bookings/{uid}/passes.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> passesPdf(@PathVariable String uid, @RequestParam(defaultValue = "true") boolean download) {
        return file(booking.passesPdf(uid), MediaType.APPLICATION_PDF, "passes.pdf", download);
    }

    @GetMapping("/api/public/package-images/{fileUid}")
    public ResponseEntity<byte[]> packageImage(@PathVariable String fileUid) {
        var img = booking.packageImage(fileUid);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.mimeType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).header("X-Content-Type-Options", "nosniff").body(img.bytes());
    }

    /** PV1: anyone scanning the QR sees whether it is valid (name masked); nothing is redeemed. */
    @GetMapping("/api/public/passes/check")
    public ApiResponse<BookingService.VerifyResult> check(@RequestParam String token, HttpServletRequest request) {
        rateLimiter.check("pass-check", ClientIp.of(request));
        return ApiResponse.ok(booking.check(token));
    }

    public record TokenRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String token) {
    }

    /** PV2: partner scanner - full details of the partner's own pass. */
    @PostMapping("/api/partner/passes/verify")
    public ApiResponse<BookingService.VerifyResult> verify(@Valid @RequestBody TokenRequest req) {
        return ApiResponse.ok(booking.verify(req.token(), SecurityUtils.currentPartnerId()));
    }

    /** PV3: marks the pass used; a second scan answers ALREADY_REDEEMED. */
    @PostMapping("/api/partner/passes/redeem")
    public ApiResponse<BookingService.VerifyResult> redeem(@Valid @RequestBody TokenRequest req) {
        BookingService.VerifyResult r = booking.redeem(req.token(), SecurityUtils.currentPartnerId(), SecurityUtils.currentUserIdOrNull());
        return ApiResponse.ok(r, r.message());
    }

    private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String name, boolean download) {
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"" + name + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .body(body);
    }

    @GetMapping(value = "/api/public/passes/{uid}.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> pass(@PathVariable String uid, @RequestParam(defaultValue = "false") boolean download) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"pass.jpg\"")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(service.passImage(uid));
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        String v = s.matches("^[=+\\-@].*") && !s.matches("^\\+\\d+$") ? "'" + s : s; // spreadsheet formula injection
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
