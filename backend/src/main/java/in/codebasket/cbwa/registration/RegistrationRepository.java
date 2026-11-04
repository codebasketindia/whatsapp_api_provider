package in.codebasket.cbwa.registration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RegistrationRepository extends JpaRepository<Registration, Long>, JpaSpecificationExecutor<Registration> {
    Optional<Registration> findByUid(String uid);

    Optional<Registration> findByUidAndPartnerId(String uid, Long partnerId);

    Optional<Registration> findByFormIdAndPhone(Long formId, String phone);

    Optional<Registration> findByClaimCode(String claimCode);

    List<Registration> findTop5ByPartnerIdAndPhoneOrderByIdDesc(Long partnerId, String phone);

    List<Registration> findByFormIdOrderByIdAsc(Long formId);

    boolean existsByClaimCode(String claimCode);

    Optional<Registration> findByRazorpayOrderId(String razorpayOrderId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Registration r where r.id = :id")
    Optional<Registration> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    /** Unpaid registrations with an order, for the reconciliation job. */
    List<Registration> findTop50ByPaymentStatusAndRazorpayOrderIdIsNotNullAndUpdatedAtBetweenOrderByIdAsc(
            PaymentStatus status, java.time.Instant from, java.time.Instant to);
}
