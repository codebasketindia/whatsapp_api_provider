package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.constants.DeliveryStatus;

import in.codebasket.cbwa.common.entity.BaseEntity;
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

    @Column(nullable = false, columnDefinition = "LONGTEXT")
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
