package in.codebasket.cbwa.invoice;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.invoice.InvoiceService.InvoiceDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** PG7, PG8 (partner) and PG13, PG14 (admin). */
@Tag(name = "Invoices")
@RestController
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService service;
    private final InvoiceRepository repository;

    @GetMapping("/api/partner/invoices")
    public ApiResponse<PageResponse<InvoiceDto>> mine(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(SecurityUtils.currentPartnerId(), from, to, page, size, false));
    }

    @GetMapping("/api/partner/invoices/{id}/pdf")
    public ResponseEntity<byte[]> myPdf(@PathVariable Long id) {
        return pdf(id, SecurityUtils.currentPartnerId());
    }

    @GetMapping("/api/admin/invoices")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public ApiResponse<PageResponse<InvoiceDto>> all(@RequestParam(required = false) Long partnerId,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(partnerId, from, to, page, size, true));
    }

    @GetMapping("/api/admin/invoices/{id}/pdf")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public ResponseEntity<byte[]> adminPdf(@PathVariable Long id) {
        return pdf(id, null);
    }

    private ResponseEntity<byte[]> pdf(Long id, Long partnerScope) {
        byte[] bytes = service.pdf(id, partnerScope);
        String no = repository.findById(id).map(Invoice::getInvoiceNo).orElseThrow(() -> AppException.notFound("Invoice"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("invoice-" + no.replace('/', '-') + ".pdf").build().toString())
                .body(bytes);
    }
}
