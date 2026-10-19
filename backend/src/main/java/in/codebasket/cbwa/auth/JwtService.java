package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.user.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** HS512 access tokens (15 min). Refresh tokens are opaque random values handled by AuthService. */
@Service
public class JwtService {

    public static class TokenExpiredException extends RuntimeException {
        public TokenExpiredException() {
            super("expired");
        }
    }

    public static class TokenInvalidException extends RuntimeException {
        public TokenInvalidException() {
            super("invalid");
        }
    }

    private static final String ISSUER = "cbwa";
    private final SecretKey key;
    private final int accessTtlMinutes;

    public JwtService(AppProperties props) {
        String secret = props.getSecurity().getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("CBWA_JWT_SECRET is not set (base64, at least 64 bytes)");
        }
        byte[] raw = Base64.getDecoder().decode(secret.trim());
        if (raw.length < 64) {
            throw new IllegalStateException("CBWA_JWT_SECRET must decode to at least 64 bytes");
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.accessTtlMinutes = props.getSecurity().getAccessTtlMinutes();
    }

    public int accessTtlSeconds() {
        return accessTtlMinutes * 60;
    }

    public String createAccessToken(AuthPrincipal p) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .subject(p.uid())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds())))
                .claim("uidn", p.userId())
                .claim("em", p.email())
                .claim("nm", p.name())
                .claim("typ", p.userType().name())
                .claim("sa", p.superAdmin())
                .claim("perms", p.permissions() == null ? List.of() : List.copyOf(p.permissions()))
                .claim("mcp", p.mustChangePassword());
        if (p.partnerId() != null) {
            builder.claim("pid", p.partnerId());
        }
        if (p.impersonatorUserId() != null) {
            builder.claim("imp", p.impersonatorUserId());
        }
        return builder.signWith(key, Jwts.SIG.HS512).compact();
    }

    public AuthPrincipal parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build().parseSignedClaims(token).getPayload();
            Set<String> perms = new HashSet<>();
            Object raw = c.get("perms");
            if (raw instanceof Collection<?> col) {
                col.forEach(o -> perms.add(String.valueOf(o)));
            }
            return new AuthPrincipal(
                    toLong(c.get("uidn")), c.getSubject(), c.get("em", String.class), c.get("nm", String.class),
                    UserType.valueOf(c.get("typ", String.class)), toLong(c.get("pid")),
                    Boolean.TRUE.equals(c.get("sa", Boolean.class)), perms, toLong(c.get("imp")), null,
                    Boolean.TRUE.equals(c.get("mcp", Boolean.class)));
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidException();
        }
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(o.toString());
    }
}
