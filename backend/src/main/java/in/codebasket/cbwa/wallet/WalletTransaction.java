package in.codebasket.cbwa.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String uid;

    @Column(nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private Long partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletTxnType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal balanceBefore;

    @Column(nullable = false)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private BigDecimal heldBefore;

    @Column(nullable = false)
    private BigDecimal heldAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletRefType referenceType;

    private String referenceId;

    private String remarks;

    private Long createdBy;

    private Long impersonatorUserId;

    @Column(nullable = false)
    private Instant createdAt;
}
