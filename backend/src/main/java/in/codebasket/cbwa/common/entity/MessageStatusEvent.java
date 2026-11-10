package in.codebasket.cbwa.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "message_status_events")
public class MessageStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long messageId;

    @Column(nullable = false)
    private String wamid;

    @Column(nullable = false)
    private String status;

    private Integer errorCode;

    private String errorTitle;

    @Column(columnDefinition = "LONGTEXT")
    private String pricing;

    private Instant eventTimestamp;

    @Column(columnDefinition = "LONGTEXT")
    private String raw;

    @Column(nullable = false)
    private Instant createdAt;
}
