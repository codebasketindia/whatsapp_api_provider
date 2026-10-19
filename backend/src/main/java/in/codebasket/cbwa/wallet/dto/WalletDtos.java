package in.codebasket.cbwa.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class WalletDtos {

    private WalletDtos() {
    }

    public record WalletDto(BigDecimal balance, BigDecimal held, BigDecimal lowBalanceThreshold, boolean isLow, String currency) {
    }

    public record CreatedByRef(String uid, String name) {
    }

    public record WalletTxnDto(String uid, String type, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                               BigDecimal heldBefore, BigDecimal heldAfter, String referenceType, String referenceId,
                               String remarks, CreatedByRef createdBy, Instant createdAt) {
    }
}
