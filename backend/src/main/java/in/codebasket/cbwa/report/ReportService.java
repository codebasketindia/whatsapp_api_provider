package in.codebasket.cbwa.report;

import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.report.dto.ReportDtos.CategoryRow;
import in.codebasket.cbwa.report.dto.ReportDtos.CountryRow;
import in.codebasket.cbwa.report.dto.ReportDtos.PartnerRef;
import in.codebasket.cbwa.report.dto.ReportDtos.PartnerUsageRow;
import in.codebasket.cbwa.report.dto.ReportDtos.ReconciliationRow;
import in.codebasket.cbwa.report.dto.ReportDtos.RevenueRow;
import in.codebasket.cbwa.report.dto.ReportDtos.UsageRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static in.codebasket.cbwa.report.ReportQueries.d;
import static in.codebasket.cbwa.report.ReportQueries.istDay;
import static in.codebasket.cbwa.report.ReportQueries.istMonth;
import static in.codebasket.cbwa.report.ReportQueries.l;

/** RP1–RP6 data. Exports are produced from the same rows by ReportTables. */
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final List<String> CATEGORIES = List.of("MARKETING", "UTILITY", "AUTHENTICATION", "AUTHENTICATION_INTERNATIONAL", "SERVICE");

    private final ReportQueries q;

    /** RP1, one page of partners (all partners when size is null - used for exports). */
    @Transactional(readOnly = true)
    public PageResponse<PartnerUsageRow> partners(LocalDate from, LocalDate to, Integer page, Integer size) {
        Instant[] r = DashboardService.range(from, to);
        long total = q.count("SELECT COUNT(*) FROM partners", new MapSqlParameterSource());
        MapSqlParameterSource pp = new MapSqlParameterSource();
        String limit = "";
        int p = 0;
        int s = (int) Math.max(total, 1);
        if (size != null) {
            var pageable = Paging.of(page, size);
            p = pageable.getPageNumber();
            s = pageable.getPageSize();
            limit = " LIMIT :limit OFFSET :offset";
            pp.addValue("limit", s).addValue("offset", (long) p * s);
        }
        List<Map<String, Object>> partners = q.list("SELECT id, partner_code, business_name FROM partners ORDER BY business_name, id" + limit, pp);
        List<Long> ids = partners.stream().map(m -> l(m.get("id"))).toList();
        List<PartnerUsageRow> rows = new ArrayList<>();
        if (!ids.isEmpty()) {
            MapSqlParameterSource params = ReportQueries.params(null, r[0], r[1]).addValue("ids", ids);
            Map<Long, Map<String, Long>> cats = new HashMap<>();
            Map<Long, Long> msgs = new HashMap<>();
            for (Map<String, Object> m : q.list("""
                    SELECT m.partner_id pid, COALESCE(m.category,'UNKNOWN') cat, COUNT(*) c FROM messages m
                     WHERE m.direction = 'OUTBOUND' AND m.created_at >= :from AND m.created_at < :to AND m.partner_id IN (:ids)
                     GROUP BY m.partner_id, cat""", params)) {
                Long pid = l(m.get("pid"));
                cats.computeIfAbsent(pid, k -> new LinkedHashMap<>()).put((String) m.get("cat"), l(m.get("c")));
                msgs.merge(pid, l(m.get("c")), Long::sum);
            }
            Map<Long, Map<String, Object>> money = new HashMap<>();
            for (Map<String, Object> m : q.list("SELECT t.partner_id pid, COALESCE(SUM(" + ReportQueries.CHARGE_EXPR + "),0) charged,"
                    + " COALESCE(SUM(CASE WHEN t.type = 'REFUND' THEN t.amount ELSE 0 END),0) refunds,"
                    + " COALESCE(SUM(CASE WHEN t.type = 'CREDIT' AND t.reference_type IN ('TOPUP_REQUEST','PAYMENT_ORDER') THEN t.amount ELSE 0 END),0) topups"
                    + " FROM wallet_transactions t WHERE t.created_at >= :from AND t.created_at < :to AND t.partner_id IN (:ids)"
                    + " GROUP BY t.partner_id", params)) {
                money.put(l(m.get("pid")), m);
            }
            for (Map<String, Object> pr : partners) {
                Long pid = l(pr.get("id"));
                Map<String, Long> byCat = new LinkedHashMap<>();
                CATEGORIES.forEach(c -> byCat.put(c, 0L));
                byCat.putAll(cats.getOrDefault(pid, Map.of()));
                Map<String, Object> mo = money.getOrDefault(pid, Map.of());
                rows.add(new PartnerUsageRow(new PartnerRef(pid, (String) pr.get("partner_code"), (String) pr.get("business_name")),
                        msgs.getOrDefault(pid, 0L), byCat, MoneyUtil.scale(d(mo.get("charged"))), MoneyUtil.scale(d(mo.get("refunds"))),
                        MoneyUtil.scale(d(mo.get("topups")))));
            }
        }
        int totalPages = s == 0 ? 0 : (int) Math.ceil(total / (double) s);
        return new PageResponse<>(rows, p, s, total, totalPages);
    }

    @Transactional(readOnly = true)
    public List<CategoryRow> categories(LocalDate from, LocalDate to, Long partnerId) {
        return grouped("COALESCE(m.category,'UNKNOWN')", from, to, partnerId).stream()
                .map(m -> new CategoryRow((String) m.get("k"), l(m.get("c")), MoneyUtil.scale(d(m.get("charged"))))).toList();
    }

    @Transactional(readOnly = true)
    public List<CountryRow> countries(LocalDate from, LocalDate to, Long partnerId) {
        return grouped("COALESCE(m.country_code,'ZZ')", from, to, partnerId).stream()
                .map(m -> new CountryRow((String) m.get("k"), l(m.get("c")), MoneyUtil.scale(d(m.get("charged"))))).toList();
    }

    private List<Map<String, Object>> grouped(String keyExpr, LocalDate from, LocalDate to, Long partnerId) {
        Instant[] r = DashboardService.range(from, to);
        return q.list("SELECT " + keyExpr + " k, COUNT(*) c, COALESCE(SUM(CASE WHEN m.charge_status = 'CHARGED' THEN m.price ELSE 0 END),0) charged"
                + " FROM messages m WHERE m.direction = 'OUTBOUND' AND m.created_at >= :from AND m.created_at < :to"
                + (partnerId != null ? " AND m.partner_id = :pid" : "") + " GROUP BY k ORDER BY c DESC, k",
                ReportQueries.params(partnerId, r[0], r[1]));
    }

    /** RP4. gst = tax on invoices issued in the period (gateway payments and manual top-ups). */
    @Transactional(readOnly = true)
    public List<RevenueRow> revenue(LocalDate from, LocalDate to, String groupBy) {
        boolean month = "month".equalsIgnoreCase(groupBy);
        if (groupBy != null && !groupBy.isBlank() && !month && !"day".equalsIgnoreCase(groupBy)) {
            throw AppException.field("groupBy", "groupBy must be day or month");
        }
        Instant[] r = DashboardService.range(from, to);
        MapSqlParameterSource p = ReportQueries.params(null, r[0], r[1]);
        Map<String, BigDecimal[]> acc = new TreeMap<>();
        String period = month ? "%s" : "%s";
        accumulate(acc, 0, q.list("SELECT " + bucket(month, "o.paid_at") + " k, COALESCE(SUM(o.total_amount),0) v FROM payment_orders o"
                + " WHERE o.status = 'PAID' AND o.paid_at >= :from AND o.paid_at < :to GROUP BY k", p));
        accumulate(acc, 1, q.list("SELECT " + bucket(month, "x.reviewed_at") + " k, COALESCE(SUM(x.amount),0) v FROM topup_requests x"
                + " WHERE x.status = 'APPROVED' AND x.reviewed_at >= :from AND x.reviewed_at < :to GROUP BY k", p));
        MapSqlParameterSource dp = new MapSqlParameterSource()
                .addValue("fromDate", TimeUtil.toIstDate(r[0])).addValue("toDate", TimeUtil.toIstDate(r[1]));
        accumulate(acc, 2, q.list("SELECT " + (month ? "DATE_FORMAT(i.invoice_date,'%Y-%m')" : "i.invoice_date") + " k,"
                + " COALESCE(SUM(i.cgst_amount + i.sgst_amount + i.igst_amount),0) v FROM invoices i"
                + " WHERE i.invoice_date >= :fromDate AND i.invoice_date < :toDate GROUP BY k", dp));
        accumulate(acc, 3, q.list("SELECT " + bucket(month, "t.created_at") + " k, COALESCE(SUM(CASE"
                + " WHEN t.type = 'DEBIT' AND t.reference_type = 'MESSAGE' THEN t.amount"
                + " WHEN t.type = 'REFUND' AND t.reference_type = 'MESSAGE' AND t.held_before = t.held_after THEN -t.amount ELSE 0 END),0) v"
                + " FROM wallet_transactions t WHERE t.created_at >= :from AND t.created_at < :to GROUP BY k", p));
        accumulate(acc, 4, q.list("SELECT " + bucket(month, "t.created_at") + " k, COALESCE(SUM(t.amount),0) v FROM wallet_transactions t"
                + " WHERE t.type = 'DEBIT' AND t.reference_type = 'PLATFORM_FEE' AND t.created_at >= :from AND t.created_at < :to GROUP BY k", p));
        if (!month) {
            for (LocalDate day : DashboardService.days(r)) {
                acc.computeIfAbsent(day.toString(), k -> zeros());
            }
        }
        List<RevenueRow> out = new ArrayList<>();
        acc.forEach((k, v) -> out.add(new RevenueRow(String.format(period, k), MoneyUtil.scale(v[0]), MoneyUtil.scale(v[1]),
                MoneyUtil.scale(v[2]), MoneyUtil.scale(v[3]), MoneyUtil.scale(v[4]))));
        return out;
    }

    /**
     * RP5: Meta cost (meta_pricing_analytics) vs our charges per IST day / sender number / country /
     * category. Our side counts outbound messages that reached SENT and the price finally charged.
     */
    @Transactional(readOnly = true)
    public List<ReconciliationRow> reconciliation(LocalDate from, LocalDate to, Long wabaId) {
        Instant[] r = DashboardService.range(from, to);
        MapSqlParameterSource p = ReportQueries.params(null, r[0], r[1])
                .addValue("fromDate", TimeUtil.toIstDate(r[0])).addValue("toDate", TimeUtil.toIstDate(r[1]));
        if (wabaId != null) {
            p.addValue("waba", wabaId);
        }
        Map<String, Object[]> rows = new TreeMap<>();
        for (Map<String, Object> m : q.list("""
                SELECT a.date d, a.phone_number ph, a.country c, a.pricing_category cat, SUM(a.volume) v, SUM(a.cost) cost
                  FROM meta_pricing_analytics a
                 WHERE a.date >= :fromDate AND a.date < :toDate""" + (wabaId != null ? " AND a.whatsapp_account_id = :waba" : "")
                + " GROUP BY d, ph, c, cat", p)) {
            String cat = metaCategory((String) m.get("cat"));
            Object[] row = rows.computeIfAbsent(key(m.get("d"), digits((String) m.get("ph")), (String) m.get("c"), cat),
                    k -> new Object[]{String.valueOf(m.get("d")), digits((String) m.get("ph")), m.get("c"), cat, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO});
            row[4] = (Long) row[4] + l(m.get("v"));
            row[5] = ((BigDecimal) row[5]).add(d(m.get("cost")));
        }
        for (Map<String, Object> m : q.list("SELECT " + istDay("m.created_at") + " d, pn.wa_number ph, COALESCE(m.country_code,'ZZ') c,"
                + " COALESCE(m.category,'SERVICE') cat, COUNT(*) n,"
                + " COALESCE(SUM(CASE WHEN m.charge_status = 'CHARGED' THEN m.price ELSE 0 END),0) charged"
                + " FROM messages m JOIN phone_numbers pn ON pn.id = m.phone_number_id"
                + " WHERE m.direction = 'OUTBOUND' AND m.status IN ('SENT','DELIVERED','READ')"
                + " AND m.created_at >= :from AND m.created_at < :to"
                + (wabaId != null ? " AND pn.whatsapp_account_id = :waba" : "") + " GROUP BY d, ph, c, cat", p)) {
            String ph = digits((String) m.get("ph"));
            String cat = (String) m.get("cat");
            Object[] row = rows.computeIfAbsent(key(m.get("d"), ph, (String) m.get("c"), cat),
                    k -> new Object[]{String.valueOf(m.get("d")), ph, m.get("c"), cat, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO});
            row[6] = (Long) row[6] + l(m.get("n"));
            row[7] = ((BigDecimal) row[7]).add(d(m.get("charged")));
        }
        List<ReconciliationRow> out = new ArrayList<>();
        for (Object[] row : rows.values()) {
            BigDecimal metaCost = MoneyUtil.scale((BigDecimal) row[5]);
            BigDecimal ours = MoneyUtil.scale((BigDecimal) row[7]);
            out.add(new ReconciliationRow((String) row[0], (String) row[1], (String) row[2], (String) row[3], (Long) row[4],
                    metaCost, (Long) row[6], ours, MoneyUtil.scale(ours.subtract(metaCost))));
        }
        return out;
    }

    /** RP6 (partner) - also usable for admin with partnerId. */
    @Transactional(readOnly = true)
    public List<UsageRow> usage(Long partnerId, LocalDate from, LocalDate to, String groupBy) {
        String g = groupBy == null || groupBy.isBlank() ? "day" : groupBy.toLowerCase();
        String keyExpr = switch (g) {
            case "day" -> istDay("m.created_at");
            case "category" -> "COALESCE(m.category,'UNKNOWN')";
            case "country" -> "COALESCE(m.country_code,'ZZ')";
            case "sender" -> "COALESCE(pn.display_phone_number, pn.phone_number_id)";
            default -> throw AppException.field("groupBy", "groupBy must be day, category, country or sender");
        };
        Instant[] r = DashboardService.range(from, to);
        Map<String, UsageRow> byKey = new LinkedHashMap<>();
        for (Map<String, Object> m : q.list("SELECT " + keyExpr + " k, " + ReportQueries.SENT + " sent, " + ReportQueries.DELIVERED
                + " delivered, " + ReportQueries.READ + " read_count, " + ReportQueries.FAILED + " failed,"
                + " COALESCE(SUM(CASE WHEN m.charge_status = 'CHARGED' THEN m.price ELSE 0 END),0) charged"
                + " FROM messages m JOIN phone_numbers pn ON pn.id = m.phone_number_id"
                + " WHERE m.direction = 'OUTBOUND' AND m.created_at >= :from AND m.created_at < :to"
                + (partnerId != null ? " AND m.partner_id = :pid" : "") + " GROUP BY k ORDER BY k", ReportQueries.params(partnerId, r[0], r[1]))) {
            String k = String.valueOf(m.get("k"));
            byKey.put(k, new UsageRow(k, l(m.get("sent")), l(m.get("delivered")), l(m.get("read_count")), l(m.get("failed")),
                    MoneyUtil.scale(d(m.get("charged")))));
        }
        if ("day".equals(g)) {
            List<UsageRow> out = new ArrayList<>();
            for (LocalDate day : DashboardService.days(r)) {
                out.add(byKey.getOrDefault(day.toString(), new UsageRow(day.toString(), 0, 0, 0, 0, MoneyUtil.ZERO)));
            }
            return out;
        }
        return new ArrayList<>(byKey.values());
    }

    /** Meta pricing categories -> our MessageCategory names (MARKETING_LITE is billed as marketing). */
    static String metaCategory(String c) {
        if (c == null) {
            return "UNKNOWN";
        }
        String u = c.toUpperCase();
        return u.startsWith("MARKETING") ? "MARKETING" : u;
    }

    private static String bucket(boolean month, String col) {
        return month ? istMonth(col) : istDay(col);
    }

    private static void accumulate(Map<String, BigDecimal[]> acc, int idx, List<Map<String, Object>> rows) {
        for (Map<String, Object> m : rows) {
            acc.computeIfAbsent(String.valueOf(m.get("k")), k -> zeros())[idx] = d(m.get("v"));
        }
    }

    private static BigDecimal[] zeros() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static String key(Object d, String ph, String c, String cat) {
        return d + "|" + ph + "|" + c + "|" + cat;
    }

    static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    public static final Set<String> FORMATS = Set.of("json", "xlsx", "pdf", "csv");
}
