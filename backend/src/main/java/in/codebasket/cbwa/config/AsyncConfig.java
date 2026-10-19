package in.codebasket.cbwa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.slf4j.MDC;

import java.util.Map;

@Configuration
@EnableAsync
public class AsyncConfig {

    /** Propagates the request id to @Async threads for log correlation. */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> ctx = MDC.getCopyOfContextMap();
            return () -> {
                if (ctx != null) {
                    MDC.setContextMap(ctx);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
