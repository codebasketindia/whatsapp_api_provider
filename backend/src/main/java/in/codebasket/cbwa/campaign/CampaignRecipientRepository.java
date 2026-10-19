package in.codebasket.cbwa.campaign;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long>, JpaSpecificationExecutor<CampaignRecipient> {
    Page<CampaignRecipient> findByCampaignIdAndStatus(Long campaignId, RecipientStatus status, Pageable pageable);

    List<CampaignRecipient> findTop500ByCampaignIdAndStatusOrderByIdAsc(Long campaignId, RecipientStatus status);

    long countByCampaignIdAndStatus(Long campaignId, RecipientStatus status);

    @Query("select r.status, count(r) from CampaignRecipient r where r.campaignId = :campaignId group by r.status")
    List<Object[]> countByStatus(@Param("campaignId") Long campaignId);
}
