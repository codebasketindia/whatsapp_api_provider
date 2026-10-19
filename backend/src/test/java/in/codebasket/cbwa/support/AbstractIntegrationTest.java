package in.codebasket.cbwa.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for integration tests against a real MySQL 8.
 *
 * - If CBWA_TEST_DB_URL is set (e.g. jdbc:mysql://127.0.0.1:3306/cbwa_test) that database is used
 *   (with CBWA_TEST_DB_USERNAME / CBWA_TEST_DB_PASSWORD). Flyway cleans and migrates it once per run.
 * - Otherwise a Testcontainers MySQL 8 container is started (requires Docker).
 *
 * All business tables are truncated before each test; seed data (roles, permissions, settings,
 * default rate plan, credit packs, meta_settings) is kept.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static MySQLContainer<?> container;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String url = System.getenv("CBWA_TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?")
                    + "useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
            registry.add("spring.datasource.username", () -> envOr("CBWA_TEST_DB_USERNAME", "cbwa_app"));
            registry.add("spring.datasource.password", () -> envOr("CBWA_TEST_DB_PASSWORD", "cbwa_dev_pass"));
        } else {
            if (container == null) {
                container = new MySQLContainer<>("mysql:8.0").withDatabaseName("cbwa").withUsername("cbwa").withPassword("cbwa");
                container.start();
            }
            registry.add("spring.datasource.url", () -> container.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestData testData;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
        testData.resetCaches();
    }
}
