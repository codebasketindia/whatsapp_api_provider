package in.codebasket.cbwa.template;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateAnalyticsRepository extends JpaRepository<TemplateAnalytics, Long> {
    List<TemplateAnalytics> findByTemplateIdAndDateBetweenOrderByDateAsc(Long templateId, LocalDate from, LocalDate to);

    Optional<TemplateAnalytics> findByTemplateIdAndDate(Long templateId, LocalDate date);
}
