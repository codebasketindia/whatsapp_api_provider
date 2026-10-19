package in.codebasket.cbwa.messaging.lookup;

import in.codebasket.cbwa.template.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Messaging-local view of templates (read + webhook status updates). */
public interface MsgTemplateRepository extends JpaRepository<Template, Long> {

    Optional<Template> findByWhatsappAccountIdAndNameAndLanguage(Long whatsappAccountId, String name, String language);

    List<Template> findByWhatsappAccountIdAndName(Long whatsappAccountId, String name);

    Optional<Template> findByMetaTemplateId(String metaTemplateId);

    List<Template> findByWhatsappAccountIdAndNameAndLanguageOrderByIdDesc(Long whatsappAccountId, String name, String language);
}
