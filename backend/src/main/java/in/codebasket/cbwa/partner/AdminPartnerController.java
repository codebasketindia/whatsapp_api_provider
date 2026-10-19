package in.codebasket.cbwa.partner;

import in.codebasket.cbwa.auth.dto.AuthDtos.TokenDto;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.audit.AuditController;
import in.codebasket.cbwa.common.audit.AuditController.AuditLogDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.ApiKeyDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.ApiKeyPatchRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.CreatePartnerRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.KycRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.LoginAsRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.OwnNumberRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.PartnerDetailDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.PartnerDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.RatePlanAssignRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.SenderAssignRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.SenderDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.StatusRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.UpdatePartnerRequest;
import in.codebasket.cbwa.partner.dto.PartnerDtos.UsageRowDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** P1-P17 (admin partner management). */
@Tag(name = "Admin - Partners")
@RestController
@RequestMapping("/api/admin/partners")
@RequiredArgsConstructor
public class AdminPartnerController {

    private final PartnerService partnerService;
    private final PartnerSenderService senderService;
    private final PartnerUsageService usageService;
    private final PartnerApiKeyAdminService apiKeyService;
    private final AuditController auditController;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PARTNER_VIEW')")
    public ApiResponse<PageResponse<PartnerDto>> list(@RequestParam(required = false) String search,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long ratePlanId,
                                                      @RequestParam(required = false) Boolean lowBalance,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size,
                                                      @RequestParam(required = false) String sort) {
        return ApiResponse.ok(partnerService.list(search, status, ratePlanId, lowBalance, page, size, sort));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PARTNER_MANAGE')")
    public ApiResponse<PartnerDto> create(@Valid @RequestBody CreatePartnerRequest req) {
        return ApiResponse.created(partnerService.create(req), "Partner created. Login details were emailed");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PARTNER_VIEW')")
    public ApiResponse<PartnerDetailDto> get(@PathVariable Long id) {
        return ApiResponse.ok(partnerService.detail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PARTNER_MANAGE')")
    public ApiResponse<PartnerDto> update(@PathVariable Long id, @Valid @RequestBody UpdatePartnerRequest req) {
        return ApiResponse.ok(partnerService.update(id, req), "Partner updated");
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_PARTNER_MANAGE')")
    public ApiResponse<PartnerDto> status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return ApiResponse.ok(partnerService.setStatus(id, req.status(), req.reason()),
                "SUSPENDED".equals(req.status()) ? "Partner suspended" : "Partner activated");
    }

    @PatchMapping("/{id}/rate-plan")
    @PreAuthorize("hasAuthority('PERM_PRICING_MANAGE')")
    public ApiResponse<PartnerDto> ratePlan(@PathVariable Long id, @Valid @RequestBody RatePlanAssignRequest req) {
        return ApiResponse.ok(partnerService.assignRatePlan(id, req.ratePlanId()), "Rate plan updated");
    }

    @PatchMapping("/{id}/own-number")
    @PreAuthorize("hasAuthority('PERM_PARTNER_MANAGE')")
    public ApiResponse<PartnerDto> ownNumber(@PathVariable Long id, @Valid @RequestBody OwnNumberRequest req) {
        return ApiResponse.ok(partnerService.setOwnNumber(id, req.allowed()),
                req.allowed() ? "Partner can now connect own numbers" : "Connecting own numbers is now off");
    }

    @PatchMapping("/{id}/kyc")
    @PreAuthorize("hasAuthority('PERM_PARTNER_MANAGE')")
    public ApiResponse<PartnerDto> kyc(@PathVariable Long id, @Valid @RequestBody KycRequest req) {
        return ApiResponse.ok(partnerService.setKyc(id, req.kycVerified()), "KYC status updated");
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('PERM_PARTNER_MANAGE')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        partnerService.resetPassword(id);
        return ApiResponse.ok(null, "A temporary password was emailed to the partner");
    }

    @PostMapping("/{id}/login-as")
    @PreAuthorize("hasAuthority('PERM_LOGIN_AS_PARTNER')")
    public ApiResponse<TokenDto> loginAs(@PathVariable Long id, @Valid @RequestBody LoginAsRequest req) {
        return ApiResponse.ok(partnerService.loginAs(id, req.reason()), "You are now viewing the partner's account");
    }

    @GetMapping("/{id}/senders")
    @PreAuthorize("hasAuthority('PERM_PARTNER_VIEW')")
    public ApiResponse<List<SenderDto>> senders(@PathVariable Long id) {
        return ApiResponse.ok(senderService.list(id));
    }

    @PostMapping("/{id}/senders")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<SenderDto> assignSender(@PathVariable Long id, @Valid @RequestBody SenderAssignRequest req) {
        return ApiResponse.ok(senderService.assign(id, req.phoneNumberDbId(), req.isDefault()), "Sender number assigned");
    }

    @DeleteMapping("/{id}/senders/{senderId}")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<Void> unassignSender(@PathVariable Long id, @PathVariable Long senderId) {
        senderService.unassign(id, senderId);
        return ApiResponse.ok(null, "Sender number removed from partner");
    }

    @GetMapping("/{id}/usage")
    @PreAuthorize("hasAuthority('PERM_PARTNER_VIEW')")
    public ApiResponse<List<UsageRowDto>> usage(@PathVariable Long id,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false, defaultValue = "day") String groupBy) {
        partnerService.require(id);
        return ApiResponse.ok(usageService.usage(id, from, to, groupBy));
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
    public ApiResponse<PageResponse<AuditLogDto>> activity(@PathVariable Long id,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        partnerService.require(id);
        return ApiResponse.ok(auditController.search(null, id, null, null, null, null, null, page, size));
    }

    @GetMapping("/{id}/api-keys")
    @PreAuthorize("hasAuthority('PERM_PARTNER_VIEW')")
    public ApiResponse<List<ApiKeyDto>> apiKeys(@PathVariable Long id) {
        return ApiResponse.ok(apiKeyService.list(id));
    }

    @PatchMapping("/{id}/api-keys/{keyId}")
    @PreAuthorize("hasAuthority('PERM_PARTNER_MANAGE')")
    public ApiResponse<ApiKeyDto> patchApiKey(@PathVariable Long id, @PathVariable Long keyId,
                                              @Valid @RequestBody ApiKeyPatchRequest req) {
        return ApiResponse.ok(apiKeyService.patch(id, keyId, req), "API key updated");
    }
}
