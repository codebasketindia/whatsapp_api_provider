package in.codebasket.cbwa.partner;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PartnerRepository extends JpaRepository<Partner, Long>, JpaSpecificationExecutor<Partner> {
    Optional<Partner> findByUserId(Long userId);

    Optional<Partner> findByUid(String uid);

    long countByRatePlanId(Long ratePlanId);

    long countByStatus(PartnerStatus status);
}
