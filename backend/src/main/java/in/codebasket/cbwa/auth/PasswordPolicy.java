package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** Min 8 chars with upper case, lower case and a digit; max 72 (BCrypt limit). */
    public static void check(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
                || !password.chars().anyMatch(Character::isUpperCase)
                || !password.chars().anyMatch(Character::isLowerCase)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw new AppException(ErrorCode.AUTH_PASSWORD_POLICY);
        }
    }

    /** Readable temporary password that satisfies the policy. */
    public static String temporary() {
        String base = in.codebasket.cbwa.common.crypto.TokenGenerator.alphanumeric(9);
        return "Cb" + base + in.codebasket.cbwa.common.crypto.TokenGenerator.numericOtp(2) + "a";
    }
}
