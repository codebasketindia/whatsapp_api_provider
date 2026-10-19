package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-IP buckets for unauthenticated auth endpoints (login, OTP, forgot password). */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final SettingsService settings;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void check(String scope, String ip) {
        int perMinute = Math.max(1, settings.getInt(SettingKeys.RATE_LIMIT, SettingKeys.LOGIN_PER_MINUTE, 10));
        Bucket bucket = buckets.computeIfAbsent(scope + ":" + ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build());
        if (!bucket.tryConsume(1)) {
            throw new AppException(ErrorCode.RATE_LIMITED, "Too many attempts. Please wait a minute and try again");
        }
    }

    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        buckets.clear();
    }
}
