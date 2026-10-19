package in.codebasket.cbwa.meta;

import lombok.Getter;

/** Error returned by the Graph API (or a network failure when httpStatus == 0). */
@Getter
public class MetaApiException extends RuntimeException {

    private final int httpStatus;
    private final Integer code;
    private final Integer subcode;
    private final String details;
    private final String fbtraceId;

    public MetaApiException(int httpStatus, Integer code, Integer subcode, String message, String details, String fbtraceId) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.subcode = subcode;
        this.details = details;
        this.fbtraceId = fbtraceId;
    }

    public boolean isRetryable() {
        return MetaErrorMapper.isRetryable(httpStatus, code);
    }

    public boolean isTokenInvalid() {
        return code != null && code == 190;
    }

    public String friendlyMessage() {
        return MetaErrorMapper.friendly(code, getMessage(), details);
    }
}
