package in.codebasket.cbwa.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long>, JpaSpecificationExecutor<AiUsageLog> {
    boolean existsByInboundMessageId(Long inboundMessageId);

    @Query("select count(l) from AiUsageLog l where l.phoneNumberId = :senderId and l.waId = :waId and l.outcome = in.codebasket.cbwa.ai.AiOutcome.REPLIED and l.createdAt >= :since")
    long countRepliesSince(@Param("senderId") Long senderId, @Param("waId") String waId, @Param("since") Instant since);

    @Query("select l.outcome, count(l), coalesce(sum(l.charged), 0) from AiUsageLog l where l.partnerId = :partnerId and l.createdAt >= :from and l.createdAt < :to group by l.outcome")
    List<Object[]> summary(@Param("partnerId") Long partnerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select l.outcome, count(l), coalesce(sum(l.charged), 0) from AiUsageLog l where (:partnerId is null or l.partnerId = :partnerId) and l.createdAt >= :from and l.createdAt < :to group by l.outcome")
    List<Object[]> summaryAll(@Param("partnerId") Long partnerId, @Param("from") Instant from, @Param("to") Instant to);
}
