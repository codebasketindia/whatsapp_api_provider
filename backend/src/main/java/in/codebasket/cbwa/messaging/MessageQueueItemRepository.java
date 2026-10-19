package in.codebasket.cbwa.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MessageQueueItemRepository extends JpaRepository<MessageQueueItem, Long> {
    Optional<MessageQueueItem> findByMessageId(Long messageId);

    /** Rows stuck in PROCESSING (worker crashed / restarted) go back to PENDING. */
    @Modifying
    @Query("""
            update MessageQueueItem q set q.status = in.codebasket.cbwa.messaging.QueueStatus.PENDING, q.lockedBy = null,
              q.lockedAt = null, q.updatedAt = :now
            where q.status = in.codebasket.cbwa.messaging.QueueStatus.PROCESSING and q.lockedAt < :staleBefore
            """)
    int resetStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
