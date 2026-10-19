package in.codebasket.cbwa.convtools;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "blocked_users")
public class BlockedUser extends AuditableEntity {

    private Long partnerId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private String waId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockStatus status;

    private String metaError;

    private Instant blockedAt;

    private Instant unblockedAt;
}
