package in.codebasket.cbwa.partner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface PartnerSettingsRepository extends JpaRepository<PartnerSettings, Long> {

    /** Sets the alert time only if no alert was sent in the last 24 h; returns 1 when this caller should alert. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update PartnerSettings s set s.lowBalanceAlertedAt = :now
            where s.partnerId = :partnerId and (s.lowBalanceAlertedAt is null or s.lowBalanceAlertedAt < :cutoff)
            """)
    int markLowBalanceAlerted(@Param("partnerId") Long partnerId, @Param("now") Instant now, @Param("cutoff") Instant cutoff);

    @Modifying(flushAutomatically = true)
    @Query("update PartnerSettings s set s.lowBalanceAlertedAt = null where s.partnerId = :partnerId and s.lowBalanceAlertedAt is not null")
    int clearLowBalanceAlert(@Param("partnerId") Long partnerId);
}
