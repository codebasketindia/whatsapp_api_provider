package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.BookingOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingOrderRepository extends JpaRepository<BookingOrder, Long> {

    List<BookingOrder> findByRegistrationIdOrderBySeqAsc(Long registrationId);

    Optional<BookingOrder> findByRegistrationIdAndSeq(Long registrationId, int seq);

    Optional<BookingOrder> findByRazorpayOrderId(String razorpayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BookingOrder o where o.id = :id")
    Optional<BookingOrder> lockById(@Param("id") Long id);

    @Query("select coalesce(max(o.seq), 1) from BookingOrder o where o.registrationId = :rid")
    int maxSeq(@Param("rid") Long registrationId);

    /** Unpaid add-on orders, for the reconciliation job. */
    List<BookingOrder> findTop50ByStatusAndRazorpayOrderIdIsNotNullAndUpdatedAtBetweenOrderByIdAsc(String status, Instant from, Instant to);
}
