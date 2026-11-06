package in.codebasket.cbwa.admin.controller;

import in.codebasket.cbwa.common.entity.Wallet;
import in.codebasket.cbwa.common.service.wallet.WalletQueryService;

import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.common.dto.PageResponse;
import in.codebasket.cbwa.common.utils.TimeUtil;
import in.codebasket.cbwa.common.dto.WalletDtos.WalletDto;
import in.codebasket.cbwa.common.dto.WalletDtos.WalletTxnDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** W1-W3 (admin) and W12-W15 (partner). */
@Tag(name = "Admin - Wallet")
@RestController
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletQueryService walletQueryService;

    public record AdjustRequest(@NotBlank @Pattern(regexp = "CREDIT|DEBIT") String type,
                                @NotNull @DecimalMin(value = "0.0001", message = "Amount must be greater than zero")
                                @DecimalMax(value = "10000000", message = "Amount is too large")
                                @Digits(integer = 8, fraction = 4) BigDecimal amount,
                                @NotBlank @Size(min = 5, max = 500, message = "Please write a remark (5-500 characters)") String remarks) {
    }

    public record ThresholdRequest(@DecimalMin("0") @DecimalMax("10000000") BigDecimal threshold) {
    }

    // ---------------- admin

    @GetMapping("/api/admin/partners/{id}/wallet")
    @PreAuthorize("hasAuthority('PERM_WALLET_VIEW')")
    public ApiResponse<WalletDto> adminWallet(@PathVariable Long id) {
        return ApiResponse.ok(walletQueryService.wallet(id));
    }

    @GetMapping("/api/admin/partners/{id}/wallet/transactions")
    @PreAuthorize("hasAuthority('PERM_WALLET_VIEW')")
    public ApiResponse<PageResponse<WalletTxnDto>> adminTransactions(@PathVariable Long id,
                                                                     @RequestParam(required = false) String type,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                     @RequestParam(defaultValue = "false") boolean includeMessageEntries,
                                                                     @RequestParam(required = false) Integer page,
                                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(walletQueryService.transactions(id, type, from, to, includeMessageEntries, page, size));
    }

    @PostMapping("/api/admin/partners/{id}/wallet/adjust")
    @PreAuthorize("hasAuthority('PERM_WALLET_ADJUST')")
    public ApiResponse<WalletTxnDto> adjust(@PathVariable Long id, @Valid @RequestBody AdjustRequest req) {
        return ApiResponse.ok(walletQueryService.adjust(id, req.type(), req.amount(), req.remarks()),
                "CREDIT".equals(req.type()) ? "Wallet credited" : "Wallet debited");
    }
}
