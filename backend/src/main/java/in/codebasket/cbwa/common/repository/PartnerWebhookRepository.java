package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.PartnerWebhook;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerWebhookRepository extends JpaRepository<PartnerWebhook, Long> {
    Optional<PartnerWebhook> findByPartnerId(Long partnerId);
}
