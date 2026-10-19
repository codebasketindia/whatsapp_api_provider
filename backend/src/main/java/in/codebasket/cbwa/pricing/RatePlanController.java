package in.codebasket.cbwa.pricing;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.pricing.dto.RatePlanDtos.CloneRequest;
import in.codebasket.cbwa.pricing.dto.RatePlanDtos.RatePlanDto;
import in.codebasket.cbwa.pricing.dto.RatePlanDtos.RatePlanRequest;
import in.codebasket.cbwa.pricing.dto.RatePlanDtos.StatusRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** R1-R6 */
@Tag(name = "Admin - Rate plans")
@RestController
@RequestMapping("/api/admin/rate-plans")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_PRICING_MANAGE')")
public class RatePlanController {

    private final RatePlanService service;

    @GetMapping
    public ApiResponse<PageResponse<RatePlanDto>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "true") boolean includeCustom,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(status, includeCustom, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RatePlanDto> create(@Valid @RequestBody RatePlanRequest req) {
        return ApiResponse.created(service.create(req), "Rate plan created");
    }

    @GetMapping("/{id}")
    public ApiResponse<RatePlanDto> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<RatePlanDto> update(@PathVariable Long id, @Valid @RequestBody RatePlanRequest req) {
        return ApiResponse.ok(service.update(id, req), "Rate plan saved");
    }

    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RatePlanDto> clone(@PathVariable Long id, @Valid @RequestBody CloneRequest req) {
        return ApiResponse.created(service.clone(id, req.name(), req.partnerId()), "Rate plan copied");
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<RatePlanDto> status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return ApiResponse.ok(service.setStatus(id, req.status()), "Status updated");
    }
}
