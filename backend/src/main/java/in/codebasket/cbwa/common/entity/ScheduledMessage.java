package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.constants.ScheduledStatus;

import in.codebasket.cbwa.common.entity.AuditableEntity;
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

    @Column(nullable = false, columnDefinition = "LONGTEXT")
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

    /** Set for follow-ups created by a registration link. */
    private Long registrationId;
}
