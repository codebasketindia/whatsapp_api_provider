package in.codebasket.cbwa.template;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TemplateRepository extends JpaRepository<Template, Long>, JpaSpecificationExecutor<Template> {
    Optional<Template> findByUid(String uid);

    Optional<Template> findByMetaTemplateId(String metaTemplateId);

    Optional<Template> findByWhatsappAccountIdAndNameAndLanguage(Long whatsappAccountId, String name, String language);

    List<Template> findByWhatsappAccountId(Long whatsappAccountId);

    List<Template> findByWhatsappAccountIdAndName(Long whatsappAccountId, String name);
}
