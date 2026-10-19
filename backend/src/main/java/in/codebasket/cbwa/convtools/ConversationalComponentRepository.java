package in.codebasket.cbwa.convtools;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationalComponentRepository extends JpaRepository<ConversationalComponent, Long> {
    Optional<ConversationalComponent> findByPhoneNumberId(Long phoneNumberId);
}
