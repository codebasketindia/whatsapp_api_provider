package in.codebasket.cbwa.sender;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerPhoneNumberRepository extends JpaRepository<PartnerPhoneNumber, Long> {
    Optional<PartnerPhoneNumber> findByPhoneNumberIdAndActiveTrue(Long phoneNumberId);

    Optional<PartnerPhoneNumber> findByPartnerIdAndPhoneNumberIdAndActiveTrue(Long partnerId, Long phoneNumberId);

    List<PartnerPhoneNumber> findByPartnerIdAndActiveTrue(Long partnerId);

    List<PartnerPhoneNumber> findByActiveTrue();
}
