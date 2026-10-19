package in.codebasket.cbwa.user;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String mobile;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(nullable = false)
    private boolean twoFactorEnabled;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private Instant lockedUntil;

    @Column(nullable = false)
    private boolean mustChangePassword;

    private Instant passwordChangedAt;

    private Instant lastLoginAt;

    private String lastLoginIp;

    @Column(nullable = false)
    private String theme = "SYSTEM";

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    public boolean isSuperAdmin() {
        return role != null && "SUPER_ADMIN".equals(role.getCode());
    }

    public boolean isAdmin() {
        return userType == UserType.ADMIN;
    }
}
