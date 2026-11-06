package in.codebasket.cbwa.partner.controller;

import in.codebasket.cbwa.common.service.sender.SenderAssignmentService;
import in.codebasket.cbwa.common.service.sender.SenderService;

import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.common.dto.PageResponse;
import in.codebasket.cbwa.common.dto.SenderDtos.AssignRequest;
import in.codebasket.cbwa.common.dto.SenderDtos.CompleteSignupRequest;
import in.codebasket.cbwa.common.dto.SenderDtos.ConnectResultDto;
import in.codebasket.cbwa.common.dto.SenderDtos.EmbeddedSignupConfigDto;
import in.codebasket.cbwa.common.dto.SenderDtos.ReasonRequest;
import in.codebasket.cbwa.common.dto.SenderDtos.SenderDetailDto;
import in.codebasket.cbwa.common.dto.SenderDtos.SenderDto;
import in.codebasket.cbwa.common.dto.SenderDtos.SignupEventRequest;
import in.codebasket.cbwa.common.dto.SenderDtos.UpdateSenderRequest;
import in.codebasket.cbwa.common.service.waba.EmbeddedSignupService;
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
