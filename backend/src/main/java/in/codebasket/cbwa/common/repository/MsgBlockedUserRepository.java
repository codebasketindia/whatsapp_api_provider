package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.constants.BlockStatus;
import in.codebasket.cbwa.common.entity.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MsgBlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    boolean existsByPhoneNumberIdAndWaIdAndStatus(Long phoneNumberId, String waId, BlockStatus status);
}
