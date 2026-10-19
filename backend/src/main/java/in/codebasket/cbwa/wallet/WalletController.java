package in.codebasket.cbwa.wallet;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.wallet.dto.WalletDtos.WalletDto;
import in.codebasket.cbwa.wallet.dto.WalletDtos.WalletTxnDto;
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
@Tag(name = "Wallet")
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletQueryService walletQueryService;

    public record AdjustRequest(@NotBlank @Pattern(regexp = "CREDIT|DEBIT") String type,
                                @NotNull @DecimalMin(value = "0.0001", message = "Amount must be greater than zero")
                                @DecimalMax(value = "10000000", message = "Amount is too large")
                                @Digits(integer = 8, fraction = 4) BigDecimal amount,
                                @NotBlank @Size(min = 5, max = 500, message = "Please write a remark (5-500 characters)") String remarks) {
    }

    public record ThresholdRequest(@NotNull @DecimalMin("0") @DecimalMax("10000000") BigDecimal threshold) {
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

    // ---------------- partner

    @GetMapping("/api/partner/wallet")
    public ApiResponse<WalletDto> myWallet() {
        return ApiResponse.ok(walletQueryService.wallet(SecurityUtils.currentPartnerId()));
    }

    @PutMapping("/api/partner/wallet/low-balance-threshold")
    public ApiResponse<WalletDto> threshold(@Valid @RequestBody ThresholdRequest req) {
        return ApiResponse.ok(walletQueryService.updateThreshold(SecurityUtils.currentPartnerId(), req.threshold()),
                "Low balance alert updated");
    }

    @GetMapping("/api/partner/wallet/transactions")
    public ApiResponse<PageResponse<WalletTxnDto>> myTransactions(@RequestParam(required = false) String type,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                  @RequestParam(defaultValue = "false") boolean includeMessageEntries,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(walletQueryService.transactions(SecurityUtils.currentPartnerId(), type, from, to,
                includeMessageEntries, page, size));
    }

    @GetMapping("/api/partner/wallet/transactions/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "false") boolean includeMessageEntries,
                                         @RequestParam(defaultValue = "csv") String format) {
        List<WalletTxnDto> rows = walletQueryService.exportRows(SecurityUtils.currentPartnerId(), type, from, to, includeMessageEntries);
        List<List<Object>> data = rows.stream().map(t -> List.<Object>of(
                t.createdAt().atZone(TimeUtil.IST).toLocalDateTime().toString().replace('T', ' '), t.uid(), t.type(),
                t.amount(), t.balanceBefore(), t.balanceAfter(), t.heldAfter(), t.referenceType(),
                t.referenceId() == null ? "" : t.referenceId(), t.remarks() == null ? "" : t.remarks())).toList();
        return TabularExport.response(format, "passbook-" + TimeUtil.todayIst(),
                List.of("Date (IST)", "Transaction ID", "Type", "Amount", "Balance before", "Balance after", "On hold after",
                        "Reference type", "Reference", "Remarks"), data);
    }
}
