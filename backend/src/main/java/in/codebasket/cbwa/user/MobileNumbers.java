package in.codebasket.cbwa.user;

import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mobile number is the login id of every user. Numbers are stored as E.164 digits without "+"
 * (9876543210 -> 919876543210) so "+91 98765 43210", "09876543210" and "9876543210" are the same user.
 */
@Component
@RequiredArgsConstructor
public class MobileNumbers {

    private final UserRepository userRepository;

    /** Normalised digits or a VALIDATION_FAILED error on the given field. */
    public static String normalise(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw AppException.field(field, "Enter the mobile number");
        }
        return PhoneUtil.parse(raw.trim(), "IN").map(PhoneUtil.Parsed::digits)
                .orElseThrow(() -> AppException.field(field, "Enter a valid mobile number"));
    }

    /** Normalised digits, or null when the input is not a valid number (used for login lookups). */
    public static String normaliseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return PhoneUtil.parse(raw.trim(), "IN").map(PhoneUtil.Parsed::digits).orElse(null);
    }

    /** Normalises and checks that no other user has this mobile number. */
    public String requireUnique(String raw, Long currentUserId, String field) {
        String mobile = normalise(raw, field);
        userRepository.findByMobile(mobile).ifPresent(other -> {
            if (!other.getId().equals(currentUserId)) {
                throw AppException.field(field, "This mobile number is already registered");
            }
        });
        return mobile;
    }
}
