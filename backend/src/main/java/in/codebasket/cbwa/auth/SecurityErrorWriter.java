package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.util.JsonUtil;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes the standard error envelope from servlet filters (outside Spring MVC). */
public final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.getStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JsonUtil.toJson(ApiResponse.error(code, message, null, null)));
    }
}
