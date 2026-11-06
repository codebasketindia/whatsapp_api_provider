package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.PaymentGatewayLog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentGatewayLogRepository extends JpaRepository<PaymentGatewayLog, Long> {
    List<PaymentGatewayLog> findByPaymentOrderIdOrderByIdAsc(Long paymentOrderId);
}
