package in.codebasket.cbwa.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaPricingAnalyticsRepository extends JpaRepository<MetaPricingAnalytics, Long> {
    Optional<MetaPricingAnalytics> findByWhatsappAccountIdAndDateAndPhoneNumberAndCountryAndPricingCategoryAndPricingType(
            Long whatsappAccountId, LocalDate date, String phoneNumber, String country, String pricingCategory, String pricingType);

    List<MetaPricingAnalytics> findByDateBetween(LocalDate from, LocalDate to);
}
