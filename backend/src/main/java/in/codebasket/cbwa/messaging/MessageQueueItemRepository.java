package in.codebasket.cbwa.messaging;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageQueueItemRepository extends JpaRepository<MessageQueueItem, Long> {
    Optional<MessageQueueItem> findByMessageId(Long messageId);
}
