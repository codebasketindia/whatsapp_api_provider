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
}
