package in.codebasket.cbwa.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** An add-on purchase on an existing booking: the packages, its own payment and invoice. */
@Getter
@Setter
@Entity
@Table(name = "booking_orders")
public class BookingOrder extends BaseEntity {

    public static final String PENDING = "PENDING", PAID = "PAID", FAILED = "FAILED", CANCELLED = "CANCELLED";

    @Column(nullable = false)
    private Long registrationId;

    @Column(nullable = false)
    private Long partnerId;

    /** 2, 3, ... (1 is the first booking itself). */
    @Column(nullable = false)
    private int seq;

    /** Comma-separated package ids bought in this order. */
    @Column(nullable = false)
    private String packageIds;

    @Column(nullable = false)
    private String status = PENDING;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal mrpTotal;

    private BigDecimal discountAmount;
    private String discountLabel;
    private String invoiceNo;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String paymentMethod;
    private Instant paidAt;
}
