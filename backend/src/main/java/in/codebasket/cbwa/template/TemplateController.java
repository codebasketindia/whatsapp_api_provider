package in.codebasket.cbwa.template;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.template.dto.TemplateDtos.AnalyticsDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.HeaderUploadDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.LibraryItemDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.LibraryUseRequest;
import in.codebasket.cbwa.template.dto.TemplateDtos.ReviewRequest;
import in.codebasket.cbwa.template.dto.TemplateDtos.SyncRequest;
import in.codebasket.cbwa.template.dto.TemplateDtos.SyncResult;
import in.codebasket.cbwa.template.dto.TemplateDtos.TemplateDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.TemplateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/** TP1-TP18 */
@Tag(name = "Templates")
@RestController
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService service;
    private final TemplateSyncService syncService;
    private final TemplateInsightsService insights;

    // ---------------- partner ----------------

    @GetMapping("/api/partner/templates")
    public ApiResponse<PageResponse<TemplateDto>> list(@RequestParam(required = false) Long wabaId,
                                                       @RequestParam(required = false) Long senderId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String category,
                                                       @RequestParam(required = false) String language,
                                                       @RequestParam(required = false) String search,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.partnerList(SecurityUtils.currentPartnerId(), wabaId, senderId, status, category, language,
                search, page, size));
    }

    @PostMapping("/api/partner/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateDto> create(@Valid @RequestBody TemplateRequest req) {
        TemplateDto t = service.create(SecurityUtils.currentPartnerId(), req);
        return ApiResponse.created(t, message(t));
    }

    @GetMapping("/api/partner/templates/{id}")
    public ApiResponse<TemplateDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.partnerGet(SecurityUtils.currentPartnerId(), id));
    }

    @PutMapping("/api/partner/templates/{id}")
    public ApiResponse<TemplateDto> update(@PathVariable Long id, @Valid @RequestBody TemplateRequest req) {
        TemplateDto t = service.update(SecurityUtils.currentPartnerId(), id, req);
        return ApiResponse.ok(t, message(t));
    }

    @DeleteMapping("/api/partner/templates/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(SecurityUtils.currentPartnerId(), id);
        return ApiResponse.ok(null, "Template deleted");
    }

    @PostMapping("/api/partner/templates/{id}/submit")
    public ApiResponse<TemplateDto> submit(@PathVariable Long id) {
        TemplateDto t = service.submit(SecurityUtils.currentPartnerId(), id);
        return ApiResponse.ok(t, message(t));
    }

    @PostMapping("/api/partner/templates/sync")
    public ApiResponse<SyncResult> sync(@RequestBody SyncRequest req) {
        Long partnerId = SecurityUtils.currentPartnerId();
        return ApiResponse.ok(syncService.sync(service.partnerWaba(partnerId, req.wabaDbId(), req.senderId()), partnerId),
                "Templates refreshed");
    }

    @GetMapping("/api/partner/templates/{id}/analytics")
    public ApiResponse<AnalyticsDto> analytics(@PathVariable Long id,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(insights.analytics(service.loadForPartner(SecurityUtils.currentPartnerId(), id), from, to));
    }

    @GetMapping("/api/partner/template-library")
    public ApiResponse<List<LibraryItemDto>> library(@RequestParam(required = false) String search,
                                                     @RequestParam(required = false) String topic,
                                                     @RequestParam(required = false) String usecase,
                                                     @RequestParam(required = false) String industry,
                                                     @RequestParam(required = false) String language) {
        return ApiResponse.ok(insights.library(SecurityUtils.currentPartnerId(), search, topic, usecase, industry, language));
    }

    @PostMapping("/api/partner/template-library/use")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateDto> useLibrary(@Valid @RequestBody LibraryUseRequest req) {
        TemplateDto t = insights.useLibrary(SecurityUtils.currentPartnerId(), req);
        return ApiResponse.created(t, message(t));
    }

    @PostMapping(value = "/api/partner/media/template-header", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<HeaderUploadDto> headerUpload(@RequestPart("file") MultipartFile file,
                                                     @RequestParam(required = false) Long wabaDbId,
                                                     @RequestParam(required = false) Long senderId) {
        return ApiResponse.ok(service.uploadHeader(SecurityUtils.currentPartnerId(), wabaDbId, senderId, file), "Sample uploaded");
    }

    // ---------------- admin ----------------

    @GetMapping("/api/admin/templates")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_VIEW')")
    public ApiResponse<PageResponse<TemplateDto>> adminList(@RequestParam(required = false) Long partnerId,
                                                            @RequestParam(required = false) Long wabaId,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String category,
                                                            @RequestParam(required = false) String search,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.adminList(partnerId, wabaId, status, category, search, page, size));
    }

    @GetMapping("/api/admin/templates/review-queue")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_REVIEW')")
    public ApiResponse<PageResponse<TemplateDto>> reviewQueue(@RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.reviewQueue(page, size));
    }

    @GetMapping("/api/admin/templates/{id}")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_VIEW')")
    public ApiResponse<TemplateDto> adminGet(@PathVariable Long id) {
        return ApiResponse.ok(service.adminGet(id));
    }

    @GetMapping("/api/admin/templates/{id}/analytics")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_VIEW')")
    public ApiResponse<AnalyticsDto> adminAnalytics(@PathVariable Long id,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(insights.analytics(service.load(id), from, to));
    }

    @PostMapping("/api/admin/templates/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_REVIEW')")
    public ApiResponse<TemplateDto> approve(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest req) {
        return ApiResponse.ok(service.approveAndSubmit(id, req == null ? null : req.note()), "Template sent to WhatsApp for approval");
    }

    @PostMapping("/api/admin/templates/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_REVIEW')")
    public ApiResponse<TemplateDto> reject(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        String reason = req.reason() != null ? req.reason() : req.note();
        return ApiResponse.ok(service.reject(id, reason), "Template returned to the partner");
    }

    @PostMapping("/api/admin/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_REVIEW')")
    public ApiResponse<TemplateDto> adminCreate(@Valid @RequestBody TemplateRequest req) {
        TemplateDto t = service.create(null, req);
        return ApiResponse.created(t, message(t));
    }

    @PutMapping("/api/admin/templates/{id}")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_REVIEW')")
    public ApiResponse<TemplateDto> adminUpdate(@PathVariable Long id, @Valid @RequestBody TemplateRequest req) {
        TemplateDto t = service.update(null, id, req);
        return ApiResponse.ok(t, message(t));
    }

    @PostMapping("/api/admin/templates/sync")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_REVIEW')")
    public ApiResponse<SyncResult> adminSync(@RequestBody SyncRequest req) {
        return ApiResponse.ok(syncService.sync(service.wabaForAdmin(req.wabaDbId(), req.senderId()), null), "Templates synced");
    }

    @DeleteMapping("/api/admin/templates/{id}")
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_REVIEW')")
    public ApiResponse<Void> adminDelete(@PathVariable Long id) {
        service.delete(null, id);
        return ApiResponse.ok(null, "Template deleted");
    }

    @PostMapping(value = "/api/admin/media/template-header", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_TEMPLATE_REVIEW')")
    public ApiResponse<HeaderUploadDto> adminHeaderUpload(@RequestPart("file") MultipartFile file,
                                                          @RequestParam(required = false) Long wabaDbId,
                                                          @RequestParam(required = false) Long senderId) {
        return ApiResponse.ok(service.uploadHeader(null, wabaDbId, senderId, file), "Sample uploaded");
    }

    private static String message(TemplateDto t) {
        return switch (t.status()) {
            case "PENDING_REVIEW" -> "Template sent for review";
            case "PENDING" -> "Template submitted to WhatsApp for approval";
            case "APPROVED" -> "Template approved";
            case "REJECTED" -> "Template rejected by WhatsApp";
            default -> "Template saved";
        };
    }
}
