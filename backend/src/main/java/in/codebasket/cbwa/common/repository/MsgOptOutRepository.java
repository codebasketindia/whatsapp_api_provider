package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.OptOut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MsgOptOutRepository extends JpaRepository<OptOut, Long> {

    boolean existsByPartnerIdAndPhone(Long partnerId, String phone);

    Optional<OptOut> findByPartnerIdAndPhone(Long partnerId, String phone);
}
