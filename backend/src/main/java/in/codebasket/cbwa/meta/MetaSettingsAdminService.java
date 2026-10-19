package in.codebasket.cbwa.meta;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.meta.dto.MetaSettingsDtos.MetaSettingsDto;
import in.codebasket.cbwa.meta.dto.MetaSettingsDtos.TestResult;
import in.codebasket.cbwa.meta.dto.MetaSettingsDtos.UpdateMetaSettingsRequest;
import in.codebasket.cbwa.waba.TokenStatus;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WabaStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** M1-M3: platform Meta settings (F1). Secrets are encrypted and never returned. */
@Service
@RequiredArgsConstructor
public class MetaSettingsAdminService {

    private final MetaSettingsRepository repository;
    private final MetaSettingsService metaSettings;
    private final CryptoService crypto;
    private final AuditService auditService;
    private final AppProperties props;
    private final MetaWabaApi wabaApi;
    private final WhatsappAccountRepository wabaRepository;

    @Transactional(readOnly = true)
    public MetaSettingsDto get() {
        return toDto(metaSettings.get());
    }

    @Transactional
    public MetaSettingsDto update(UpdateMetaSettingsRequest req) {
        MetaSettings m = metaSettings.get();
        MetaSettingsDto old = toDto(m);
        m.setAppId(blank(req.appId()));
        if (req.appSecret() != null && !req.appSecret().isBlank()) {
            m.setAppSecretEnc(crypto.encrypt(req.appSecret().trim()));
        }
        if (req.verifyToken() != null && !req.verifyToken().isBlank()) {
            m.setWebhookVerifyTokenEnc(crypto.encrypt(req.verifyToken().trim()));
        }
        m.setEmbeddedSignupConfigId(blank(req.configId()));
        m.setSolutionId(blank(req.solutionId()));
        m.setBusinessPortfolioId(blank(req.businessPortfolioId()));
        m.setGraphApiVersion(req.graphApiVersion().trim());
        m.setBillingMode(BillingMode.valueOf(req.billingMode()));
        m.setExtendedCreditLineId(blank(req.extendedCreditLineId()));
        if (m.getBillingMode() == BillingMode.SOLUTION_PARTNER && m.getExtendedCreditLineId() == null) {
            throw AppException.field("extendedCreditLineId", "Solution Partner mode needs your extended credit line ID");
        }
        repository.save(m);
        MetaSettingsDto now = toDto(m);
        auditService.log("SETTINGS_UPDATE", "META_SETTINGS", "1", old, now);
        return now;
    }

    /** M3: checks the app credentials, a platform WABA token and the webhook subscription. */
    @Transactional(noRollbackFor = AppException.class)
    public TestResult test() {
        List<String> details = new ArrayList<>();
        boolean appOk = false;
        boolean tokenOk = false;
        boolean subscribed = false;
        MetaSettings m = metaSettings.get();
        try {
            String appToken = metaSettings.appAccessToken();
            JsonNode app = wabaApi.appInfo(metaSettings.appId(), appToken);
            appOk = true;
            details.add("App credentials OK: " + app.path("name").asText(app.path("id").asText()));
            try {
                JsonNode subs = wabaApi.appSubscriptions(metaSettings.appId(), appToken);
                for (JsonNode s : subs.path("data")) {
                    if ("whatsapp_business_account".equals(s.path("object").asText()) && s.path("active").asBoolean(true)) {
                        subscribed = true;
                        details.add("Webhook callback: " + s.path("callback_url").asText());
                    }
                }
                if (!subscribed) {
                    details.add("Webhook is not configured for whatsapp_business_account in the Meta App dashboard");
                }
            } catch (MetaApiException e) {
                details.add("Could not read webhook subscriptions: " + e.friendlyMessage());
            }
        } catch (AppException e) {
            details.add(e.getMessage());
        } catch (MetaApiException e) {
            details.add("App credentials failed: " + e.friendlyMessage());
        }
        List<WhatsappAccount> platform = wabaRepository.findAll().stream()
                .filter(w -> w.getOwnerType() == WabaOwnerType.PLATFORM && w.getStatus() != WabaStatus.DISCONNECTED).toList();
        if (platform.isEmpty()) {
            details.add("No platform WhatsApp account added yet");
        }
        for (WhatsappAccount w : platform) {
            try {
                wabaApi.getWaba(w.getWabaId(), crypto.decrypt(w.getAccessTokenEnc()));
                tokenOk = true;
                details.add("WABA " + w.getWabaId() + ": token OK");
            } catch (MetaApiException e) {
                details.add("WABA " + w.getWabaId() + ": " + e.friendlyMessage());
                if (e.isTokenInvalid()) {
                    w.setTokenStatus(TokenStatus.INVALID);
                    w.setTokenCheckedAt(Instant.now());
                    wabaRepository.save(w);
                }
            }
        }
        m.setLastTestAt(Instant.now());
        m.setLastTestOk(appOk && (platform.isEmpty() || tokenOk));
        repository.save(m);
        return new TestResult(appOk, tokenOk, subscribed, details);
    }

    public String webhookUrl() {
        String base = props.getPublicBaseUrl() == null ? "" : props.getPublicBaseUrl().replaceAll("/+$", "");
        return base + "/api/webhooks/whatsapp";
    }

    private MetaSettingsDto toDto(MetaSettings m) {
        return new MetaSettingsDto(m.getAppId(), m.getAppSecretEnc() != null, m.getEmbeddedSignupConfigId(), m.getSolutionId(),
                m.getBusinessPortfolioId(), m.getGraphApiVersion(), m.getWebhookVerifyTokenEnc() != null,
                m.getBillingMode().name(), m.getExtendedCreditLineId(), webhookUrl(), m.getLastTestAt(), m.getLastTestOk());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static void requireConfigured(MetaSettings m) {
        if (m.getAppId() == null || m.getAppSecretEnc() == null) {
            throw new AppException(ErrorCode.META_NOT_CONFIGURED);
        }
    }
}
