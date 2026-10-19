package in.codebasket.cbwa.pricing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RatePlanRepository extends JpaRepository<RatePlan, Long>, JpaSpecificationExecutor<RatePlan> {
    Optional<RatePlan> findFirstByDefaultPlanTrue();

    List<RatePlan> findByDefaultPlanTrue();
}
