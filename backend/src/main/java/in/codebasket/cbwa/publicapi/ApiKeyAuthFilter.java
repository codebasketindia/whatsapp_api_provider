package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.auth.AuthPrincipal;
import in.codebasket.cbwa.auth.SecurityErrorWriter;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.web.ClientIp;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.partner.PartnerStatus;
import in.codebasket.cbwa.user.UserType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates /api/v1/public/** with X-API-KEY + X-API-SECRET, enforces the optional IP
 * whitelist and the per-key rate limit (Bucket4j, admin configurable).
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final PartnerRepository partnerRepository;
    private final ApiSecretHasher hasher;
    private final SettingsService settings;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastUsedWritten = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("X-API-KEY");
        String secret = request.getHeader("X-API-SECRET");
        if (key == null || secret == null) {
            SecurityErrorWriter.write(response, ErrorCode.API_KEY_INVALID, "X-API-KEY and X-API-SECRET headers are required");
            return;
        }
        ApiKey apiKey = apiKeyRepository.findByApiKey(key.trim()).orElse(null);
        if (apiKey == null || !hasher.matches(secret.trim(), apiKey.getSecretHash())) {
            SecurityErrorWriter.write(response, ErrorCode.API_KEY_INVALID, null);
            return;
        }
        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            SecurityErrorWriter.write(response, ErrorCode.API_KEY_DISABLED, null);
            return;
        }
        String ip = ClientIp.of(request);
        List<String> whitelist = apiKey.getIpWhitelist();
        if (whitelist != null && !whitelist.isEmpty() && !whitelist.contains(ip)) {
            SecurityErrorWriter.write(response, ErrorCode.IP_NOT_WHITELISTED, "Requests from " + ip + " are not allowed for this key");
            return;
        }
        Partner partner = partnerRepository.findById(apiKey.getPartnerId()).orElse(null);
        if (partner == null || partner.getStatus() != PartnerStatus.ACTIVE) {
            SecurityErrorWriter.write(response, ErrorCode.PARTNER_SUSPENDED, null);
            return;
        }
        int limit = apiKey.getRateLimitPerMinute() != null ? apiKey.getRateLimitPerMinute()
                : settings.getInt(SettingKeys.RATE_LIMIT, SettingKeys.PUBLIC_API_PER_MINUTE, 600);
        int perMinute = Math.max(1, limit);
        Bucket bucket = buckets.compute(apiKey.getApiKey() + ":" + perMinute, (k, existing) -> existing != null ? existing
                : Bucket.builder().addLimit(Bandwidth.builder().capacity(perMinute)
                .refillGreedy(perMinute, Duration.ofMinutes(1)).build()).build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", String.valueOf(perMinute));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));
        if (!probe.isConsumed()) {
            response.setHeader("Retry-After", String.valueOf(Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L)));
            SecurityErrorWriter.write(response, ErrorCode.RATE_LIMITED, null);
            return;
        }
        touch(apiKey, ip);
        AuthPrincipal principal = new AuthPrincipal(null, partner.getUid(), partner.getEmail(), partner.getBusinessName(),
                UserType.PARTNER, partner.getId(), false, Set.of(), null, apiKey.getId(), false);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_API")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    /** Updates last-used info at most once a minute per key to avoid a write on every call. */
    private void touch(ApiKey apiKey, String ip) {
        Instant now = Instant.now();
        Instant last = lastUsedWritten.get(apiKey.getId());
        if (last == null || last.isBefore(now.minusSeconds(60)) || !ip.equals(apiKey.getLastUsedIp())) {
            lastUsedWritten.put(apiKey.getId(), now);
            try {
                apiKeyRepository.touch(apiKey.getId(), now, ip);
            } catch (RuntimeException e) {
                // usage stats are best effort and must never block an API call
                logger.debug("Could not update API key usage", e);
            }
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanup() {
        buckets.clear();
    }
}
