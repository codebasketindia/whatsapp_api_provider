package in.codebasket.cbwa.messaging;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ConversationWindowRepository extends JpaRepository<ConversationWindow, Long>, JpaSpecificationExecutor<ConversationWindow> {
    Optional<ConversationWindow> findByPhoneNumberIdAndWaId(Long phoneNumberId, String waId);
}
