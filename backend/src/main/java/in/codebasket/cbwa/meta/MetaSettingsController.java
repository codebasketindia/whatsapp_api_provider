package in.codebasket.cbwa.meta;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.meta.dto.MetaSettingsDtos.MetaSettingsDto;
import in.codebasket.cbwa.meta.dto.MetaSettingsDtos.TestResult;
import in.codebasket.cbwa.meta.dto.MetaSettingsDtos.UpdateMetaSettingsRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Meta settings")
@RestController
@RequestMapping("/api/admin/meta/settings")
@RequiredArgsConstructor
public class MetaSettingsController {

    private final MetaSettingsAdminService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_VIEW')")
    public ApiResponse<MetaSettingsDto> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<MetaSettingsDto> update(@Valid @RequestBody UpdateMetaSettingsRequest req) {
        return ApiResponse.ok(service.update(req), "Meta settings saved");
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('PERM_WHATSAPP_MANAGE')")
    public ApiResponse<TestResult> test() {
        TestResult r = service.test();
        return ApiResponse.ok(r, r.appOk() ? "Connection test finished" : "Connection test failed");
    }
}
