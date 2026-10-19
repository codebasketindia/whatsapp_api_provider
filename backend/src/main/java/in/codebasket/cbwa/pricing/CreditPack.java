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
@Table(name = "credit_packs")
public class CreditPack extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BonusType bonusType;

    @Column(nullable = false)
    private BigDecimal bonusValue;

    @Column(name = "is_popular", nullable = false)
    private boolean popular;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int sortOrder;
}
