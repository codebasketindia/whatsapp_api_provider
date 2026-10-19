package in.codebasket.cbwa.template;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateComponentRepository extends JpaRepository<TemplateComponent, Long> {
    List<TemplateComponent> findByTemplateIdOrderByPositionAsc(Long templateId);

    @Modifying
    @Query("delete from TemplateComponent c where c.templateId = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
