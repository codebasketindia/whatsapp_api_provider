package in.codebasket.cbwa.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client IP and user agent of the current request. Nginx sets X-Forwarded-For and Spring's
 * forward-headers-strategy=native resolves getRemoteAddr() through it.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String current() {
        return of(currentRequest());
    }

    public static String userAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String ua = req.getHeader("User-Agent");
        return ua == null ? null : ua.substring(0, Math.min(255, ua.length()));
    }
}
