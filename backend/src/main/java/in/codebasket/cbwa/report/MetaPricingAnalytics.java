package in.codebasket.cbwa.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "meta_pricing_analytics")
public class MetaPricingAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long whatsappAccountId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String pricingCategory;

    @Column(nullable = false)
    private String pricingType;

    @Column(nullable = false)
    private int volume;

    @Column(nullable = false)
    private BigDecimal cost;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant syncedAt;
}
