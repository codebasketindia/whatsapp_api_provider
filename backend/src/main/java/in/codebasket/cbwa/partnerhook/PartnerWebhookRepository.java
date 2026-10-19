package in.codebasket.cbwa.partnerhook;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerWebhookRepository extends JpaRepository<PartnerWebhook, Long> {
    Optional<PartnerWebhook> findByPartnerId(Long partnerId);
}
