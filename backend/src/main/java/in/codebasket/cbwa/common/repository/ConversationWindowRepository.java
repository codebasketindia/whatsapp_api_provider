package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.ConversationWindow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ConversationWindowRepository extends JpaRepository<ConversationWindow, Long>, JpaSpecificationExecutor<ConversationWindow> {
    Optional<ConversationWindow> findByPhoneNumberIdAndWaId(Long phoneNumberId, String waId);
}
