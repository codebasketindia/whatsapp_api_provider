package in.codebasket.cbwa.pricing;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rate_plans")
public class RatePlan extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPlan;

    @Column(name = "is_custom", nullable = false)
    private boolean custom;

    private Long partnerId;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false)
    private BigDecimal sessionMessageFee;

    @Column(nullable = false)
    private BigDecimal platformFeePerMessage;

    @Column(nullable = false)
    private BigDecimal platformFeeMonthlyPerNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RatePlanStatus status;
}
