package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.CampaignDto;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.CampaignRequest;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.LaunchRequest;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.PrepareResult;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.RecipientDto;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.UploadDto;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;

/** CP1-CP11 (partner). */
@Tag(name = "Partner - Campaigns")
@RestController
@RequestMapping("/api/partner/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignControlService controlService;
    private final CampaignQueryService queryService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadDto> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(campaignService.upload(pid(), file), "File uploaded");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CampaignDto> create(@Valid @RequestBody CampaignRequest req) {
        return ApiResponse.created(campaignService.create(pid(), req), "Campaign saved as draft");
    }

    @PutMapping("/{id}")
    public ApiResponse<CampaignDto> update(@PathVariable Long id, @Valid @RequestBody CampaignRequest req) {
        return ApiResponse.ok(campaignService.update(pid(), id, req), "Campaign updated");
    }

    @PostMapping("/{id}/prepare")
    public ApiResponse<PrepareResult> prepare(@PathVariable Long id) {
        return ApiResponse.ok(campaignService.prepare(pid(), id));
    }

    @PostMapping("/{id}/launch")
    public ApiResponse<CampaignDto> launch(@PathVariable Long id, @RequestBody(required = false) LaunchRequest req) {
        CampaignDto dto = campaignService.launch(pid(), id, req == null ? null : req.scheduleAt(), req == null ? null : req.confirmCost());
        return ApiResponse.ok(dto, "SCHEDULED".equals(dto.status()) ? "Campaign scheduled" : "Campaign started");
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<CampaignDto> pause(@PathVariable Long id) {
        return ApiResponse.ok(controlService.pause(pid(), id, null), "Campaign paused");
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<CampaignDto> resume(@PathVariable Long id) {
        return ApiResponse.ok(controlService.resume(pid(), id), "Campaign resumed");
    }

    public record RescheduleRequest(@jakarta.validation.constraints.NotNull Instant scheduledAt) {
    }

    @PostMapping("/{id}/reschedule")
    public ApiResponse<CampaignDto> reschedule(@PathVariable Long id, @jakarta.validation.Valid @RequestBody RescheduleRequest req) {
        return ApiResponse.ok(campaignService.reschedule(pid(), id, req.scheduledAt()), "Campaign time changed");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<CampaignDto> cancel(@PathVariable Long id) {
        return ApiResponse.ok(controlService.cancel(pid(), id, null), "Campaign cancelled");
    }

    @GetMapping
    public ApiResponse<PageResponse<CampaignDto>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String search,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(queryService.list(pid(), null, status, search, from, to, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<CampaignDto> get(@PathVariable Long id) {
        return ApiResponse.ok(queryService.get(pid(), id));
    }

    @GetMapping("/{id}/recipients")
    public ApiResponse<PageResponse<RecipientDto>> recipients(@PathVariable Long id,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String search,
                                                              @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(queryService.recipients(pid(), id, status, search, page, size));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> report(@PathVariable Long id, @RequestParam(defaultValue = "xlsx") String format) {
        CampaignQueryService.Report r = queryService.report(pid(), id, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(r.filename()).build().toString())
                .contentType(MediaType.parseMediaType(r.contentType()))
                .body(r.body());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        campaignService.delete(pid(), id);
        return ApiResponse.ok(null, "Draft deleted");
    }

    private static Long pid() {
        return SecurityUtils.currentPartnerId();
    }
}
