package in.codebasket.cbwa.messaging.lookup;

import in.codebasket.cbwa.campaign.Campaign;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Messaging/webhook-local access to campaigns: auto-pause on sender quality drop and names for DTOs. */
public interface MsgCampaignRepository extends Repository<Campaign, Long> {

    Optional<Campaign> findById(Long id);

    Optional<Campaign> findByUid(String uid);

    List<Campaign> findByIdIn(Collection<Long> ids);

    @Modifying
    @Query(value = "update campaigns set status = 'PAUSED', pause_reason = :reason, updated_at = UTC_TIMESTAMP(6) "
            + "where phone_number_id = :phoneNumberId and status in ('RUNNING', 'SCHEDULED')", nativeQuery = true)
    int pauseActiveForSender(@Param("phoneNumberId") Long phoneNumberId, @Param("reason") String reason);
}
