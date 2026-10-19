package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "platform_fee_charges")
public class PlatformFeeCharge extends BaseEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private String period;

    @Column(nullable = false)
    private BigDecimal amount;

    private Long walletTransactionId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;
}
