package in.codebasket.cbwa.report;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.waba.WabaStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Pulls Meta's pricing analytics (cost per day / number / country / category) for every active
 * WABA into meta_pricing_analytics, used by the Meta cost reconciliation report (RP5).
 * Runs daily at 03:00 IST for the last 3 days (Meta finalises numbers with a delay).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetaPricingAnalyticsJob {

    private static final int DAYS = 3;

    private final WhatsappAccountRepository wabaRepository;
    private final MetaPricingAnalyticsRepository repository;
    private final MetaGraphClient graph;
    private final SenderAccessService senderAccess;
    private final TransactionTemplate tx;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    public void scheduled() {
        syncAll(DAYS);
    }

    /** Returns the number of rows upserted. Errors on one WABA do not stop the others. */
    public int syncAll(int days) {
        LocalDate today = TimeUtil.todayIst();
        long start = TimeUtil.startOfDayIst(today.minusDays(days)).getEpochSecond();
        long end = TimeUtil.startOfDayIst(today).getEpochSecond();
        int total = 0;
        for (WhatsappAccount waba : wabaRepository.findByStatusNot(WabaStatus.DISCONNECTED)) {
            if (waba.getStatus() != WabaStatus.ACTIVE) {
                continue;
            }
            try {
                total += sync(waba, start, end);
            } catch (RuntimeException e) {
                log.warn("Pricing analytics sync failed for WABA {}: {}", waba.getWabaId(), e.getMessage());
            }
        }
        return total;
    }

    int sync(WhatsappAccount waba, long start, long end) {
        String fields = "pricing_analytics.start(" + start + ").end(" + end + ").granularity(DAILY)"
                + ".dimensions([\"PRICING_CATEGORY\",\"PRICING_TYPE\",\"COUNTRY\",\"PHONE\"])";
        JsonNode res = graph.get("/" + waba.getWabaId(), Map.of("fields", fields), senderAccess.accessToken(waba));
        String currency = waba.getCurrency() == null ? "INR" : waba.getCurrency();
        Integer count = tx.execute(s -> {
            int n = 0;
            for (JsonNode block : res.path("pricing_analytics").path("data")) {
                for (JsonNode p : block.path("data_points")) {
                    upsert(waba.getId(), p, currency);
                    n++;
                }
            }
            return n;
        });
        return count == null ? 0 : count;
    }

    private void upsert(Long wabaId, JsonNode p, String currency) {
        LocalDate date = TimeUtil.toIstDate(Instant.ofEpochSecond(p.path("start").asLong()));
        String phone = ReportService.digits(p.path("phone_number").asText(""));
        String country = p.path("country").asText("ZZ");
        String category = p.path("pricing_category").asText("UNKNOWN").toUpperCase();
        String type = p.path("pricing_type").asText("REGULAR").toUpperCase();
        MetaPricingAnalytics row = repository
                .findByWhatsappAccountIdAndDateAndPhoneNumberAndCountryAndPricingCategoryAndPricingType(wabaId, date, phone, country, category, type)
                .orElseGet(MetaPricingAnalytics::new);
        row.setWhatsappAccountId(wabaId);
        row.setDate(date);
        row.setPhoneNumber(phone);
        row.setCountry(country);
        row.setPricingCategory(category);
        row.setPricingType(type);
        row.setVolume(p.path("volume").asInt(0));
        row.setCost(MoneyUtil.scale(p.hasNonNull("cost") ? new BigDecimal(p.get("cost").asText()) : BigDecimal.ZERO));
        row.setCurrency(currency);
        row.setSyncedAt(Instant.now());
        repository.save(row);
    }
}
