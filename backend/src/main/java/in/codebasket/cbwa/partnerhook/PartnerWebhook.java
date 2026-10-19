package in.codebasket.cbwa.partnerhook;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "partner_webhooks")
public class PartnerWebhook extends AuditableEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String secretEnc;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> events;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int consecutiveFailures;

    private Instant lastSuccessAt;
}
