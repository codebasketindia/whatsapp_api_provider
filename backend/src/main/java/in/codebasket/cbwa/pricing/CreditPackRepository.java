package in.codebasket.cbwa.pricing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditPackRepository extends JpaRepository<CreditPack, Long> {
    List<CreditPack> findAllByOrderBySortOrderAscIdAsc();

    List<CreditPack> findByActiveTrueOrderBySortOrderAscIdAsc();
}
