package in.codebasket.cbwa.common.exception;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.FieldErrorDto;
import lombok.Getter;

import java.util.List;

/** Business exception carrying an API error code; rendered by GlobalExceptionHandler. */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object data;
    private final List<FieldErrorDto> errors;

    public AppException(ErrorCode code) {
        this(code, code.getDefaultMessage(), null, null);
    }

    public AppException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    public AppException(ErrorCode code, String message, Object data) {
        this(code, message, data, null);
    }

    public AppException(ErrorCode code, String message, Object data, List<FieldErrorDto> errors) {
        super(message != null ? message : code.getDefaultMessage());
        this.code = code;
        this.data = data;
        this.errors = errors;
    }

    public static AppException notFound(String what) {
        return new AppException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static AppException field(String field, String message) {
        return new AppException(ErrorCode.VALIDATION_FAILED, message, null, List.of(new FieldErrorDto(field, message)));
    }

    public static AppException conflict(String message) {
        return new AppException(ErrorCode.CONFLICT, message);
    }

    public static AppException forbidden() {
        return new AppException(ErrorCode.FORBIDDEN);
    }

    public static AppException badRequest(String message) {
        return new AppException(ErrorCode.BAD_REQUEST, message);
    }
}
