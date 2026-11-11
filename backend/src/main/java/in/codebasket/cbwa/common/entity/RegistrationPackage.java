package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.dto.ItineraryStop;
import in.codebasket.cbwa.config.ItineraryListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    /** Picture by URL (built-in /ziyarat/*.jpg or any https image) when no file was uploaded. */
    private String imageUrl;

    /** false for a fee-only line such as the registration fee. */
    @Column(nullable = false)
    private boolean issuePass = true;

    /** TICKET (generated pass) or FORM_DESIGN (the link's pass design with the QR in the photo box). */
    @Column(nullable = false)
    private String passStyle = "TICKET";

    /** Rounds / days with places, shown under "Full Details". */
    @Convert(converter = ItineraryListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<ItineraryStop> itinerary = new ArrayList<>();
}
