package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.constants.BookingStatus;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "travel_bookings")
public class TravelBooking extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Long packageId;

    @Column(nullable = false)
    private String phone;

    private String customerName;

    @Column(nullable = false)
    private int travellers = 1;

    private BigDecimal amount;
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    private LocalDate travelDate;
    private String notes;
}
