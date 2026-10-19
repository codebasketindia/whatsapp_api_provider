package in.codebasket.cbwa.waba;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WhatsappAccountRepository extends JpaRepository<WhatsappAccount, Long>, JpaSpecificationExecutor<WhatsappAccount> {
    Optional<WhatsappAccount> findByWabaId(String wabaId);

    List<WhatsappAccount> findByStatusNot(WabaStatus status);

    List<WhatsappAccount> findByPartnerId(Long partnerId);
}
