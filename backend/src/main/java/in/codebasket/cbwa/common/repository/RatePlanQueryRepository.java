package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.Partner;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Pricing module view on partners (count of partners per plan). */
public interface RatePlanQueryRepository extends Repository<Partner, Long> {

    @Query("select count(p) from Partner p where p.ratePlanId = :planId")
    long countPartners(@Param("planId") Long planId);
}
