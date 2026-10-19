package in.codebasket.cbwa.pricing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatePlanRepository extends JpaRepository<RatePlan, Long> {
    Optional<RatePlan> findFirstByDefaultPlanTrue();

    List<RatePlan> findByDefaultPlanTrue();
}
