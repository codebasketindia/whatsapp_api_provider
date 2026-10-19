package in.codebasket.cbwa.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookInboundLogRepository extends JpaRepository<WebhookInboundLog, Long>, JpaSpecificationExecutor<WebhookInboundLog> {
    Optional<WebhookInboundLog> findByEventId(String eventId);

    List<WebhookInboundLog> findTop100ByStatusInAndAttemptsLessThanOrderByIdAsc(List<InboundLogStatus> statuses, int attempts);

    @Modifying
    @Query("delete from WebhookInboundLog l where l.receivedAt < :before and l.status in ('PROCESSED','IGNORED')")
    int deleteOld(@Param("before") Instant before);
}
