package in.codebasket.cbwa.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_usage_logs")
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private String waId;

    @Column(nullable = false)
    private Long inboundMessageId;

    private Long replyMessageId;

    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiOutcome outcome;

    private String reason;

    private String inboundPreview;

    private String replyPreview;

    @Column(nullable = false)
    private int inputTokens;

    @Column(nullable = false)
    private int outputTokens;

    @Column(nullable = false)
    private int cacheReadTokens;

    @Column(nullable = false)
    private int cacheWriteTokens;

    @Column(nullable = false)
    private BigDecimal charged;

    private Long walletTransactionId;

    @Column(nullable = false)
    private Instant createdAt;
}
