package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.campaign.dto.CampaignDtos.CampaignDto;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.ReasonRequest;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** CP12-CP13: all partners' campaigns for admins. */
@Tag(name = "Admin - Campaigns")
@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
public class AdminCampaignController {

    private final CampaignQueryService queryService;
    private final CampaignControlService controlService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_CAMPAIGN_VIEW')")
    public ApiResponse<PageResponse<CampaignDto>> list(@RequestParam(required = false) Long partnerId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String search,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(queryService.list(null, partnerId, status, search, from, to, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_CAMPAIGN_VIEW')")
    public ApiResponse<CampaignDto> get(@PathVariable Long id) {
        return ApiResponse.ok(queryService.get(null, id));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('PERM_CAMPAIGN_MANAGE')")
    public ApiResponse<CampaignDto> pause(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonRequest req) {
        return ApiResponse.ok(controlService.pause(null, id, req == null ? null : req.reason()), "Campaign paused");
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PERM_CAMPAIGN_MANAGE')")
    public ApiResponse<CampaignDto> cancel(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonRequest req) {
        return ApiResponse.ok(controlService.cancel(null, id, req == null ? null : req.reason()), "Campaign cancelled");
    }
}
