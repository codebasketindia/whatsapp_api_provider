package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.ConversationalComponent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationalComponentRepository extends JpaRepository<ConversationalComponent, Long> {
    Optional<ConversationalComponent> findByPhoneNumberId(Long phoneNumberId);
}
