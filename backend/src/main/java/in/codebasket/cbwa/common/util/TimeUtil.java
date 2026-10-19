package in.codebasket.cbwa.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Stores are UTC; query date params (YYYY-MM-DD) are interpreted as IST calendar days. */
public final class TimeUtil {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private TimeUtil() {
    }

    public static Instant startOfDayIst(LocalDate date) {
        return date.atStartOfDay(IST).toInstant();
    }

    /** Exclusive end: start of the following IST day. */
    public static Instant endOfDayIstExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay(IST).toInstant();
    }

    public static LocalDate todayIst() {
        return LocalDate.now(IST);
    }

    public static LocalDate toIstDate(Instant instant) {
        return instant.atZone(IST).toLocalDate();
    }

    public static Instant startOfMonthIst(YearMonth ym) {
        return ym.atDay(1).atStartOfDay(IST).toInstant();
    }

    public static ZonedDateTime nowIst() {
        return ZonedDateTime.now(IST);
    }

    /** Range helper: from defaults to 30 days ago, to defaults to today (IST). */
    public static Instant[] range(LocalDate from, LocalDate to, int defaultDays) {
        LocalDate t = to != null ? to : todayIst();
        LocalDate f = from != null ? from : t.minusDays(defaultDays - 1L);
        if (f.isAfter(t)) {
            LocalDate tmp = f;
            f = t;
            t = tmp;
        }
        return new Instant[]{startOfDayIst(f), endOfDayIstExclusive(t)};
    }
}
