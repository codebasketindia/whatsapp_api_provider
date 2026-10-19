package in.codebasket.cbwa.report;

import in.codebasket.cbwa.report.dto.ReportDtos.CategoryRow;
import in.codebasket.cbwa.report.dto.ReportDtos.CountryRow;
import in.codebasket.cbwa.report.dto.ReportDtos.PartnerUsageRow;
import in.codebasket.cbwa.report.dto.ReportDtos.ReconciliationRow;
import in.codebasket.cbwa.report.dto.ReportDtos.RevenueRow;
import in.codebasket.cbwa.report.dto.ReportDtos.UsageRow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static in.codebasket.cbwa.report.ReportTable.money;
import static in.codebasket.cbwa.report.ReportTable.num;
import static in.codebasket.cbwa.report.ReportTable.text;

/** Converts report rows into export tables (same data as the JSON responses). */
public final class ReportTables {

    private ReportTables() {
    }

    static String period(LocalDate from, LocalDate to) {
        return "Period: " + (from == null ? "last 30 days" : from) + (to == null ? "" : " to " + to) + " (IST)";
    }

    public static ReportTable partners(List<PartnerUsageRow> rows, String period) {
        List<ReportTable.Column> cols = new ArrayList<>(List.of(text("Partner code"), text("Business"), num("Messages")));
        ReportService.CATEGORIES.forEach(c -> cols.add(num(c)));
        cols.addAll(List.of(money("Charged (₹)"), money("Refunds (₹)"), money("Top-ups (₹)")));
        List<List<Object>> data = new ArrayList<>();
        for (PartnerUsageRow r : rows) {
            List<Object> row = new ArrayList<>(List.of(r.partner().partnerCode(), r.partner().businessName(), r.messages()));
            ReportService.CATEGORIES.forEach(c -> row.add(r.byCategory().getOrDefault(c, 0L)));
            row.addAll(List.of(r.charged(), r.refunds(), r.topups()));
            data.add(row);
        }
        return new ReportTable("Partner usage & revenue", period, cols, data);
    }

    public static ReportTable categories(List<CategoryRow> rows, String period) {
        return new ReportTable("Category-wise usage", period, List.of(text("Category"), num("Messages"), money("Charged (₹)")),
                rows.stream().map(r -> List.<Object>of(r.category(), r.count(), r.charged())).toList());
    }

    public static ReportTable countries(List<CountryRow> rows, String period) {
        return new ReportTable("Country-wise usage", period, List.of(text("Country"), num("Messages"), money("Charged (₹)")),
                rows.stream().map(r -> List.<Object>of(r.country(), r.count(), r.charged())).toList());
    }

    public static ReportTable revenue(List<RevenueRow> rows, String period) {
        return new ReportTable("Revenue", period, List.of(text("Period"), money("Gateway collections (₹)"), money("Manual top-ups (₹)"),
                money("GST (₹)"), money("Message charges (₹)"), money("Platform fees (₹)")),
                rows.stream().map(r -> List.<Object>of(r.period(), r.gatewayCollections(), r.manualTopups(), r.gst(),
                        r.messageCharges(), r.platformFees())).toList());
    }

    public static ReportTable reconciliation(List<ReconciliationRow> rows, String period) {
        return new ReportTable("Meta cost reconciliation", period, List.of(text("Date"), text("Sender"), text("Country"), text("Category"),
                num("Meta volume"), money("Meta cost"), num("Our count"), money("Our charge (₹)"), money("Profit")),
                rows.stream().map(r -> List.<Object>of(r.date(), r.sender(), r.country(), r.category(), r.metaVolume(), r.metaCost(),
                        r.ourCount(), r.ourCharge(), r.profit())).toList());
    }

    public static ReportTable usage(List<UsageRow> rows, String groupBy, String period) {
        return new ReportTable("Usage by " + (groupBy == null ? "day" : groupBy), period, List.of(text(groupBy == null ? "day" : groupBy),
                num("Sent"), num("Delivered"), num("Read"), num("Failed"), money("Charged (₹)")),
                rows.stream().map(r -> List.<Object>of(r.key(), r.sent(), r.delivered(), r.read(), r.failed(), r.charged())).toList());
    }
}
