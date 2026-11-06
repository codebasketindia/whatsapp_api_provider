package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.TemplateComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MsgTemplateComponentRepository extends JpaRepository<TemplateComponent, Long> {

    List<TemplateComponent> findByTemplateIdOrderByPositionAsc(Long templateId);
}
