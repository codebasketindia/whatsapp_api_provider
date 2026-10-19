package in.codebasket.cbwa.messaging.lookup;

import in.codebasket.cbwa.template.TemplateComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MsgTemplateComponentRepository extends JpaRepository<TemplateComponent, Long> {

    List<TemplateComponent> findByTemplateIdOrderByPositionAsc(Long templateId);
}
