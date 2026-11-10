package in.codebasket.cbwa.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A bookable package on a registration link (e.g. "Special Makkah & Madina Ziyarat"). One QR pass per paid package. */
@Getter
@Setter
@Entity
@Table(name = "registration_packages")
public class RegistrationPackage extends AuditableEntity {

    @Column(nullable = false)
    private Long formId;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String icon;
    private Long imageFileId;

    @Column(nullable = false)
    private BigDecimal mrp;

    @Column(nullable = false)
    private BigDecimal salePrice;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sortOrder;

    private String passTitle;
    private Long passDesignFileId;
}
