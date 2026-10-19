package in.codebasket.cbwa.report;

import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.report.dto.DashboardDtos.AdminDashboardDto;
import in.codebasket.cbwa.report.dto.DashboardDtos.Counts;
import in.codebasket.cbwa.report.dto.DashboardDtos.MessagesPoint;
import in.codebasket.cbwa.report.dto.DashboardDtos.PartnerCounts;
import in.codebasket.cbwa.report.dto.DashboardDtos.PartnerDashboardDto;
import in.codebasket.cbwa.report.dto.DashboardDtos.PartnerPoint;
import in.codebasket.cbwa.report.dto.DashboardDtos.PartnersSummary;
import in.codebasket.cbwa.report.dto.DashboardDtos.Revenue;
import in.codebasket.cbwa.report.dto.DashboardDtos.RevenuePoint;
import in.codebasket.cbwa.report.dto.DashboardDtos.SenderAlert;
import in.codebasket.cbwa.report.dto.DashboardDtos.SenderSummary;
import in.codebasket.cbwa.report.dto.DashboardDtos.WalletSummary;
import in.codebasket.cbwa.wallet.LowBalanceMonitor;
import in.codebasket.cbwa.wallet.Wallet;
import in.codebasket.cbwa.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.codebasket.cbwa.report.ReportQueries.d;
import static in.codebasket.cbwa.report.ReportQueries.l;

/** DB1–DB4. All figures come straight from messages / ledger rows (see ReportQueries). */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int MAX_CHART_DAYS = 366;

    private final ReportQueries q;
    private final WalletRepository walletRepository;
    private final LowBalanceMonitor lowBalanceMonitor;

    @Transactional(readOnly = true)
    public AdminDashboardDto admin() {
        LocalDate today = TimeUtil.todayIst();
        Instant dayStart = TimeUtil.startOfDayIst(today);
        Instant dayEnd = TimeUtil.endOfDayIstExclusive(today);
        Instant monthStart = TimeUtil.startOfMonthIst(YearMonth.from(today));
        MapSqlParameterSource none = new MapSqlParameterSource();

        Map<String, Object> partners = q.one("SELECT COUNT(*) total, COALESCE(SUM(status = 'ACTIVE'),0) active FROM partners", none);
        Map<String, Object> wallets = q.one("SELECT COALESCE(SUM(balance),0) bal, COALESCE(SUM(held_amount),0) held FROM wallets", none);
        long pendingTopups = q.count("SELECT COUNT(*) FROM topup_requests WHERE status = 'PENDING'", none);

        return new AdminDashboardDto(
                new PartnersSummary(l(partners.get("total")), l(partners.get("active"))),
                MoneyUtil.scale(d(wallets.get("bal"))), MoneyUtil.scale(d(wallets.get("held"))),
                counts(q.messageCounts(null, dayStart, dayEnd)),
                new Revenue(MoneyUtil.scale(q.charges(null, dayStart, dayEnd)), MoneyUtil.scale(q.charges(null, monthStart, dayEnd))),
                pendingTopups, senderAlerts(null));
    }

    /** DB2: metric=messages -> MessagesPoint per IST day; metric=revenue -> RevenuePoint per IST day. */
    @Transactional(readOnly = true)
    public List<?> adminChart(LocalDate from, LocalDate to, String metric) {
        Instant[] r = range(from, to);
        List<LocalDate> days = days(r);
        if ("revenue".equalsIgnoreCase(metric)) {
            Map<String, BigDecimal> revenue = byDay("SELECT " + ReportQueries.istDay("t.created_at") + " d, COALESCE(SUM("
                    + ReportQueries.CHARGE_EXPR + "),0) v FROM wallet_transactions t WHERE t.created_at >= :from AND t.created_at < :to GROUP BY d",
                    ReportQueries.params(null, r[0], r[1]));
            Map<String, BigDecimal> gateway = byDay("SELECT " + ReportQueries.istDay("o.paid_at") + " d, COALESCE(SUM(o.total_amount),0) v"
                    + " FROM payment_orders o WHERE o.status = 'PAID' AND o.paid_at >= :from AND o.paid_at < :to GROUP BY d",
                    ReportQueries.params(null, r[0], r[1]));
            Map<String, BigDecimal> manual = byDay("SELECT " + ReportQueries.istDay("x.reviewed_at") + " d, COALESCE(SUM(x.amount),0) v"
                    + " FROM topup_requests x WHERE x.status = 'APPROVED' AND x.reviewed_at >= :from AND x.reviewed_at < :to GROUP BY d",
                    ReportQueries.params(null, r[0], r[1]));
            List<RevenuePoint> out = new ArrayList<>();
            for (LocalDate day : days) {
                String k = day.toString();
                out.add(new RevenuePoint(k, MoneyUtil.scale(revenue.getOrDefault(k, BigDecimal.ZERO)),
                        MoneyUtil.scale(gateway.getOrDefault(k, BigDecimal.ZERO).add(manual.getOrDefault(k, BigDecimal.ZERO)))));
            }
            return out;
        }
        if (metric != null && !metric.isBlank() && !"messages".equalsIgnoreCase(metric)) {
            throw AppException.field("metric", "metric must be messages or revenue");
        }
        Map<String, Map<String, Object>> rows = messageRowsByDay(null, r);
        List<MessagesPoint> out = new ArrayList<>();
        for (LocalDate day : days) {
            Map<String, Object> m = rows.getOrDefault(day.toString(), Map.of());
            out.add(new MessagesPoint(day.toString(), l(m.get("total")), l(m.get("sent")), l(m.get("delivered")),
                    l(m.get("read_count")), l(m.get("failed"))));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PartnerDashboardDto partner(Long partnerId) {
        LocalDate today = TimeUtil.todayIst();
        Instant dayStart = TimeUtil.startOfDayIst(today);
        Instant dayEnd = TimeUtil.endOfDayIstExclusive(today);
        Wallet w = walletRepository.findByPartnerId(partnerId).orElseThrow(() -> AppException.notFound("Wallet"));
        BigDecimal threshold = lowBalanceMonitor.thresholdFor(partnerId);
        boolean low = threshold.signum() > 0 && w.getBalance().compareTo(threshold) < 0;
        WalletSummary wallet = new WalletSummary(w.getBalance(), w.getHeldAmount(), MoneyUtil.scale(threshold), low, w.getCurrency());

        Map<String, Object> c = q.messageCounts(partnerId, dayStart, dayEnd);
        PartnerCounts counts = new PartnerCounts(l(c.get("total")), l(c.get("sent")), l(c.get("delivered")), l(c.get("read_count")),
                l(c.get("failed")), MoneyUtil.scale(q.charges(partnerId, dayStart, dayEnd)));

        Map<String, Object> week = q.messageCounts(partnerId, TimeUtil.startOfDayIst(today.minusDays(6)), dayEnd);
        long denominator = l(week.get("sent")) + l(week.get("failed"));
        BigDecimal rate = denominator == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(l(week.get("delivered")) * 100).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);

        List<SenderSummary> senders = q.list("""
                SELECT pn.id, pn.display_phone_number, pn.verified_name, pn.quality_rating, pn.status, pn.is_paused
                  FROM partner_phone_numbers a JOIN phone_numbers pn ON pn.id = a.phone_number_id
                 WHERE a.partner_id = :pid AND a.active = TRUE ORDER BY a.is_default DESC, pn.id""",
                new MapSqlParameterSource("pid", partnerId)).stream()
                .map(m -> new SenderSummary(l(m.get("id")), (String) m.get("display_phone_number"), (String) m.get("verified_name"),
                        (String) m.get("quality_rating"), (String) m.get("status"), Boolean.TRUE.equals(bool(m.get("is_paused")))))
                .toList();
        return new PartnerDashboardDto(wallet, counts, rate, senders, low);
    }

    @Transactional(readOnly = true)
    public List<PartnerPoint> partnerChart(Long partnerId, LocalDate from, LocalDate to) {
        Instant[] r = range(from, to);
        Map<String, Map<String, Object>> rows = messageRowsByDay(partnerId, r);
        Map<String, BigDecimal> spend = byDay("SELECT " + ReportQueries.istDay("t.created_at") + " d, COALESCE(SUM("
                + ReportQueries.CHARGE_EXPR + "),0) v FROM wallet_transactions t WHERE t.partner_id = :pid AND t.created_at >= :from"
                + " AND t.created_at < :to GROUP BY d", ReportQueries.params(partnerId, r[0], r[1]));
        List<PartnerPoint> out = new ArrayList<>();
        for (LocalDate day : days(r)) {
            String k = day.toString();
            Map<String, Object> m = rows.getOrDefault(k, Map.of());
            out.add(new PartnerPoint(k, l(m.get("sent")), l(m.get("delivered")), l(m.get("read_count")), l(m.get("failed")),
                    MoneyUtil.scale(spend.getOrDefault(k, BigDecimal.ZERO))));
        }
        return out;
    }

    /** Senders needing attention: quality YELLOW/RED, paused, or WABA token invalid/expired. */
    public List<SenderAlert> senderAlerts(Long partnerId) {
        String sql = """
                SELECT pn.id, pn.display_phone_number, pn.verified_name, pn.quality_rating, pn.is_paused, pn.pause_reason,
                       w.token_status, p.business_name
                  FROM phone_numbers pn
                  JOIN whatsapp_accounts w ON w.id = pn.whatsapp_account_id
                  LEFT JOIN partner_phone_numbers a ON a.phone_number_id = pn.id AND a.active = TRUE
                  LEFT JOIN partners p ON p.id = a.partner_id
                 WHERE pn.status <> 'DISCONNECTED'
                   AND (pn.quality_rating IN ('YELLOW','RED') OR pn.is_paused = TRUE OR w.token_status IN ('INVALID','EXPIRED'))
                """ + (partnerId != null ? " AND a.partner_id = :pid" : "") + " ORDER BY pn.id LIMIT 50";
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (partnerId != null) {
            p.addValue("pid", partnerId);
        }
        return q.list(sql, p).stream().map(m -> {
            boolean paused = Boolean.TRUE.equals(bool(m.get("is_paused")));
            String token = (String) m.get("token_status");
            String quality = (String) m.get("quality_rating");
            String reason = ("INVALID".equals(token) || "EXPIRED".equals(token)) ? "Access token " + token.toLowerCase() + " - reconnect needed"
                    : paused ? "Sender paused" + (m.get("pause_reason") != null ? ": " + m.get("pause_reason") : "")
                    : "Quality rating " + quality;
            return new SenderAlert(l(m.get("id")), (String) m.get("display_phone_number"), (String) m.get("verified_name"),
                    (String) m.get("business_name"), quality, paused, (String) m.get("pause_reason"), token, reason);
        }).toList();
    }

    private Map<String, Map<String, Object>> messageRowsByDay(Long partnerId, Instant[] r) {
        String sql = "SELECT " + ReportQueries.istDay("m.created_at") + " d, COUNT(*) total, " + ReportQueries.SENT + " sent, "
                + ReportQueries.DELIVERED + " delivered, " + ReportQueries.READ + " read_count, " + ReportQueries.FAILED + " failed"
                + " FROM messages m WHERE m.direction = 'OUTBOUND' AND m.created_at >= :from AND m.created_at < :to"
                + (partnerId != null ? " AND m.partner_id = :pid" : "") + " GROUP BY d";
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (Map<String, Object> row : q.list(sql, ReportQueries.params(partnerId, r[0], r[1]))) {
            out.put(String.valueOf(row.get("d")), row);
        }
        return out;
    }

    private Map<String, BigDecimal> byDay(String sql, MapSqlParameterSource p) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (Map<String, Object> row : q.list(sql, p)) {
            out.put(String.valueOf(row.get("d")), d(row.get("v")));
        }
        return out;
    }

    private static Counts counts(Map<String, Object> m) {
        return new Counts(l(m.get("total")), l(m.get("sent")), l(m.get("delivered")), l(m.get("read_count")), l(m.get("failed")));
    }

    static Instant[] range(LocalDate from, LocalDate to) {
        Instant[] r = TimeUtil.range(from, to, 30);
        if (ChronoUnit.DAYS.between(r[0], r[1]) > MAX_CHART_DAYS) {
            throw AppException.field("from", "Choose a range of at most one year");
        }
        return r;
    }

    static List<LocalDate> days(Instant[] r) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate d = TimeUtil.toIstDate(r[0]);
        LocalDate end = TimeUtil.toIstDate(r[1]);
        while (d.isBefore(end)) {
            out.add(d);
            d = d.plusDays(1);
        }
        return out;
    }

    static Boolean bool(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        return ((Number) o).intValue() != 0;
    }
}
