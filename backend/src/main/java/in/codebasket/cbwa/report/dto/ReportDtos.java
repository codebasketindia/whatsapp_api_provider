package in.codebasket.cbwa.report.dto;

import java.math.BigDecimal;
import java.util.Map;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record PartnerRef(Long id, String partnerCode, String businessName) {
    }

    /** RP1. charged = net finalized charges; refunds = all REFUND rows; topups = credits from top-ups + payments. */
    public record PartnerUsageRow(PartnerRef partner, long messages, Map<String, Long> byCategory, BigDecimal charged,
                                  BigDecimal refunds, BigDecimal topups) {
    }

    /** RP2 / RP3: charged = Σ price of messages with charge_status CHARGED. */
    public record CategoryRow(String category, long count, BigDecimal charged) {
    }

    public record CountryRow(String country, long count, BigDecimal charged) {
    }

    /** RP4. */
    public record RevenueRow(String period, BigDecimal gatewayCollections, BigDecimal manualTopups, BigDecimal gst,
                             BigDecimal messageCharges, BigDecimal platformFees) {
    }

    /** RP5. */
    public record ReconciliationRow(String date, String sender, String country, String category, long metaVolume,
                                    BigDecimal metaCost, long ourCount, BigDecimal ourCharge, BigDecimal profit) {
    }

    /** RP6 (and admin partner usage). */
    public record UsageRow(String key, long sent, long delivered, long read, long failed, BigDecimal charged) {
    }
}
