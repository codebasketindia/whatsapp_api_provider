package in.codebasket.cbwa.exception;

/** A stored encrypted value cannot be decrypted (corrupted, or CBWA_ENCRYPTION_KEY changed). */
public class SecretUnreadableException extends IllegalStateException {

    public SecretUnreadableException(String message, Throwable cause) {
        super(message, cause);
    }
}
