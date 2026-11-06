package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.BusinessProfile;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {
    Optional<BusinessProfile> findByPhoneNumberId(Long phoneNumberId);
}
