package in.codebasket.cbwa.waba;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.sender.dto.SenderDtos.ConnectResultDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.ManualConnectRequest;
import in.codebasket.cbwa.waba.dto.WabaDtos.AddWabaRequest;
import in.codebasket.cbwa.waba.dto.WabaDtos.HealthOverviewDto;
import in.codebasket.cbwa.waba.dto.WabaDtos.TokenRequest;
import in.codebasket.cbwa.waba.dto.WabaDtos.TokenTestDto;
import in.codebasket.cbwa.waba.dto.WabaDtos.WabaDetailDto;
import in.codebasket.cbwa.waba.dto.WabaDtos.WabaDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - WhatsApp accounts")
@RestController
@RequiredArgsConstructor
public class WabaController {

    private final WabaService service;

    @GetMapping("/api/admin/whatsapp/accounts")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_VIEW')")
    public ApiResponse<PageResponse<WabaDto>> list(@RequestParam(required = false) String ownerType,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String tokenStatus,
                                                   @RequestParam(required = false) Long partnerId,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(ownerType, status, tokenStatus, partnerId, page, size));
    }

    @PostMapping("/api/admin/whatsapp/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<WabaDetailDto> add(@Valid @RequestBody AddWabaRequest req) {
        return ApiResponse.created(service.addPlatformWaba(req.wabaId().trim(), req.accessToken().trim()), "WhatsApp account added");
    }

    @GetMapping("/api/admin/whatsapp/accounts/{id}")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_VIEW')")
    public ApiResponse<WabaDetailDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PutMapping("/api/admin/whatsapp/accounts/{id}/token")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<WabaDto> token(@PathVariable Long id, @Valid @RequestBody TokenRequest req) {
        return ApiResponse.ok(service.updateToken(id, req.accessToken().trim()), "Access token updated");
    }

    @PostMapping("/api/admin/whatsapp/accounts/{id}/sync")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<WabaDto> sync(@PathVariable Long id) {
        return ApiResponse.ok(service.sync(id), "Synced with Meta");
    }

    @PostMapping("/api/admin/whatsapp/accounts/{id}/test")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<TokenTestDto> test(@PathVariable Long id) {
        TokenTestDto r = service.testToken(id);
        return ApiResponse.ok(r, r.tokenValid() ? "Token is valid" : "Token is not valid");
    }

    @PostMapping("/api/admin/whatsapp/accounts/{id}/subscribe")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<WabaDto> subscribe(@PathVariable Long id) {
        return ApiResponse.ok(service.subscribe(id), "Webhook subscription active");
    }

    @PostMapping("/api/admin/whatsapp/accounts/{id}/share-credit-line")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<WabaDto> shareCreditLine(@PathVariable Long id) {
        return ApiResponse.ok(service.shareCreditLine(id), "Credit line shared");
    }

    @PostMapping("/api/admin/whatsapp/manual-connect")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<ConnectResultDto> manualConnect(@Valid @RequestBody ManualConnectRequest req) {
        return ApiResponse.ok(service.manualConnect(req), "Number connected");
    }

    @GetMapping("/api/admin/meta/health")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_VIEW')")
    public ApiResponse<HealthOverviewDto> health() {
        return ApiResponse.ok(service.health());
    }
}
