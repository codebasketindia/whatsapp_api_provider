package in.codebasket.cbwa.partnerhook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long>, JpaSpecificationExecutor<WebhookDeliveryLog> {

    List<WebhookDeliveryLog> findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByIdAsc(List<DeliveryStatus> statuses, Instant now);

    Optional<WebhookDeliveryLog> findByIdAndPartnerId(Long id, Long partnerId);

    /** Claims due deliveries; rows locked by another dispatcher are skipped. Call inside a transaction. */
    @Query(value = """
            select id from webhook_delivery_logs
             where status in ('PENDING','FAILED') and next_retry_at <= :now
             order by id limit :limit for update skip locked
            """, nativeQuery = true)
    List<Long> lockDueIds(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("update WebhookDeliveryLog l set l.nextRetryAt = :lease where l.id in :ids")
    int lease(@Param("ids") List<Long> ids, @Param("lease") Instant lease);

    @Modifying
    @Query("delete from WebhookDeliveryLog l where l.createdAt < :before and l.status in :statuses")
    int deleteOld(@Param("before") Instant before, @Param("statuses") List<DeliveryStatus> statuses);
}
