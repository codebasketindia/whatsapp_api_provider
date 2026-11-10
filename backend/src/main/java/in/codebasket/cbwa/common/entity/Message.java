package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.constants.ChargeStatus;
import in.codebasket.cbwa.common.constants.MessageDirection;
import in.codebasket.cbwa.common.constants.MessageSource;
import in.codebasket.cbwa.common.constants.MessageStatus;
import in.codebasket.cbwa.common.constants.MessageType;

import in.codebasket.cbwa.common.constants.MessageCategory;
import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "messages")
public class Message extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageDirection direction;

    @Column(nullable = false)
    private String waId;

    private String countryCode;

    private Long contactId;

    private Long campaignId;

    private Long campaignRecipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    private Long templateId;

    private String templateName;

    private String templateLanguage;

    @Enumerated(EnumType.STRING)
    private MessageCategory category;

    @Column(columnDefinition = "LONGTEXT")
    private String payload;

    private String preview;

    private String contextWamid;

    private String wamid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status;

    private Integer errorCode;

    private String errorTitle;

    private String errorMessage;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeStatus chargeStatus;

    private Boolean billable;

    private String pricingCategory;

    private String pricingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageSource source;

    private Long apiKeyId;

    private String clientReference;

    private Instant sentAt;

    private Instant deliveredAt;

    private Instant readAt;

    private Instant failedAt;
}
