package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.RegistrationItem;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationItemRepository extends JpaRepository<RegistrationItem, Long> {
    List<RegistrationItem> findByRegistrationIdOrderByIdAsc(Long registrationId);

    List<RegistrationItem> findByRegistrationIdIn(Collection<Long> registrationIds);

    boolean existsByPackageId(Long packageId);

    void deleteByRegistrationId(Long registrationId);

    /** Redeems once: 1 when this call redeemed it, 0 when it was already redeemed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RegistrationItem i set i.redeemedAt = :at, i.redeemedBy = :by where i.id = :id and i.redeemedAt is null")
    int redeem(@Param("id") Long id, @Param("at") Instant at, @Param("by") Long by);
}
