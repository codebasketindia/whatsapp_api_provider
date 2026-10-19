package in.codebasket.cbwa.meta;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to the singleton Meta settings with decrypted secrets (never returned to clients). */
@Service
@RequiredArgsConstructor
public class MetaSettingsService {

    private final MetaSettingsRepository repository;
    private final CryptoService crypto;

    @Transactional(readOnly = true)
    public MetaSettings get() {
        return repository.findById(MetaSettings.SINGLETON_ID).orElseThrow(() -> new AppException(ErrorCode.META_NOT_CONFIGURED));
    }

    public String graphVersion() {
        String v = get().getGraphApiVersion();
        return v == null || v.isBlank() ? "v25.0" : v;
    }

    public String appId() {
        String id = get().getAppId();
        if (id == null || id.isBlank()) {
            throw new AppException(ErrorCode.META_NOT_CONFIGURED, "Meta App ID is not configured");
        }
        return id;
    }

    public String appSecret() {
        String s = crypto.decrypt(get().getAppSecretEnc());
        if (s == null || s.isBlank()) {
            throw new AppException(ErrorCode.META_NOT_CONFIGURED, "Meta App Secret is not configured");
        }
        return s;
    }

    /** App secret or null (used by the webhook signature check, which must not throw). */
    public String appSecretOrNull() {
        MetaSettings m = repository.findById(MetaSettings.SINGLETON_ID).orElse(null);
        return m == null ? null : crypto.decrypt(m.getAppSecretEnc());
    }

    public String verifyTokenOrNull() {
        MetaSettings m = repository.findById(MetaSettings.SINGLETON_ID).orElse(null);
        return m == null ? null : crypto.decrypt(m.getWebhookVerifyTokenEnc());
    }

    /** App access token "app_id|app_secret" for /debug_token and app-level calls. */
    public String appAccessToken() {
        return appId() + "|" + appSecret();
    }
}
