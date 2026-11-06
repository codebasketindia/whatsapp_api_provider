package in.codebasket.cbwa.partner.controller;

import in.codebasket.cbwa.common.service.pricing.CreditPackService;

import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.common.service.pricing.CreditPackService.CreditPackDto;
import in.codebasket.cbwa.common.service.pricing.CreditPackService.CreditPackRequest;
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

    @GetMapping("/api/partner/credit-packs")
    public ApiResponse<List<CreditPackDto>> partnerPacks() {
        return ApiResponse.ok(service.list(true));
    }
}
