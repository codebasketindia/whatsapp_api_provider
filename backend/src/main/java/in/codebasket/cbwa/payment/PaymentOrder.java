package in.codebasket.cbwa.payment;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "payment_orders")
public class PaymentOrder extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GatewayType gateway;

    private String gatewayOrderId;

    private String gatewayPaymentId;

    private Long creditPackId;

    @Column(nullable = false)
    private BigDecimal baseAmount;

    @Column(nullable = false)
    private BigDecimal bonusAmount;

    @Column(nullable = false)
    private BigDecimal gstPercent;

    @Column(nullable = false)
    private BigDecimal gstAmount;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private BigDecimal creditAmount;

    @Column(nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentOrderStatus status;

    private String method;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    private VerifiedVia verifiedVia;

    private Instant paidAt;

    private Long walletTransactionId;

    private Long invoiceId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String rawPayload;
}
