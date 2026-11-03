package in.codebasket.cbwa.travel;

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
@Table(name = "travel_packages")
public class TravelPackage extends AuditableEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String destination;
    private String duration;
    private BigDecimal price;
    private String priceNote;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer seatsTotal;

    @Column(columnDefinition = "TEXT")
    private String inclusions;

    @Column(columnDefinition = "TEXT")
    private String exclusions;

    @Column(columnDefinition = "TEXT")
    private String itinerary;

    @Column(columnDefinition = "TEXT")
    private String details;

    private Long fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravelPackageStatus status = TravelPackageStatus.ACTIVE;

    @Column(nullable = false)
    private int sortOrder;
}
