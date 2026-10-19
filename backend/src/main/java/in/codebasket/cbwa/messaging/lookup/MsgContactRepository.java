package in.codebasket.cbwa.messaging.lookup;

import in.codebasket.cbwa.contact.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MsgContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByPartnerIdAndPhone(Long partnerId, String phone);

    List<Contact> findByIdIn(Collection<Long> ids);
}
