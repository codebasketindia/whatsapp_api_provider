package in.codebasket.cbwa.invoice;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "invoices")
public class Invoice extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String invoiceNo;

    @Column(nullable = false)
    private Long partnerId;

    private Long paymentOrderId;

    private Long topupRequestId;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false)
    private String billingName;

    private String gstin;

    @Column(columnDefinition = "TEXT")
    private String billingAddress;

    private String placeOfSupply;

    @Column(nullable = false)
    private BigDecimal taxableAmount;

    @Column(nullable = false)
    private BigDecimal cgstAmount;

    @Column(nullable = false)
    private BigDecimal sgstAmount;

    @Column(nullable = false)
    private BigDecimal igstAmount;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    private Long fileId;
}
