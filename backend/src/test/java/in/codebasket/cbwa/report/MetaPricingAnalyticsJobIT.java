package in.codebasket.cbwa.report;

import com.github.tomakehurst.wiremock.WireMockServer;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class MetaPricingAnalyticsJobIT extends AbstractIntegrationTest {

    static final WireMockServer WM = new WireMockServer(options().dynamicPort());

    static {
        WM.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cbwa.meta.graph-base-url", WM::baseUrl);
    }

    @Autowired
    MetaPricingAnalyticsJob job;
    @Autowired
    MetaPricingAnalyticsRepository repository;

    @BeforeEach
    void reset() {
        WM.resetAll();
    }

    @Test
    void pullsPricingAnalyticsAndUpsertsIdempotently() {
        WhatsappAccount ok = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        WhatsappAccount broken = testData.waba(WabaOwnerType.PLATFORM, null, BillingMode.PLATFORM);
        long start = TimeUtil.startOfDayIst(TimeUtil.todayIst().minusDays(1)).getEpochSecond();
        WM.stubFor(get(urlPathEqualTo("/v25.0/" + ok.getWabaId()))
                .withQueryParam("fields", containing("pricing_analytics"))
                .withHeader("Authorization", equalTo("Bearer TEST_TOKEN"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"pricing_analytics":{"data":[{"data_points":[
                          {"start":%d,"end":%d,"country":"IN","pricing_type":"REGULAR","pricing_category":"MARKETING",
                           "phone_number":"919876543210","volume":5,"cost":4.25},
                          {"start":%d,"end":%d,"country":"IN","pricing_type":"FREE_CUSTOMER_SERVICE","pricing_category":"SERVICE",
                           "phone_number":"919876543210","volume":2,"cost":0}
                        ]}]},"id":"%s"}""".formatted(start, start + 86400, start, start + 86400, ok.getWabaId()))));
        WM.stubFor(get(urlPathEqualTo("/v25.0/" + broken.getWabaId()))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":{\"message\":\"bad\",\"code\":100}}")));

        assertThat(job.syncAll(3)).isEqualTo(2);
        assertThat(job.syncAll(3)).isEqualTo(2); // second run updates, no duplicates
        List<MetaPricingAnalytics> rows = repository.findAll();
        assertThat(rows).hasSize(2);
        MetaPricingAnalytics marketing = rows.stream().filter(r -> r.getPricingCategory().equals("MARKETING")).findFirst().orElseThrow();
        assertThat(marketing.getDate()).isEqualTo(TimeUtil.todayIst().minusDays(1));
        assertThat(marketing.getVolume()).isEqualTo(5);
        assertThat(marketing.getCost()).isEqualByComparingTo(new BigDecimal("4.25"));
        assertThat(marketing.getWhatsappAccountId()).isEqualTo(ok.getId());
        assertThat(LocalDate.now()).isNotNull();
    }
}
