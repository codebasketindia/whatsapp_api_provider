package in.codebasket.cbwa.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private MoneyUtil() {
    }

    public static BigDecimal scale(BigDecimal v) {
        return v == null ? ZERO : v.setScale(4, RoundingMode.HALF_UP);
    }

    /** Rounds to whole paise (2 decimals) - used for amounts charged through a gateway. */
    public static BigDecimal rupees(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static long toPaise(BigDecimal rupees) {
        return rupees.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    public static BigDecimal fromPaise(long paise) {
        return scale(BigDecimal.valueOf(paise).movePointLeft(2));
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return scale(amount.multiply(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    public static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    /** JSON representation for the API: money is sent as a string with 4 decimals. */
    public static String str(BigDecimal v) {
        return scale(v).toPlainString();
    }
}
