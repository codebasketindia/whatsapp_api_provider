package in.codebasket.cbwa.pricing;

import in.codebasket.cbwa.common.domain.MessageCategory;
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
@Table(name = "rate_plan_items")
public class RatePlanItem extends AuditableEntity {

    @Column(nullable = false)
    private Long ratePlanId;

    @Column(nullable = false)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageCategory category;

    @Column(nullable = false)
    private BigDecimal price;
}
