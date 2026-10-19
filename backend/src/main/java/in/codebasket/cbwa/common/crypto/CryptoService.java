package in.codebasket.cbwa.common.crypto;

import in.codebasket.cbwa.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for secrets at rest (Meta tokens, PINs, gateway keys, webhook secrets).
 * Format: "v1:" + base64(iv[12] | ciphertext | tag[16]). The version prefix allows key rotation later.
 */
@Service
public class CryptoService {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(AppProperties props) {
        String b64 = props.getSecurity().getEncryptionKey();
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException("CBWA_ENCRYPTION_KEY is not set (base64 encoded 32 bytes)");
        }
        byte[] raw = Base64.getDecoder().decode(b64.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("CBWA_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct);
            return PREFIX + Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String enc) {
        if (enc == null || enc.isBlank()) {
            return null;
        }
        if (!enc.startsWith(PREFIX)) {
            throw new SecretUnreadableException("Unknown encrypted value format", null);
        }
        try {
            byte[] all = Base64.getDecoder().decode(enc.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] pt = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new SecretUnreadableException("Decryption failed - wrong CBWA_ENCRYPTION_KEY or corrupted value", e);
        }
    }
}
