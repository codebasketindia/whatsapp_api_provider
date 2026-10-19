package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.messaging.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Cross-module lookup: has an API key been used to send messages (FK messages.api_key_id)? */
public interface ApiKeyUsageRepository extends JpaRepository<Message, Long> {

    @Query("select count(m) > 0 from Message m where m.apiKeyId = :apiKeyId")
    boolean isUsed(@Param("apiKeyId") Long apiKeyId);
}
