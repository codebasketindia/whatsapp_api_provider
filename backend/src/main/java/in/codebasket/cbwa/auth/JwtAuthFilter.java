package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.common.api.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Authenticates dashboard calls with the Bearer access token. Authorities: ROLE_ADMIN / ROLE_PARTNER
 * plus PERM_<code> for each permission (super admin receives every permission at login).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Endpoints allowed while the user must change the password. */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of("/api/me", "/api/me/change-password",
            "/api/auth/logout", "/api/me/notifications/unread-count");

    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/v1/public/") || uri.startsWith("/api/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        AuthPrincipal principal;
        try {
            principal = jwtService.parse(header.substring(7).trim());
        } catch (JwtService.TokenExpiredException e) {
            SecurityErrorWriter.write(response, ErrorCode.AUTH_TOKEN_EXPIRED, null);
            return;
        } catch (JwtService.TokenInvalidException e) {
            SecurityErrorWriter.write(response, ErrorCode.AUTH_TOKEN_INVALID, null);
            return;
        }
        if (principal.mustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(request.getRequestURI())) {
            SecurityErrorWriter.write(response, ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED, null);
            return;
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.userType().name()));
        principal.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
