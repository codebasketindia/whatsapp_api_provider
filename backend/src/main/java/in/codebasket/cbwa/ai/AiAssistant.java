package in.codebasket.cbwa.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One AI assistant per partner (PK = partner_id). */
@Getter
@Setter
@Entity
@Table(name = "ai_assistants")
@EntityListeners(AuditingEntityListener.class)
public class AiAssistant {

    @Id
    @Column(name = "partner_id")
    private Long partnerId;

    @Column(nullable = false)
    private boolean enabled;

    /** Null = every sender of the partner. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Long> senderIds;

    private String businessName;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiLanguage language = AiLanguage.AUTO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiTone tone = AiTone.FRIENDLY;

    private String fallbackMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> handoverKeywords = new ArrayList<>();

    @Column(nullable = false)
    private int maxRepliesPerChatPerDay = 20;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long updatedBy;

    @Version
    @Column(nullable = false)
    private Integer version;
}
