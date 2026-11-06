package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.TemplateAnalytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateAnalyticsRepository extends JpaRepository<TemplateAnalytics, Long> {
    List<TemplateAnalytics> findByTemplateIdAndDateBetweenOrderByDateAsc(Long templateId, LocalDate from, LocalDate to);

    Optional<TemplateAnalytics> findByTemplateIdAndDate(Long templateId, LocalDate date);
}
