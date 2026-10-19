package in.codebasket.cbwa.partner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One row per partner (PK = partner_id). */
@Getter
@Setter
@Entity
@Table(name = "partner_settings")
@EntityListeners(AuditingEntityListener.class)
public class PartnerSettings {

    @Id
    @Column(name = "partner_id")
    private Long partnerId;

    private BigDecimal lowBalanceThreshold;
    private Instant lowBalanceAlertedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> optOutKeywords = new ArrayList<>(List.of("STOP", "UNSUBSCRIBE"));

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> optInKeywords = new ArrayList<>(List.of("START"));

    private String optOutAutoReply;
    private Long defaultPhoneNumberId;

    @Column(nullable = false)
    private boolean notifyEmail = true;

    @Column(nullable = false)
    private String timezone = "Asia/Kolkata";

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long updatedBy;

    @Version
    @Column(nullable = false)
    private Integer version;
}
