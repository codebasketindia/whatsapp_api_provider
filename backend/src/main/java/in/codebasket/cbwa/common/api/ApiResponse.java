package in.codebasket.cbwa.common.api;

import java.util.List;

/** Standard envelope: { success, code, message, data, errors }. */
public record ApiResponse<T>(boolean success, String code, String message, T data, List<FieldErrorDto> errors) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "Success", data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, "OK", message, data, null);
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return new ApiResponse<>(true, "CREATED", message, data, null);
    }

    public static <T> ApiResponse<T> custom(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, T data, List<FieldErrorDto> errors) {
        return new ApiResponse<>(false, code.name(), message != null ? message : code.getDefaultMessage(), data, errors);
    }
}
