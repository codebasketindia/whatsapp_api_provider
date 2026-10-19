package in.codebasket.cbwa.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserOtpRepository extends JpaRepository<UserOtp, Long> {
    Optional<UserOtp> findByChallengeId(String challengeId);

    @Modifying
    @Query("delete from UserOtp o where o.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
