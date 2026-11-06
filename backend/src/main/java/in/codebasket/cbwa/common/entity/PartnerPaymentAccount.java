package in.codebasket.cbwa.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

/** The partner's own Razorpay account (registration fees go straight to the partner). Secrets AES-GCM encrypted. */
@Getter
@Setter
@Entity
@Table(name = "partner_payment_accounts")
@EntityListeners(AuditingEntityListener.class)
public class PartnerPaymentAccount {

    @Id
    @Column(name = "partner_id")
    private Long partnerId;

    @Column(nullable = false)
    private String provider = "RAZORPAY";

    private String keyId;
    private String keySecretEnc;
    private String webhookSecretEnc;

    @Column(nullable = false)
    private boolean enabled;

    private Instant verifiedAt;

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
