package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.campaign.Campaign;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Sender module view on campaigns: auto-pause campaigns when a sender becomes unhealthy (F8). */
public interface SenderCampaignRepository extends Repository<Campaign, Long> {

    @Modifying
    @Query("update Campaign c set c.status = in.codebasket.cbwa.campaign.CampaignStatus.PAUSED, c.pauseReason = :reason "
            + "where c.phoneNumberId = :senderId and c.status in (in.codebasket.cbwa.campaign.CampaignStatus.RUNNING, "
            + "in.codebasket.cbwa.campaign.CampaignStatus.SCHEDULED)")
    int pauseActiveForSender(@Param("senderId") Long senderId, @Param("reason") String reason);
}
