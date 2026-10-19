package in.codebasket.cbwa.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Jobs can be switched off (e.g. in tests) with cbwa.jobs.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "cbwa.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
