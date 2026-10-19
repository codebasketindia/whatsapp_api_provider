package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "phone_numbers")
public class PhoneNumber extends AuditableEntity {

    @Column(nullable = false)
    private Long whatsappAccountId;

    @Column(nullable = false)
    private String phoneNumberId;

    private String displayPhoneNumber;

    private String waNumber;

    private String verifiedName;

    private String nameStatus;

    private String newNameStatus;

    @Column(nullable = false)
    private String qualityRating = "UNKNOWN";

    private String messagingLimitTier;

    private String throughputLevel;

    @Column(nullable = false)
    private int maxMps = 60;

    private String codeVerificationStatus;

    private String platformType;

    private String metaStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    private String healthStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SenderType senderType;

    @Column(name = "is_coexistence", nullable = false)
    private boolean coexistence;

    @Column(name = "is_registered", nullable = false)
    private boolean registered;

    private String pinEnc;

    private Integer dailySendCap;

    @Column(name = "is_paused", nullable = false)
    private boolean paused;

    private String pauseReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SenderStatus status;

    private Instant connectedAt;

    private Instant lastSyncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String raw;
}
