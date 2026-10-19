package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.TokenGenerator;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.publicapi.dto.ApiKeyDtos.ApiKeyDto;
import in.codebasket.cbwa.publicapi.dto.ApiKeyDtos.CreateApiKeyRequest;
import in.codebasket.cbwa.publicapi.dto.ApiKeyDtos.UpdateApiKeyRequest;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Partner API keys (D1–D5). The plain secret is returned only by create / regenerate. */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    public static final int MAX_KEYS = 5;

    private final ApiKeyRepository repository;
    private final ApiKeyUsageRepository usageRepository;
    private final ApiSecretHasher hasher;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ApiKeyDto> list(Long partnerId) {
        return repository.findByPartnerIdOrderByIdDesc(partnerId).stream().map(k -> toDto(k, null)).toList();
    }

    @Transactional
    public ApiKeyDto create(Long partnerId, CreateApiKeyRequest req) {
        if (repository.countByPartnerId(partnerId) >= MAX_KEYS) {
            throw AppException.conflict("You can have at most " + MAX_KEYS + " API keys. Delete an unused key first");
        }
        ApiKey k = new ApiKey();
        k.setPartnerId(partnerId);
        k.setName(req.name().trim());
        k.setApiKey("cbk_live_" + TokenGenerator.alphanumeric(24));
        k.setIpWhitelist(IpAddressValidator.normalise(req.ipWhitelist()));
        k.setStatus(ApiKeyStatus.ACTIVE);
        String secret = newSecret(k);
        repository.save(k);
        auditService.log("API_KEY_CREATE", "API_KEY", k.getId(), null, summary(k), partnerId);
        return toDto(k, secret);
    }

    @Transactional
    public ApiKeyDto update(Long partnerId, Long id, UpdateApiKeyRequest req) {
        ApiKey k = get(partnerId, id);
        Map<String, Object> old = summary(k);
        if (req.name() != null && !req.name().isBlank()) {
            k.setName(req.name().trim());
        }
        if (req.ipWhitelist() != null) {
            k.setIpWhitelist(IpAddressValidator.normalise(req.ipWhitelist()));
        }
        if (req.status() != null) {
            k.setStatus(ApiKeyStatus.valueOf(req.status()));
        }
        repository.save(k);
        auditService.log("API_KEY_UPDATE", "API_KEY", k.getId(), old, summary(k), partnerId);
        return toDto(k, null);
    }

    /** D4: requires the partner user's password. The old secret stops working immediately. */
    @Transactional
    public ApiKeyDto regenerate(Long partnerId, Long id, String password) {
        Long userId = SecurityUtils.current().userId();
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Password is incorrect");
        }
        ApiKey k = get(partnerId, id);
        String secret = newSecret(k);
        repository.save(k);
        auditService.log("API_KEY_REGENERATE", "API_KEY", k.getId(), null, summary(k), partnerId);
        return toDto(k, secret);
    }

    /**
     * D5: keys never used to send messages are deleted; keys referenced by messages are kept for
     * history (FK messages.api_key_id) but permanently DISABLED.
     */
    @Transactional
    public void delete(Long partnerId, Long id) {
        ApiKey k = get(partnerId, id);
        Map<String, Object> old = summary(k);
        if (usageRepository.isUsed(k.getId())) {
            k.setStatus(ApiKeyStatus.DISABLED);
            repository.save(k);
        } else {
            repository.delete(k);
        }
        auditService.log("API_KEY_DELETE", "API_KEY", id, old, null, partnerId);
    }

    private ApiKey get(Long partnerId, Long id) {
        return repository.findByIdAndPartnerId(id, partnerId).orElseThrow(() -> AppException.notFound("API key"));
    }

    private String newSecret(ApiKey k) {
        String secret = "cbs_" + TokenGenerator.alphanumeric(40);
        k.setSecretHash(hasher.hash(secret));
        k.setSecretLast4(secret.substring(secret.length() - 4));
        return secret;
    }

    private static Map<String, Object> summary(ApiKey k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", k.getName());
        m.put("apiKey", k.getApiKey());
        m.put("status", k.getStatus());
        m.put("ipWhitelist", k.getIpWhitelist());
        return m;
    }

    public static ApiKeyDto toDto(ApiKey k, String secret) {
        return new ApiKeyDto(k.getId(), k.getName(), k.getApiKey(), k.getSecretLast4(),
                k.getIpWhitelist() == null ? List.of() : k.getIpWhitelist(), k.getRateLimitPerMinute(), k.getStatus().name(),
                k.getLastUsedAt(), k.getLastUsedIp(), k.getCreatedAt(), secret);
    }
}
