package in.codebasket.cbwa.sender;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PhoneNumberRepository extends JpaRepository<PhoneNumber, Long>, JpaSpecificationExecutor<PhoneNumber> {
    Optional<PhoneNumber> findByPhoneNumberId(String phoneNumberId);

    List<PhoneNumber> findByWhatsappAccountId(Long whatsappAccountId);

    List<PhoneNumber> findByStatus(SenderStatus status);
}
