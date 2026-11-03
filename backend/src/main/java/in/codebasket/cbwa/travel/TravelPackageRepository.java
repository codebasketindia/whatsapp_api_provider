package in.codebasket.cbwa.travel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelPackageRepository extends JpaRepository<TravelPackage, Long> {
    List<TravelPackage> findByPartnerIdOrderBySortOrderAscIdAsc(Long partnerId);

    List<TravelPackage> findByPartnerIdAndStatusOrderBySortOrderAscIdAsc(Long partnerId, TravelPackageStatus status);

    Optional<TravelPackage> findByIdAndPartnerId(Long id, Long partnerId);

    boolean existsByPartnerIdAndCodeAndIdNot(Long partnerId, String code, Long id);

    boolean existsByPartnerIdAndCode(Long partnerId, String code);

    long countByPartnerId(Long partnerId);
}
