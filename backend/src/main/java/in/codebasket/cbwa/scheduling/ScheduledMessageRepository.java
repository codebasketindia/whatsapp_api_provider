package in.codebasket.cbwa.scheduling;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledMessageRepository extends JpaRepository<ScheduledMessage, Long>, JpaSpecificationExecutor<ScheduledMessage> {
    Optional<ScheduledMessage> findByUidAndPartnerId(String uid, Long partnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduledMessage s where s.uid = :uid and s.partnerId = :partnerId")
    Optional<ScheduledMessage> lockByUidAndPartnerId(@Param("uid") String uid, @Param("partnerId") Long partnerId);

    @Query(value = """
            select id from scheduled_messages
             where status = 'SCHEDULED' and scheduled_at <= :now
             order by scheduled_at, id limit :limit for update skip locked
            """, nativeQuery = true)
    List<Long> lockDueIds(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ScheduledMessage s set s.status = in.codebasket.cbwa.scheduling.ScheduledStatus.SENDING, s.attemptedAt = :now"
            + " where s.id in :ids")
    int markSending(@Param("ids") List<Long> ids, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ScheduledMessage s set s.status = in.codebasket.cbwa.scheduling.ScheduledStatus.FAILED, s.error = :error"
            + " where s.status = in.codebasket.cbwa.scheduling.ScheduledStatus.SENDING and s.attemptedAt < :before")
    int failStuck(@Param("before") Instant before, @Param("error") String error);
}
