package in.codebasket.cbwa.messaging;

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
@Table(name = "message_queue")
public class MessageQueueItem extends BaseEntity {

    @Column(nullable = false)
    private Long messageId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private int maxAttempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private String lockedBy;

    private Instant lockedAt;

    private String lastError;
}
