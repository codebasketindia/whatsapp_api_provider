package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "campaigns")
public class Campaign extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudienceType audienceType;

    @JdbcTypeCode(SqlTypes.JSON)
    private String audience;

    private Long sourceFileId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String columnMapping;

    @JdbcTypeCode(SqlTypes.JSON)
    private String headerMedia;

    @Column(nullable = false)
    private String defaultCountry = "IN";

    @Column(nullable = false)
    private boolean saveContacts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status;

    private String pauseReason;

    private Instant scheduledAt;

    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int validCount;

    @Column(nullable = false)
    private int invalidCount;

    @Column(nullable = false)
    private int skippedCount;

    @Column(nullable = false)
    private int queuedCount;

    @Column(nullable = false)
    private int sentCount;

    @Column(nullable = false)
    private int deliveredCount;

    @Column(nullable = false)
    private int readCount;

    @Column(nullable = false)
    private int failedCount;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal estimatedCost;

    @Column(nullable = false)
    private BigDecimal chargedAmount;

    @Column(nullable = false)
    private BigDecimal refundedAmount;

    private Instant preparedAt;
}
