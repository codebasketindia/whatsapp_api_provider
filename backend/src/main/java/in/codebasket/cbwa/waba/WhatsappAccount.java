package in.codebasket.cbwa.waba;

import in.codebasket.cbwa.common.domain.BillingMode;
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
@Table(name = "whatsapp_accounts")
public class WhatsappAccount extends AuditableEntity {

    @Column(nullable = false)
    private String wabaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WabaOwnerType ownerType;

    private Long partnerId;

    private String businessId;

    private String name;

    private String currency;

    private String timezoneId;

    private String messageTemplateNamespace;

    private String accountReviewStatus;

    private String businessVerificationStatus;

    private Boolean paymentMethodAttached;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessTokenEnc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType tokenType;

    private Instant tokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenStatus tokenStatus;

    private Instant tokenCheckedAt;

    @Column(nullable = false)
    private boolean webhookSubscribed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnboardingType onboardingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingMode billingMode;

    @Column(nullable = false)
    private boolean creditLineShared;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WabaStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    private String restrictionInfo;

    private Instant connectedAt;

    private Instant disconnectedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String raw;
}
