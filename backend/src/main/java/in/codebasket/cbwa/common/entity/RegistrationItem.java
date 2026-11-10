package in.codebasket.cbwa.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A package bought in one booking: price snapshot, its pass and redemption. */
@Getter
@Setter
@Entity
@Table(name = "registration_items")
public class RegistrationItem extends BaseEntity {

    @Column(nullable = false)
    private Long registrationId;

    @Column(nullable = false)
    private Long packageId;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal mrp;

    @Column(nullable = false)
    private BigDecimal price;

    private String passNo;
    private Long passFileId;
    private Instant redeemedAt;
    private Long redeemedBy;
}
