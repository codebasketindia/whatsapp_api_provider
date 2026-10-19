package in.codebasket.cbwa.meta;

import java.util.Map;
import java.util.Set;

/** Maps Meta error codes to plain-language partner messages and decides what is retryable (F7). */
public final class MetaErrorMapper {

    private static final Map<Integer, String> FRIENDLY = Map.ofEntries(
            Map.entry(131026, "Recipient is not on WhatsApp or can't receive this message"),
            Map.entry(131047, "24-hour window closed - use an approved template"),
            Map.entry(132015, "Template is paused by Meta due to low quality"),
            Map.entry(132016, "Template is disabled by Meta"),
            Map.entry(132000, "Template variables don't match the template"),
            Map.entry(132001, "Template doesn't exist in this language"),
            Map.entry(132005, "Template text is too long after adding variables"),
            Map.entry(132007, "Template content violates WhatsApp policy"),
            Map.entry(132012, "Template variable format is not correct"),
            Map.entry(131050, "Recipient has stopped marketing messages from you"),
            Map.entry(131049, "Meta limited marketing messages to this user to keep a healthy ecosystem"),
            Map.entry(131056, "Too many messages to the same user too quickly"),
            Map.entry(130429, "Sending speed limit reached - message will be retried"),
            Map.entry(80007, "WhatsApp account rate limit reached - message will be retried"),
            Map.entry(131042, "Business payment issue - add a payment method in WhatsApp Manager"),
            Map.entry(131031, "WhatsApp business account is locked"),
            Map.entry(133010, "Phone number is not registered with WhatsApp"),
            Map.entry(190, "Access token expired or invalid - please reconnect the number"),
            Map.entry(368, "Account temporarily restricted for policy violations"),
            Map.entry(131048, "Spam rate limit hit - too many messages were blocked or reported"),
            Map.entry(131051, "This message type is not supported"),
            Map.entry(131052, "Media could not be downloaded from the user"),
            Map.entry(131053, "Media upload failed - check the file type and size"),
            Map.entry(131021, "Sender and recipient numbers are the same"),
            Map.entry(131009, "One of the message values is not valid"),
            Map.entry(131008, "A required message value is missing"),
            Map.entry(131005, "Permission denied for this action"),
            Map.entry(131000, "Temporary WhatsApp error - message will be retried"),
            Map.entry(100, "Invalid request sent to WhatsApp"),
            Map.entry(10, "Permission denied - check the number and WhatsApp account permissions"),
            Map.entry(200, "Permission denied - check the number and WhatsApp account permissions"),
            Map.entry(470, "24-hour window closed - use an approved template"),
            Map.entry(1, "Temporary WhatsApp error - message will be retried"),
            Map.entry(2, "Temporary WhatsApp error - message will be retried"),
            Map.entry(4, "Too many API calls - message will be retried"));

    private static final Set<Integer> RETRYABLE_CODES = Set.of(1, 2, 4, 80007, 130429, 131000, 131056, 133004);

    private MetaErrorMapper() {
    }

    public static String friendly(Integer code, String metaMessage, String details) {
        if (code != null && FRIENDLY.containsKey(code)) {
            return FRIENDLY.get(code);
        }
        if (details != null && !details.isBlank()) {
            return truncate(details);
        }
        return metaMessage == null ? "WhatsApp returned an error" : truncate(metaMessage);
    }

    /** Retry only on throttling, 429 and 5xx/network errors; never on permanent errors. */
    public static boolean isRetryable(int httpStatus, Integer code) {
        if (httpStatus == 0 || httpStatus == 429 || httpStatus >= 500) {
            return true;
        }
        return code != null && RETRYABLE_CODES.contains(code);
    }

    private static String truncate(String s) {
        return s.length() > 250 ? s.substring(0, 250) : s;
    }
}
