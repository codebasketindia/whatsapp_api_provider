package in.codebasket.cbwa.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "webhook_inbound_logs")
public class WebhookInboundLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private boolean signatureValid;

    private String objectType;

    private String wabaId;

    private String fields;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundLogStatus status;

    @Column(nullable = false)
    private int attempts;

    private String error;

    @Column(nullable = false)
    private Instant receivedAt;

    private Instant processedAt;
}
