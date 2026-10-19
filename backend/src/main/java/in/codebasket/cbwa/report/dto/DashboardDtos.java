package in.codebasket.cbwa.report.dto;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Counts(long total, long sent, long delivered, long read, long failed) {
    }

    public record PartnerCounts(long total, long sent, long delivered, long read, long failed, BigDecimal spend) {
    }

    public record PartnersSummary(long total, long active) {
    }

    public record Revenue(BigDecimal today, BigDecimal month) {
    }

    public record SenderAlert(Long senderId, String displayPhoneNumber, String verifiedName, String partnerName,
                              String qualityRating, boolean paused, String pauseReason, String tokenStatus, String reason) {
    }

    public record AdminDashboardDto(PartnersSummary partners, BigDecimal walletTotal, BigDecimal walletHeld, Counts today,
                                    Revenue revenue, long pendingTopups, List<SenderAlert> senderAlerts) {
    }

    public record WalletSummary(BigDecimal balance, BigDecimal held, BigDecimal lowBalanceThreshold, boolean isLow, String currency) {
    }

    public record SenderSummary(Long id, String display, String verifiedName, String quality, String status, boolean paused) {
    }

    /** deliveryRate7d is a percentage (0–100, 2 decimals) of delivered+read over sent+failed outbound. */
    public record PartnerDashboardDto(WalletSummary wallet, PartnerCounts today, BigDecimal deliveryRate7d,
                                      List<SenderSummary> senders, boolean lowBalance) {
    }

    public record MessagesPoint(String date, long total, long sent, long delivered, long read, long failed) {
    }

    public record RevenuePoint(String date, BigDecimal revenue, BigDecimal collections) {
    }

    public record PartnerPoint(String date, long sent, long delivered, long read, long failed, BigDecimal spend) {
    }
}
