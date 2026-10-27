package in.codebasket.cbwa.ai;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiKnowledgeItemRepository extends JpaRepository<AiKnowledgeItem, Long> {
    List<AiKnowledgeItem> findByPartnerIdOrderBySortOrderAscIdAsc(Long partnerId);

    List<AiKnowledgeItem> findByPartnerIdAndActiveTrueOrderBySortOrderAscIdAsc(Long partnerId);

    Optional<AiKnowledgeItem> findByIdAndPartnerId(Long id, Long partnerId);

    long countByPartnerId(Long partnerId);
}
