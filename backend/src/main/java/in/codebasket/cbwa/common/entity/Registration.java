package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.constants.PassStatus;
import in.codebasket.cbwa.common.constants.PaymentStatus;

import in.codebasket.cbwa.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "registrations")
public class Registration extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long formId;

    @Column(nullable = false)
    private Long partnerId;

    /** Given when the registration is complete (for paid links: after payment). */
    private String passNo;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    private Long photoFileId;
    private Long passFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PassStatus passStatus;

    private Long passMessageId;
    private String passError;

    @Column(nullable = false)
    private String claimCode;

    private Long contactId;

    @Column(nullable = false)
    private Instant consentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_REQUIRED;

    private java.math.BigDecimal amount;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String paymentMethod;
    private Instant paidAt;
    private String invoiceNo;
    private java.math.BigDecimal mrpTotal;

    private String ip;
    private String userAgent;
}
