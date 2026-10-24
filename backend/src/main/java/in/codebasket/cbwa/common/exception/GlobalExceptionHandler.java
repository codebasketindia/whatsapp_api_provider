package in.codebasket.cbwa.common.exception;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.FieldErrorDto;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleApp(AppException ex) {
        if (ex.getCode().getStatus().is5xxServerError()) {
            log.warn("Business error {}: {}", ex.getCode(), ex.getMessage());
        }
        return build(ex.getCode(), ex.getMessage(), ex.getData(), ex.getErrors());
    }

    @ExceptionHandler(in.codebasket.cbwa.meta.MetaApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleMeta(in.codebasket.cbwa.meta.MetaApiException ex) {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("metaCode", ex.getCode());
        data.put("metaSubcode", ex.getSubcode());
        data.put("friendly", ex.friendlyMessage());
        data.put("fbtraceId", ex.getFbtraceId());
        ErrorCode code = ex.isTokenInvalid() ? ErrorCode.META_TOKEN_INVALID : ErrorCode.META_API_ERROR;
        return build(code, ex.friendlyMessage(), data, null);
    }

    @ExceptionHandler(in.codebasket.cbwa.common.crypto.SecretUnreadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecret(in.codebasket.cbwa.common.crypto.SecretUnreadableException ex) {
        log.error("Stored secret cannot be decrypted: {}", ex.getMessage());
        return build(ErrorCode.CONFLICT, "A saved key or token can't be read. Please enter it again (or reconnect the number)", null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDto(fe.getField(), fe.getDefaultMessage()))
                .toList();
        List<FieldErrorDto> global = ex.getBindingResult().getGlobalErrors().stream()
                .map(ge -> new FieldErrorDto(ge.getObjectName(), ge.getDefaultMessage()))
                .toList();
        List<FieldErrorDto> all = new java.util.ArrayList<>(errors);
        all.addAll(global);
        return build(ErrorCode.VALIDATION_FAILED, null, null, all);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    int dot = path.lastIndexOf('.');
                    return new FieldErrorDto(dot >= 0 ? path.substring(dot + 1) : path, v.getMessage());
                })
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, null, null, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(Exception ex) {
        String message = switch (ex) {
            case MissingServletRequestParameterException m -> "Missing parameter: " + m.getParameterName();
            case MissingServletRequestPartException m -> "Missing file/part: " + m.getRequestPartName();
            case MethodArgumentTypeMismatchException m -> "Invalid value for: " + m.getName();
            default -> ErrorCode.BAD_REQUEST.getDefaultMessage();
        };
        return build(ErrorCode.BAD_REQUEST, message, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.BAD_REQUEST, "Method not supported", null, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "Not found", null, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUpload(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.FILE_TOO_LARGE, null, null, null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Object>> handleDenied(Exception ex) {
        return build(ErrorCode.FORBIDDEN, null, null, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimistic(OptimisticLockingFailureException ex) {
        return build(ErrorCode.CONFLICT, "This record was changed by someone else. Please reload and try again", null, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.CONFLICT, "This record already exists or is linked to other data", null, null);
    }

    /**
     * Unknown enum values in filters (e.g. status=NOPE) surface as IllegalArgumentException, sometimes
     * wrapped by Spring Data (InvalidDataAccessApiUsageException). They are client errors, not 500s.
     */
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.dao.InvalidDataAccessApiUsageException.class})
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(RuntimeException ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage() == null ? "" : root.getMessage();
        if (root instanceof IllegalArgumentException && msg.startsWith("No enum constant")) {
            return build(ErrorCode.BAD_REQUEST, "Invalid value: " + msg.substring(msg.lastIndexOf('.') + 1), null, null);
        }
        log.error("Unhandled error", ex);
        return build(ErrorCode.INTERNAL_ERROR, null, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAll(Exception ex) {
        log.error("Unhandled error", ex);
        return build(ErrorCode.INTERNAL_ERROR, null, null, null);
    }

    private ResponseEntity<ApiResponse<Object>> build(ErrorCode code, String message, Object data, List<FieldErrorDto> errors) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code, message, data, errors));
    }
}
