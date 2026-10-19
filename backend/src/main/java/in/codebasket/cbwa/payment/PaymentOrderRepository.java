package in.codebasket.cbwa.payment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long>, JpaSpecificationExecutor<PaymentOrder> {
    Optional<PaymentOrder> findByUid(String uid);

    Optional<PaymentOrder> findByGatewayOrderId(String gatewayOrderId);

    Optional<PaymentOrder> findByGatewayPaymentId(String gatewayPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.id = :id")
    Optional<PaymentOrder> lockById(@Param("id") Long id);

    List<PaymentOrder> findTop200ByStatusInAndCreatedAtBeforeOrderByIdAsc(List<PaymentOrderStatus> statuses, Instant before);

    long countByCreditPackId(Long creditPackId);
}
