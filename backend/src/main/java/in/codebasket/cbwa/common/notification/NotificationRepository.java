package in.codebasket.cbwa.common.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Partner users see their partner's notifications; admins see ADMIN audience + their own. */
    @Query("""
            select n from Notification n where
              ((:partnerId is not null and n.partnerId = :partnerId and n.audience = 'PARTNER')
               or (:partnerId is null and n.audience = 'ADMIN' and (n.userId is null or n.userId = :userId)))
              and (:unreadOnly = false or n.read = false)
            order by n.createdAt desc
            """)
    Page<Notification> findForUser(@Param("userId") Long userId, @Param("partnerId") Long partnerId,
                                   @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    @Query("""
            select count(n) from Notification n where n.read = false and
              ((:partnerId is not null and n.partnerId = :partnerId and n.audience = 'PARTNER')
               or (:partnerId is null and n.audience = 'ADMIN' and (n.userId is null or n.userId = :userId)))
            """)
    long countUnread(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    @Modifying
    @Query("""
            update Notification n set n.read = true, n.readAt = :now where n.read = false and
              ((:partnerId is not null and n.partnerId = :partnerId and n.audience = 'PARTNER')
               or (:partnerId is null and n.audience = 'ADMIN' and (n.userId is null or n.userId = :userId)))
            """)
    int markAllRead(@Param("userId") Long userId, @Param("partnerId") Long partnerId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before and n.read = true")
    int deleteReadBefore(@Param("before") Instant before);
}
