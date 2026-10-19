package in.codebasket.cbwa.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageStatusEventRepository extends JpaRepository<MessageStatusEvent, Long> {
    List<MessageStatusEvent> findByMessageIdOrderByIdAsc(Long messageId);

    boolean existsByWamidAndStatus(String wamid, String status);

    /** Status webhooks that arrived before the send response stored the wamid. */
    List<MessageStatusEvent> findByWamidAndMessageIdIsNullOrderByIdAsc(String wamid);
}
