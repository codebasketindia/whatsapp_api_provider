package in.codebasket.cbwa.scheduling;

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
@Table(name = "scheduled_messages")
public class ScheduledMessage extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private String waId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String request;

    private String preview;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduledStatus status;

    private Long messageId;

    private String error;

    private Instant attemptedAt;
}
