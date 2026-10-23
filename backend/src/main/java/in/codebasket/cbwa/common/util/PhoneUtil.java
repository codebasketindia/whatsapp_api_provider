package in.codebasket.cbwa.common.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Optional;

/**
 * Normalises phone numbers to WhatsApp format: E.164 digits without '+' (e.g. 919876543210).
 * Numbers without a country code are parsed with the default region (IN unless given).
 */
public final class PhoneUtil {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneUtil() {
    }

    public record Parsed(String digits, String regionCode) {
    }

    public static Optional<Parsed> parse(String raw, String defaultRegion) {
        if (raw == null) {
            return Optional.empty();
        }
        String cleaned = raw.replaceAll("[^0-9+]", "");
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        // Numbers like 919876543210 (already with country code, no plus) are common in CSV uploads.
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        } else if (!cleaned.startsWith("+") && !cleaned.startsWith("0") && cleaned.length() > 10) {
            // 919876543210 - country code without "+" (common in CSV uploads)
            cleaned = "+" + cleaned;
        }
        // a single leading 0 (09876543210) is the national trunk prefix and is parsed with the default region
        try {
            Phonenumber.PhoneNumber pn = UTIL.parse(cleaned, defaultRegion == null ? "IN" : defaultRegion);
            if (!UTIL.isValidNumber(pn)) {
                return Optional.empty();
            }
            String digits = UTIL.format(pn, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1);
            return Optional.of(new Parsed(digits, UTIL.getRegionCodeForNumber(pn)));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    public static Optional<Parsed> parse(String raw) {
        return parse(raw, "IN");
    }

    /** Region (ISO-2) for a number already in WhatsApp digit format; "ZZ" if unknown. */
    public static String regionOfDigits(String digits) {
        return parse("+" + digits, "IN").map(Parsed::regionCode).orElse("ZZ");
    }

    public static String mask(String digits) {
        if (digits == null || digits.length() < 6) {
            return digits;
        }
        return digits.substring(0, 4) + "*".repeat(digits.length() - 6) + digits.substring(digits.length() - 2);
    }
}
