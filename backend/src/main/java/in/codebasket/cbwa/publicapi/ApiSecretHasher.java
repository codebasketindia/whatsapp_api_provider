package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.config.AppProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * API secrets are high-entropy random values, so a keyed HMAC (pepper from env) is enough and
 * keeps per-request verification fast. The plain secret is shown to the partner only once.
 */
@Component
public class ApiSecretHasher {

    private final byte[] pepper;

    public ApiSecretHasher(AppProperties props) {
        String p = props.getSecurity().getApiSecretPepper();
        if (p == null || p.isBlank()) {
            throw new IllegalStateException("CBWA_API_SECRET_PEPPER is not set");
        }
        this.pepper = Base64.getDecoder().decode(p.trim());
    }

    public String hash(String secret) {
        return HashUtil.hmacSha256Hex(pepper, secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean matches(String secret, String hash) {
        return secret != null && HashUtil.constantTimeEquals(hash(secret), hash);
    }
}
