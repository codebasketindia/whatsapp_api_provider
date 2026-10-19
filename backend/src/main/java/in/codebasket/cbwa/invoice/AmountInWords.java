package in.codebasket.cbwa.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Indian numbering words: "Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six and Fifty Paise Only". */
public final class AmountInWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private AmountInWords() {
    }

    public static String rupees(BigDecimal amount) {
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = a.longValue();
        int paise = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String words = rupees == 0 ? "Zero" : words(rupees);
        return "Rupees " + words + (paise > 0 ? " and " + twoDigits(paise) + " Paise" : "") + " Only";
    }

    static String words(long n) {
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        n %= 10_000_000;
        long lakh = n / 100_000;
        n %= 100_000;
        long thousand = n / 1000;
        n %= 1000;
        long hundred = n / 100;
        long rest = n % 100;
        if (crore > 0) {
            sb.append(words(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            sb.append(twoDigits((int) lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            sb.append(twoDigits((int) thousand)).append(" Thousand ");
        }
        if (hundred > 0) {
            sb.append(ONES[(int) hundred]).append(" Hundred ");
        }
        if (rest > 0) {
            sb.append(twoDigits((int) rest));
        }
        return sb.toString().trim();
    }

    private static String twoDigits(int n) {
        if (n < 20) {
            return ONES[n];
        }
        return (TENS[n / 10] + " " + ONES[n % 10]).trim();
    }
}
