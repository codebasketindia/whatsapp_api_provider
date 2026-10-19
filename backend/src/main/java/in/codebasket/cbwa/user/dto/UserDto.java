package in.codebasket.cbwa.user.dto;

import java.time.Instant;
import java.util.List;

public record UserDto(String uid, String name, String email, String mobile, String userType, RoleRef role,
                      List<String> permissions, boolean isSuperAdmin, boolean twoFactorEnabled,
                      boolean mustChangePassword, String theme, String status, Instant lastLoginAt, Instant createdAt,
                      PartnerRef partner, ImpersonatorRef impersonatedBy) {

    public record RoleRef(Long id, String code, String name) {
    }

    public record PartnerRef(Long id, String uid, String partnerCode, String businessName, String status) {
    }

    public record ImpersonatorRef(String uid, String name) {
    }
}
