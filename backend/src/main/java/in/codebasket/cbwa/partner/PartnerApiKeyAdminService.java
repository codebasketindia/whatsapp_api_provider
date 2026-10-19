package in.codebasket.cbwa.partner;

import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.partner.dto.PartnerDtos.ApiKeyDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.ApiKeyPatchRequest;
import in.codebasket.cbwa.publicapi.ApiKey;
import in.codebasket.cbwa.publicapi.ApiKeyRepository;
import in.codebasket.cbwa.publicapi.ApiKeyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P16-P17: admin view of a partner's API keys (enable/disable, per-key rate limit). Secrets are never exposed. */
@Service
@RequiredArgsConstructor
public class PartnerApiKeyAdminService {

    private final PartnerService partnerService;
    private final ApiKeyRepository apiKeyRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ApiKeyDto> list(Long partnerId) {
        partnerService.require(partnerId);
        return apiKeyRepository.findByPartnerIdOrderByIdDesc(partnerId).stream().map(PartnerApiKeyAdminService::toDto).toList();
    }

    @Transactional
    public ApiKeyDto patch(Long partnerId, Long keyId, ApiKeyPatchRequest req) {
        ApiKey k = apiKeyRepository.findByIdAndPartnerId(keyId, partnerId).orElseThrow(() -> AppException.notFound("API key"));
        Map<String, Object> old = snapshot(k);
        if (req.status() != null) {
            k.setStatus(ApiKeyStatus.valueOf(req.status()));
        }
        if (Boolean.TRUE.equals(req.clearRateLimit())) {
            k.setRateLimitPerMinute(null);
        } else if (req.rateLimitPerMinute() != null) {
            k.setRateLimitPerMinute(req.rateLimitPerMinute());
        }
        apiKeyRepository.save(k);
        auditService.log("API_KEY_ADMIN_UPDATE", "API_KEY", k.getId(), old, snapshot(k), partnerId);
        return toDto(k);
    }

    private static Map<String, Object> snapshot(ApiKey k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", k.getStatus());
        m.put("rateLimitPerMinute", k.getRateLimitPerMinute());
        return m;
    }

    static ApiKeyDto toDto(ApiKey k) {
        return new ApiKeyDto(k.getId(), k.getName(), k.getApiKey(), k.getSecretLast4(),
                k.getIpWhitelist() == null ? List.of() : k.getIpWhitelist(), k.getRateLimitPerMinute(), k.getStatus().name(),
                k.getLastUsedAt(), k.getLastUsedIp(), k.getCreatedAt());
    }
}
