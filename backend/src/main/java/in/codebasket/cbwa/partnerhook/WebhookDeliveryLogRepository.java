package in.codebasket.cbwa.partnerhook;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long>, JpaSpecificationExecutor<WebhookDeliveryLog> {
    List<WebhookDeliveryLog> findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByIdAsc(List<DeliveryStatus> statuses, Instant now);
}
