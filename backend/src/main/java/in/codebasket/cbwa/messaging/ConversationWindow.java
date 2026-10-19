package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "conversation_windows")
public class ConversationWindow extends BaseEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private String waId;

    private String contactName;

    private Instant lastInboundAt;

    private Instant expiresAt;

    @Column(nullable = false)
    private int unreadCount;

    private String lastMessagePreview;

    @Column(nullable = false)
    private Instant lastMessageAt;
}
