package in.codebasket.cbwa.partner;

import in.codebasket.cbwa.campaign.Campaign;
import in.codebasket.cbwa.campaign.CampaignStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

/** Lets partner management pause a partner's running/scheduled campaigns when it is suspended. */
public interface PartnerCampaignControlRepository extends Repository<Campaign, Long> {

    @Modifying
    @Query("""
            update Campaign c set c.status = :paused, c.pauseReason = :reason, c.version = c.version + 1, c.updatedAt = :now
            where c.partnerId = :partnerId and c.status in :statuses
            """)
    int pauseAll(@Param("partnerId") Long partnerId, @Param("statuses") Collection<CampaignStatus> statuses,
                 @Param("paused") CampaignStatus paused, @Param("reason") String reason, @Param("now") Instant now);
}
