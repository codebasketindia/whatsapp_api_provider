package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.constants.PartnerStatus;
import in.codebasket.cbwa.common.entity.Partner;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PartnerRepository extends JpaRepository<Partner, Long>, JpaSpecificationExecutor<Partner> {
    Optional<Partner> findByUserId(Long userId);

    Optional<Partner> findByUid(String uid);

    long countByRatePlanId(Long ratePlanId);

    long countByStatus(PartnerStatus status);
}
