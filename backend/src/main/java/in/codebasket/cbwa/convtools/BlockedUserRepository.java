package in.codebasket.cbwa.convtools;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    Optional<BlockedUser> findByPhoneNumberIdAndWaId(Long phoneNumberId, String waId);

    Page<BlockedUser> findByPhoneNumberIdAndStatus(Long phoneNumberId, BlockStatus status, Pageable pageable);

    boolean existsByPhoneNumberIdAndWaIdAndStatus(Long phoneNumberId, String waId, BlockStatus status);
}
