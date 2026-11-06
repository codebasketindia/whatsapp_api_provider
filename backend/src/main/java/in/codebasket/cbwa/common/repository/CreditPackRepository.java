package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.CreditPack;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditPackRepository extends JpaRepository<CreditPack, Long> {
    List<CreditPack> findAllByOrderBySortOrderAscIdAsc();

    List<CreditPack> findByActiveTrueOrderBySortOrderAscIdAsc();
}
