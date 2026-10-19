package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthPrincipal currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        return null;
    }

    public static AuthPrincipal current() {
        AuthPrincipal p = currentOrNull();
        if (p == null) {
            throw new AppException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return p;
    }

    public static Long currentUserIdOrNull() {
        AuthPrincipal p = currentOrNull();
        return p == null ? null : p.userId();
    }

    /** Partner id of the current partner user / API key. Fails for admin callers. */
    public static Long currentPartnerId() {
        AuthPrincipal p = current();
        if (p.partnerId() == null) {
            throw AppException.forbidden();
        }
        return p.partnerId();
    }
}
