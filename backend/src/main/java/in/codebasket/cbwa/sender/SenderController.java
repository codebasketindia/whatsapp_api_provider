package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.sender.dto.SenderDtos.AssignRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.CompleteSignupRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.ConnectResultDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.EmbeddedSignupConfigDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.ReasonRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDetailDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.SignupEventRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.UpdateSenderRequest;
import in.codebasket.cbwa.waba.EmbeddedSignupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** M13-M17, E1-E9. */
@Tag(name = "Senders")
@RestController
@RequiredArgsConstructor
public class SenderController {

    private final SenderService senderService;
    private final SenderAssignmentService assignmentService;
    private final EmbeddedSignupService signupService;

    // ---------------- admin ----------------

    @GetMapping("/api/admin/senders")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_VIEW')")
    public ApiResponse<PageResponse<SenderDto>> adminList(@RequestParam(required = false) String senderType,
                                                          @RequestParam(required = false) Long partnerId,
                                                          @RequestParam(required = false) String quality,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Boolean assigned,
                                                          @RequestParam(required = false) String search,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(senderService.adminList(senderType, partnerId, quality, status, assigned, search, page, size));
    }

    @GetMapping("/api/admin/senders/{id}")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_VIEW')")
    public ApiResponse<SenderDetailDto> adminDetail(@PathVariable Long id) {
        return ApiResponse.ok(senderService.adminDetail(id));
    }

    @PatchMapping("/api/admin/senders/{id}")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<SenderDto> adminUpdate(@PathVariable Long id, @Valid @RequestBody UpdateSenderRequest req) {
        return ApiResponse.ok(senderService.adminUpdate(id, req), "Sender updated");
    }

    @PostMapping("/api/admin/senders/{id}/assign")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<SenderDto> assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return ApiResponse.ok(assignmentService.assign(id, req.partnerId(), req.isDefault()), "Number assigned");
    }

    @PostMapping("/api/admin/senders/{id}/unassign")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<SenderDto> unassign(@PathVariable Long id) {
        return ApiResponse.ok(assignmentService.unassign(id), "Number unassigned");
    }

    @PostMapping("/api/admin/senders/{id}/disconnect")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<SenderDto> adminDisconnect(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonRequest req) {
        return ApiResponse.ok(senderService.adminDisconnect(id, req == null ? null : req.reason()), "Number disconnected");
    }

    // ---------------- partner ----------------

    @GetMapping("/api/partner/senders")
    public ApiResponse<List<SenderDto>> partnerList() {
        return ApiResponse.ok(senderService.partnerList(SecurityUtils.currentPartnerId()));
    }

    @GetMapping("/api/partner/senders/{id}")
    public ApiResponse<SenderDetailDto> partnerDetail(@PathVariable Long id) {
        return ApiResponse.ok(senderService.partnerDetail(SecurityUtils.currentPartnerId(), id));
    }

    @PatchMapping("/api/partner/senders/{id}/default")
    public ApiResponse<SenderDto> makeDefault(@PathVariable Long id) {
        return ApiResponse.ok(senderService.makeDefault(SecurityUtils.currentPartnerId(), id), "Default sender updated");
    }

    @PostMapping("/api/partner/senders/{id}/disconnect")
    public ApiResponse<SenderDto> partnerDisconnect(@PathVariable Long id) {
        return ApiResponse.ok(senderService.partnerDisconnect(SecurityUtils.currentPartnerId(), id), "Number disconnected");
    }

    @GetMapping("/api/partner/embedded-signup/config")
    public ApiResponse<EmbeddedSignupConfigDto> signupConfig() {
        return ApiResponse.ok(signupService.config(SecurityUtils.currentPartnerId()));
    }

    @PostMapping("/api/partner/embedded-signup/complete")
    public ApiResponse<ConnectResultDto> signupComplete(@Valid @RequestBody CompleteSignupRequest req) {
        return ApiResponse.ok(signupService.complete(SecurityUtils.currentPartnerId(), req), "WhatsApp number connected");
    }

    @PostMapping("/api/partner/embedded-signup/events")
    public ApiResponse<Void> signupEvent(@Valid @RequestBody SignupEventRequest req) {
        signupService.logEvent(SecurityUtils.currentPartnerId(), req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/partner/senders/{id}/reconnect")
    public ApiResponse<ConnectResultDto> reconnect(@PathVariable Long id, @Valid @RequestBody CompleteSignupRequest req) {
        return ApiResponse.ok(signupService.reconnect(SecurityUtils.currentPartnerId(), id, req), "Number reconnected");
    }
}
