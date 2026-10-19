package in.codebasket.cbwa.messaging;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageStatusEventRepository extends JpaRepository<MessageStatusEvent, Long> {
    List<MessageStatusEvent> findByMessageIdOrderByIdAsc(Long messageId);

    boolean existsByWamidAndStatus(String wamid, String status);
}
