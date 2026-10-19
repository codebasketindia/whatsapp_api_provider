package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.publicapi.dto.ApiKeyDtos.ApiKeyDto;
import in.codebasket.cbwa.publicapi.dto.ApiKeyDtos.CreateApiKeyRequest;
import in.codebasket.cbwa.publicapi.dto.ApiKeyDtos.RegenerateRequest;
import in.codebasket.cbwa.publicapi.dto.ApiKeyDtos.UpdateApiKeyRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Partner - API keys")
@RestController
@RequestMapping("/api/partner/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTNER')")
public class ApiKeyController {

    private final ApiKeyService service;

    @GetMapping
    public ApiResponse<List<ApiKeyDto>> list() {
        return ApiResponse.ok(service.list(SecurityUtils.currentPartnerId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApiKeyDto> create(@Valid @RequestBody CreateApiKeyRequest req) {
        return ApiResponse.created(service.create(SecurityUtils.currentPartnerId(), req),
                "API key created. Copy the secret now - it will not be shown again");
    }

    @PatchMapping("/{id}")
    public ApiResponse<ApiKeyDto> update(@PathVariable Long id, @Valid @RequestBody UpdateApiKeyRequest req) {
        return ApiResponse.ok(service.update(SecurityUtils.currentPartnerId(), id, req), "API key updated");
    }

    @PostMapping("/{id}/regenerate")
    public ApiResponse<ApiKeyDto> regenerate(@PathVariable Long id, @Valid @RequestBody RegenerateRequest req) {
        return ApiResponse.ok(service.regenerate(SecurityUtils.currentPartnerId(), id, req.password()),
                "New secret generated. Copy it now - it will not be shown again");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(SecurityUtils.currentPartnerId(), id);
        return ApiResponse.ok(null, "API key deleted");
    }
}
