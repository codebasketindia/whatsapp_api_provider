package in.codebasket.cbwa.report;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Native aggregation queries for dashboards and reports. Every number is computed from the
 * source-of-truth tables (messages, wallet_transactions, topup_requests, payment_orders, invoices,
 * meta_pricing_analytics) so dashboards always match the message logs and the ledger.
 *
 * Definitions used everywhere:
 *  - sent      = outbound messages whose status reached SENT (SENT, DELIVERED or READ)
 *  - delivered = outbound messages DELIVERED or READ;  read = READ;  failed = FAILED
 *  - charges   = DEBIT rows (MESSAGE + PLATFORM_FEE) minus REFUND rows of already-finalized charges
 *                (a REFUND with held_before = held_after); refunds of open holds were never revenue.
 * Day grouping is by IST calendar day: DATE(CONVERT_TZ(ts,'+00:00','+05:30')).
 */
@Component
@RequiredArgsConstructor
public class ReportQueries {

    public static final String IST_DAY = "DATE(CONVERT_TZ(%s,'+00:00','+05:30'))";
    public static final String IST_MONTH = "DATE_FORMAT(CONVERT_TZ(%s,'+00:00','+05:30'),'%%Y-%%m')";

    static final String SENT = "SUM(CASE WHEN m.status IN ('SENT','DELIVERED','READ') THEN 1 ELSE 0 END)";
    static final String DELIVERED = "SUM(CASE WHEN m.status IN ('DELIVERED','READ') THEN 1 ELSE 0 END)";
    static final String READ = "SUM(CASE WHEN m.status = 'READ' THEN 1 ELSE 0 END)";
    static final String FAILED = "SUM(CASE WHEN m.status = 'FAILED' THEN 1 ELSE 0 END)";
    /** Net finalized charge contribution of one ledger row t. */
    static final String CHARGE_EXPR = """
            CASE WHEN t.type = 'DEBIT' AND t.reference_type IN ('MESSAGE','PLATFORM_FEE') THEN t.amount
                 WHEN t.type = 'REFUND' AND t.reference_type = 'MESSAGE' AND t.held_before = t.held_after THEN -t.amount
                 ELSE 0 END""";

    private final NamedParameterJdbcTemplate jdbc;

    public static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    public static String istDay(String column) {
        return String.format(IST_DAY, column);
    }

    public static String istMonth(String column) {
        return String.format(IST_MONTH, column);
    }

    public List<Map<String, Object>> list(String sql, MapSqlParameterSource params) {
        return jdbc.queryForList(sql, params);
    }

    public Map<String, Object> one(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public long count(String sql, MapSqlParameterSource params) {
        Number n = jdbc.queryForObject(sql, params, Number.class);
        return n == null ? 0 : n.longValue();
    }

    public BigDecimal sum(String sql, MapSqlParameterSource params) {
        BigDecimal v = jdbc.queryForObject(sql, params, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Outbound message counts (total, sent, delivered, read, failed) in [from, to), optional partner. */
    public Map<String, Object> messageCounts(Long partnerId, Instant from, Instant to) {
        String sql = "SELECT COUNT(*) total, COALESCE(" + SENT + ",0) sent, COALESCE(" + DELIVERED + ",0) delivered, COALESCE("
                + READ + ",0) read_count, COALESCE(" + FAILED + ",0) failed FROM messages m"
                + " WHERE m.direction = 'OUTBOUND' AND m.created_at >= :from AND m.created_at < :to"
                + (partnerId != null ? " AND m.partner_id = :pid" : "");
        return one(sql, params(partnerId, from, to));
    }

    /** Net finalized charges (messages + platform fees) in [from, to), optional partner. */
    public BigDecimal charges(Long partnerId, Instant from, Instant to) {
        String sql = "SELECT COALESCE(SUM(" + CHARGE_EXPR + "),0) FROM wallet_transactions t"
                + " WHERE t.created_at >= :from AND t.created_at < :to" + (partnerId != null ? " AND t.partner_id = :pid" : "");
        return sum(sql, params(partnerId, from, to));
    }

    public static MapSqlParameterSource params(Long partnerId, Instant from, Instant to) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (from != null) {
            p.addValue("from", utc(from));
        }
        if (to != null) {
            p.addValue("to", utc(to));
        }
        if (partnerId != null) {
            p.addValue("pid", partnerId);
        }
        return p;
    }

    public static long l(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    public static BigDecimal d(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
