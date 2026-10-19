package in.codebasket.cbwa.partnerhook;

import in.codebasket.cbwa.common.entity.BaseEntity;
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
@Table(name = "webhook_delivery_logs")
public class WebhookDeliveryLog extends BaseEntity {

    @Column(nullable = false)
    private Long partnerWebhookId;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    private Integer responseCode;

    private String responseBody;

    private Integer durationMs;

    private Instant nextRetryAt;
}
