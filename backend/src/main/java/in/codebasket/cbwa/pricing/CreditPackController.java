package in.codebasket.cbwa.pricing;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.pricing.CreditPackService.CreditPackDto;
import in.codebasket.cbwa.pricing.CreditPackService.CreditPackRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** W8-W11 (admin) and W16 (partner). */
@Tag(name = "Credit packs")
@RestController
@RequiredArgsConstructor
public class CreditPackController {

    private final CreditPackService service;

    @GetMapping("/api/admin/credit-packs")
    @PreAuthorize("hasAuthority('PERM_PRICING_MANAGE')")
    public ApiResponse<List<CreditPackDto>> list(@RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(service.list(active));
    }

    @PostMapping("/api/admin/credit-packs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PRICING_MANAGE')")
    public ApiResponse<CreditPackDto> create(@Valid @RequestBody CreditPackRequest req) {
        return ApiResponse.created(service.create(req), "Credit pack created");
    }

    @PutMapping("/api/admin/credit-packs/{id}")
    @PreAuthorize("hasAuthority('PERM_PRICING_MANAGE')")
    public ApiResponse<CreditPackDto> update(@PathVariable Long id, @Valid @RequestBody CreditPackRequest req) {
        return ApiResponse.ok(service.update(id, req), "Credit pack updated");
    }

    @DeleteMapping("/api/admin/credit-packs/{id}")
    @PreAuthorize("hasAuthority('PERM_PRICING_MANAGE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        boolean deleted = service.delete(id);
        return ApiResponse.ok(null, deleted ? "Credit pack deleted" : "Credit pack was bought before, so it was deactivated instead");
    }

    @GetMapping("/api/partner/credit-packs")
    public ApiResponse<List<CreditPackDto>> partnerPacks() {
        return ApiResponse.ok(service.list(true));
    }
}
