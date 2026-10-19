package in.codebasket.cbwa.messaging.lookup;

import in.codebasket.cbwa.contact.OptOut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MsgOptOutRepository extends JpaRepository<OptOut, Long> {

    boolean existsByPartnerIdAndPhone(Long partnerId, String phone);

    Optional<OptOut> findByPartnerIdAndPhone(Long partnerId, String phone);
}
