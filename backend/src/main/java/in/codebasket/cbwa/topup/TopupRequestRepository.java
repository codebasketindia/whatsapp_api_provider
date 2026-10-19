package in.codebasket.cbwa.topup;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopupRequestRepository extends JpaRepository<TopupRequest, Long>, JpaSpecificationExecutor<TopupRequest> {
    boolean existsByUtr(String utr);

    long countByStatus(TopupStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TopupRequest t where t.id = :id")
    Optional<TopupRequest> lockById(@Param("id") Long id);
}
