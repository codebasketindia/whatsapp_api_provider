package in.codebasket.cbwa.payment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentGatewayLogRepository extends JpaRepository<PaymentGatewayLog, Long> {
    List<PaymentGatewayLog> findByPaymentOrderIdOrderByIdAsc(Long paymentOrderId);
}
