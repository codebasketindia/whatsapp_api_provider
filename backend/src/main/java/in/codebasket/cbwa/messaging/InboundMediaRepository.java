package in.codebasket.cbwa.messaging;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundMediaRepository extends JpaRepository<InboundMedia, Long> {
    List<InboundMedia> findTop50ByDownloadStatusAndAttemptsLessThanOrderByIdAsc(String downloadStatus, int attempts);

    Optional<InboundMedia> findFirstByMessageId(Long messageId);
}
