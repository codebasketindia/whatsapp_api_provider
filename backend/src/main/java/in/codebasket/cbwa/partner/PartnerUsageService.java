package in.codebasket.cbwa.partner;

import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.partner.dto.PartnerDtos.UsageRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * P14 usage for one partner, grouped by IST day / category / country / sender.
 * sent = reached Meta (SENT, DELIVERED or READ), delivered = DELIVERED or READ, charged = final charges.
 */
@Service
@RequiredArgsConstructor
public class PartnerUsageService {

    /** Whitelisted group expressions (never interpolate client input into SQL). */
    private static final Map<String, String> GROUPS = Map.of(
            "day", "DATE_FORMAT(CONVERT_TZ(m.created_at, '+00:00', '+05:30'), '%Y-%m-%d')",
            "category", "COALESCE(m.category, 'SERVICE')",
            "country", "COALESCE(m.country_code, 'ZZ')",
            "sender", "CAST(m.phone_number_id AS CHAR)");

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<UsageRowDto> usage(Long partnerId, LocalDate from, LocalDate to, String groupBy) {
        String key = groupBy == null || groupBy.isBlank() ? "day" : groupBy;
        String expr = GROUPS.get(key);
        if (expr == null) {
            throw AppException.field("groupBy", "Use day, category, country or sender");
        }
        Instant[] range = TimeUtil.range(from, to, 30);
        if (range[1].getEpochSecond() - range[0].getEpochSecond() > 366L * 86400) {
            throw AppException.field("from", "Please choose a range of one year or less");
        }
        String label = "sender".equals(key)
                ? "coalesce(p.display_phone_number, p.phone_number_id)"
                : expr;
        String sql = "select " + expr + " as k, max(" + label + ") as lbl, count(*) as total,"
                + " sum(case when m.status in ('SENT','DELIVERED','READ') then 1 else 0 end) as sent,"
                + " sum(case when m.status in ('DELIVERED','READ') then 1 else 0 end) as delivered,"
                + " sum(case when m.status = 'READ' then 1 else 0 end) as rd,"
                + " sum(case when m.status = 'FAILED' then 1 else 0 end) as failed,"
                + " coalesce(sum(case when m.charge_status = 'CHARGED' then m.price else 0 end), 0) as charged"
                + " from messages m left join phone_numbers p on p.id = m.phone_number_id where m.partner_id = :pid and m.direction = 'OUTBOUND'"
                + " and m.created_at >= :from and m.created_at < :to group by k order by k";
        MapSqlParameterSource params = new MapSqlParameterSource("pid", partnerId)
                .addValue("from", Timestamp.from(range[0])).addValue("to", Timestamp.from(range[1]));
        return jdbc.query(sql, params, (rs, i) -> new UsageRowDto(rs.getString("k"), rs.getString("lbl"), rs.getLong("total"),
                rs.getLong("sent"), rs.getLong("delivered"), rs.getLong("rd"), rs.getLong("failed"),
                MoneyUtil.scale(new BigDecimal(rs.getString("charged")))));
    }
}
