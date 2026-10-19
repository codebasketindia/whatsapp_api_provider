package in.codebasket.cbwa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "cbwa")
public class AppProperties {

    private String publicBaseUrl;
    private String frontendUrl;
    private String corsAllowedOrigins;
    private boolean devMode;
    private Security security = new Security();
    private Seed seed = new Seed();
    private Storage storage = new Storage();
    private Mail mail = new Mail();
    private Queue queue = new Queue();
    private Meta meta = new Meta();
    private Razorpay razorpay = new Razorpay();
    private Jobs jobs = new Jobs();

    public List<String> corsOrigins() {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(corsAllowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Getter @Setter
    public static class Security {
        private String jwtSecret;
        private int accessTtlMinutes = 15;
        private int refreshTtlDays = 7;
        private String encryptionKey;
        private String apiSecretPepper;
    }

    @Getter @Setter
    public static class Seed {
        private String adminEmail;
        private String adminPassword;
    }

    @Getter @Setter
    public static class Storage {
        private String path = "./storage";
    }

    @Getter @Setter
    public static class Mail {
        private String from;
    }

    @Getter @Setter
    public static class Queue {
        private int workerThreads = 8;
        private int batchSize = 50;
    }

    @Getter @Setter
    public static class Meta {
        private String graphBaseUrl = "https://graph.facebook.com";
    }

    @Getter @Setter
    public static class Razorpay {
        private String baseUrl = "https://api.razorpay.com";
    }

    @Getter @Setter
    public static class Jobs {
        private boolean enabled = true;
    }
}
