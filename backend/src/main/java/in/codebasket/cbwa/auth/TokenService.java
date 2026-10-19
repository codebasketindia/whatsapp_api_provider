package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.auth.dto.AuthDtos.TokenDto;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.crypto.TokenGenerator;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.common.web.ClientIp;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserMapper;
import in.codebasket.cbwa.user.UserRepository;
import in.codebasket.cbwa.user.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;

/** Issues access + rotating refresh tokens. Impersonation sessions get a 1 hour refresh token. */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Duration IMPERSONATION_TTL = Duration.ofHours(1);

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PartnerRepository partnerRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AppProperties props;

    @Transactional
    public TokenDto issue(User user, Long impersonatorUserId, String familyId) {
        Partner partner = user.getUserType() == UserType.PARTNER ? partnerRepository.findByUserId(user.getId()).orElse(null) : null;
        User impersonator = impersonatorUserId == null ? null : userRepository.findById(impersonatorUserId).orElse(null);
        AuthPrincipal principal = principalOf(user, partner, impersonatorUserId);
        String access = jwtService.createAccessToken(principal);

        String rawRefresh = TokenGenerator.urlSafe(32);
        Duration ttl = impersonatorUserId != null ? IMPERSONATION_TTL : Duration.ofDays(props.getSecurity().getRefreshTtlDays());
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(HashUtil.sha256Hex(rawRefresh));
        rt.setFamilyId(familyId != null ? familyId : Ulids.next());
        rt.setIssuedAt(Instant.now());
        rt.setExpiresAt(Instant.now().plus(ttl));
        rt.setImpersonatorUserId(impersonatorUserId);
        rt.setIp(ClientIp.current());
        rt.setUserAgent(ClientIp.userAgent());
        refreshTokenRepository.save(rt);
        lastIssued.set(rt);
        return new TokenDto(access, rawRefresh, jwtService.accessTtlSeconds(), ttl.toSeconds(),
                userMapper.toDto(user, partner, impersonator));
    }

    /** The refresh token row created by the last issue() call on this thread (used for rotation links). */
    private final ThreadLocal<RefreshToken> lastIssued = new ThreadLocal<>();

    public RefreshToken takeLastIssued() {
        RefreshToken rt = lastIssued.get();
        lastIssued.remove();
        return rt;
    }

    public static AuthPrincipal principalOf(User user, Partner partner, Long impersonatorUserId) {
        return new AuthPrincipal(user.getId(), user.getUid(), user.getEmail(), user.getName(), user.getUserType(),
                partner == null ? null : partner.getId(), user.isSuperAdmin(), new HashSet<>(UserMapper.permissionsOf(user)),
                impersonatorUserId, null, user.isMustChangePassword());
    }
}
