package in.codebasket.cbwa.contact;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OptOutRepository extends JpaRepository<OptOut, Long>, JpaSpecificationExecutor<OptOut> {
    Optional<OptOut> findByPartnerIdAndPhone(Long partnerId, String phone);

    boolean existsByPartnerIdAndPhone(Long partnerId, String phone);

    List<OptOut> findByPartnerIdAndPhoneIn(Long partnerId, Collection<String> phones);
}
