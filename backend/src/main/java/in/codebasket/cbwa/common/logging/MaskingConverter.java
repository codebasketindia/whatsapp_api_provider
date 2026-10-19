package in.codebasket.cbwa.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Last line of defence: masks secrets that might slip into log messages
 * (access tokens, bearer tokens, PINs, secrets, passwords, UPI ids).
 */
public class MaskingConverter extends MessageConverter {

    private static final Pattern[] PATTERNS = {
            Pattern.compile("(?i)(access_token|token|secret|password|pin|key_secret|signature|app_secret)([\"']?\\s*[:=]\\s*[\"']?)([^\"'&\\s,}]{3,})"),
            Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9._\\-]{10,})"),
            Pattern.compile("EAA[A-Za-z0-9]{20,}"),
            Pattern.compile("(?i)\\b([a-z0-9._-]{2,})@(ok[a-z]+|ybl|paytm|upi|ibl|axl|apl|icici|sbi|hdfcbank)\\b")
    };

    @Override
    public String convert(ILoggingEvent event) {
        return mask(super.convert(event));
    }

    public static String mask(String msg) {
        if (msg == null) {
            return null;
        }
        String out = PATTERNS[0].matcher(msg).replaceAll("$1$2****");
        out = PATTERNS[1].matcher(out).replaceAll("$1****");
        out = PATTERNS[2].matcher(out).replaceAll("EAA****");
        out = PATTERNS[3].matcher(out).replaceAll("****@$2");
        return out;
    }
}
