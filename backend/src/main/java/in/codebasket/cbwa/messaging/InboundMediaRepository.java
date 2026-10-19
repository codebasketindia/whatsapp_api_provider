package in.codebasket.cbwa.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InboundMediaRepository extends JpaRepository<InboundMedia, Long> {
    List<InboundMedia> findTop50ByDownloadStatusAndAttemptsLessThanOrderByIdAsc(String downloadStatus, int attempts);

    Optional<InboundMedia> findFirstByMessageId(Long messageId);

    List<InboundMedia> findByMessageIdIn(Collection<Long> messageIds);
}
