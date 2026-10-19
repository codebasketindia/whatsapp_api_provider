package in.codebasket.cbwa.campaign;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    Optional<Campaign> findByUid(String uid);

    Optional<Campaign> findByIdAndPartnerId(Long id, Long partnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> lockById(@Param("id") Long id);

    List<Campaign> findByStatusAndScheduledAtLessThanEqual(CampaignStatus status, Instant now);

    List<Campaign> findByStatus(CampaignStatus status);

    List<Campaign> findByPhoneNumberIdAndStatusIn(Long phoneNumberId, List<CampaignStatus> statuses);
}
