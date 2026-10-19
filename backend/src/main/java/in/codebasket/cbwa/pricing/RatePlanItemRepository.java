package in.codebasket.cbwa.pricing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatePlanItemRepository extends JpaRepository<RatePlanItem, Long> {
    List<RatePlanItem> findByRatePlanIdOrderByCountryCodeAscCategoryAsc(Long ratePlanId);

    Optional<RatePlanItem> findByRatePlanIdAndCountryCodeAndCategory(Long ratePlanId, String countryCode,
                                                                     in.codebasket.cbwa.common.domain.MessageCategory category);

    @Modifying
    @Query("delete from RatePlanItem i where i.ratePlanId = :planId")
    void deleteByRatePlanId(@Param("planId") Long planId);
}
