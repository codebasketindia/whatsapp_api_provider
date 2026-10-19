package in.codebasket.cbwa.contact;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContactRepository extends JpaRepository<Contact, Long>, JpaSpecificationExecutor<Contact> {
    Optional<Contact> findByPartnerIdAndPhone(Long partnerId, String phone);

    Optional<Contact> findByIdAndPartnerId(Long id, Long partnerId);

    List<Contact> findByPartnerIdAndPhoneIn(Long partnerId, Collection<String> phones);

    List<Contact> findByPartnerIdAndIdIn(Long partnerId, Collection<Long> ids);
}
