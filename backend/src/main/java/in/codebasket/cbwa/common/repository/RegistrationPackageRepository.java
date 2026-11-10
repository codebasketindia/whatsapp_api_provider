package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.RegistrationPackage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationPackageRepository extends JpaRepository<RegistrationPackage, Long> {
    List<RegistrationPackage> findByFormIdOrderBySortOrderAscIdAsc(Long formId);

    List<RegistrationPackage> findByFormIdAndActiveTrueOrderBySortOrderAscIdAsc(Long formId);

    boolean existsByImageFileId(Long imageFileId);
}
