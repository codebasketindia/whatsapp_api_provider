package in.codebasket.cbwa.meta;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration tests that need a fake Graph API. WireMock serves {@code /v25.0/...}; Meta settings
 * (app id/secret, config id) are reset before every test.
 */
public abstract class MetaTestBase extends AbstractIntegrationTest {

    protected static final String V = "/v25.0";
    protected static final WireMockServer META = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        META.start();
    }

    @DynamicPropertySource
    static void metaProps(DynamicPropertyRegistry registry) {
        registry.add("cbwa.meta.graph-base-url", () -> "http://localhost:" + META.port());
    }

    @Autowired
    protected MetaSettingsRepository metaSettingsRepository;
    @Autowired
    protected CryptoService crypto;

    @BeforeEach
    void resetMeta() {
        META.resetAll();
        MetaSettings m = metaSettingsRepository.findById(1L).orElseThrow();
        m.setAppId("123456789");
        m.setAppSecretEnc(crypto.encrypt("app-secret"));
        m.setEmbeddedSignupConfigId("987654321");
        m.setGraphApiVersion("v25.0");
        m.setBillingMode(BillingMode.TECH_PROVIDER);
        m.setExtendedCreditLineId(null);
        m.setSolutionId(null);
        metaSettingsRepository.save(m);
    }
}
