package in.codebasket.cbwa.pricing;

import in.codebasket.cbwa.partner.Partner;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Pricing module view on partners (count of partners per plan). */
public interface RatePlanQueryRepository extends Repository<Partner, Long> {

    @Query("select count(p) from Partner p where p.ratePlanId = :planId")
    long countPartners(@Param("planId") Long planId);
}
