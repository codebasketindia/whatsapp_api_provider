package in.codebasket.cbwa.messaging.lookup;

import in.codebasket.cbwa.convtools.BlockStatus;
import in.codebasket.cbwa.convtools.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MsgBlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    boolean existsByPhoneNumberIdAndWaIdAndStatus(Long phoneNumberId, String waId, BlockStatus status);
}
