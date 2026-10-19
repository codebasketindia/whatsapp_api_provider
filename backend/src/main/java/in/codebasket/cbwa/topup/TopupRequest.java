package in.codebasket.cbwa.topup;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "topup_requests")
public class TopupRequest extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String requestNo;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMode paymentMode;

    @Column(nullable = false)
    private String utr;

    @Column(nullable = false)
    private LocalDate paymentDate;

    private Long screenshotFileId;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopupStatus status;

    private String rejectReason;

    private Long walletTransactionId;

    private Long invoiceId;

    private Long reviewedBy;

    private Instant reviewedAt;
}
