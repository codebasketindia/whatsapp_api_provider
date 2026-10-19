package in.codebasket.cbwa.messaging;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long>, JpaSpecificationExecutor<Message> {
    Optional<Message> findByUid(String uid);

    Optional<Message> findByUidAndPartnerId(String uid, Long partnerId);

    Optional<Message> findByWamid(String wamid);

    boolean existsByWamid(String wamid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Message m where m.id = :id")
    Optional<Message> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Message m where m.wamid = :wamid")
    Optional<Message> lockByWamid(@Param("wamid") String wamid);

    long countByPhoneNumberIdAndDirectionAndCreatedAtGreaterThanEqual(Long phoneNumberId, MessageDirection direction, Instant from);

    @Query("select m.id from Message m where m.chargeStatus = :status and m.createdAt < :before order by m.id")
    List<Long> findIdsByChargeStatusBefore(@Param("status") ChargeStatus status, @Param("before") Instant before, Pageable pageable);

    @Query("""
            select m from Message m where m.partnerId = :partnerId and m.phoneNumberId = :senderId and m.waId = :waId
              and (:beforeId is null or m.id < :beforeId) order by m.id desc
            """)
    List<Message> conversation(@Param("partnerId") Long partnerId, @Param("senderId") Long senderId, @Param("waId") String waId,
                               @Param("beforeId") Long beforeId, Pageable pageable);

    Optional<Message> findFirstByPhoneNumberIdAndWaIdAndDirectionOrderByIdDesc(Long phoneNumberId, String waId, MessageDirection direction);
}
