package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.user.UserType;

import java.util.Set;

/**
 * The authenticated caller. For dashboard users it comes from the JWT; for the public API it is
 * built from the API key (userId null, apiKeyId set, userType PARTNER).
 */
public record AuthPrincipal(Long userId, String uid, String email, String name, UserType userType, Long partnerId,
                            boolean superAdmin, Set<String> permissions, Long impersonatorUserId, Long apiKeyId,
                            boolean mustChangePassword) {

    public boolean isAdmin() {
        return userType == UserType.ADMIN;
    }

    public boolean isPartner() {
        return userType == UserType.PARTNER;
    }

    public boolean isApiKey() {
        return apiKeyId != null;
    }

    public boolean isImpersonating() {
        return impersonatorUserId != null;
    }

    public boolean hasPermission(String permission) {
        return superAdmin || (permissions != null && permissions.contains(permission));
    }
}
