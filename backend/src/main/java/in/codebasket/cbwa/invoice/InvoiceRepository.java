package in.codebasket.cbwa.invoice;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {
    Optional<Invoice> findByPaymentOrderId(Long paymentOrderId);

    Optional<Invoice> findByTopupRequestId(Long topupRequestId);
}
