package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.RegistrationForm;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationFormRepository extends JpaRepository<RegistrationForm, Long> {
    Optional<RegistrationForm> findBySlug(String slug);

    Optional<RegistrationForm> findByIdAndPartnerId(Long id, Long partnerId);

    List<RegistrationForm> findByPartnerIdOrderByIdDesc(Long partnerId);

    boolean existsBySlug(String slug);

    boolean existsByHeroImageUrlContaining(String part);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from RegistrationForm f where f.id = :id")
    Optional<RegistrationForm> lockById(@Param("id") Long id);
}
