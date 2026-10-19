package in.codebasket.cbwa.meta;

import in.codebasket.cbwa.common.domain.BillingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/** Singleton row (id = 1) with the Meta app / platform configuration. */
@Getter
@Setter
@Entity
@Table(name = "meta_settings")
@EntityListeners(AuditingEntityListener.class)
public class MetaSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    private String appId;
    private String appSecretEnc;
    private String embeddedSignupConfigId;
    private String solutionId;
    private String businessPortfolioId;

    @Column(nullable = false)
    private String graphApiVersion;

    private String webhookVerifyTokenEnc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingMode billingMode;

    private String extendedCreditLineId;
    private Instant lastTestAt;
    private Boolean lastTestOk;

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
