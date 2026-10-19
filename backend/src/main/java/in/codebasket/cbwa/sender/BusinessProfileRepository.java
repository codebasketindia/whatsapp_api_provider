package in.codebasket.cbwa.sender;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {
    Optional<BusinessProfile> findByPhoneNumberId(Long phoneNumberId);
}
