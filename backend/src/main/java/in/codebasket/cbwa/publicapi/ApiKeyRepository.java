package in.codebasket.cbwa.publicapi;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByApiKey(String apiKey);

    List<ApiKey> findByPartnerIdOrderByIdDesc(Long partnerId);

    Optional<ApiKey> findByIdAndPartnerId(Long id, Long partnerId);

    long countByPartnerId(Long partnerId);

    @Transactional
    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :now, k.lastUsedIp = :ip where k.id = :id")
    int touch(@Param("id") Long id, @Param("now") Instant now, @Param("ip") String ip);
}
