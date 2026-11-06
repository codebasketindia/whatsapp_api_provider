package in.codebasket.cbwa.security;

import in.codebasket.cbwa.common.entity.RefreshToken;
import in.codebasket.cbwa.common.repository.RefreshTokenRepository;

import in.codebasket.cbwa.security.AuthDtos.TokenDto;
import in.codebasket.cbwa.common.utils.HashUtil;
import in.codebasket.cbwa.common.utils.TokenGenerator;
import in.codebasket.cbwa.common.utils.Ulids;
import in.codebasket.cbwa.common.utils.ClientIp;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.repository.PartnerRepository;
import in.codebasket.cbwa.common.entity.User;
import in.codebasket.cbwa.security.UserMapper;
import in.codebasket.cbwa.common.repository.UserRepository;
import in.codebasket.cbwa.common.constants.UserType;
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
                impersonatorUserId, null, impersonatorUserId == null && user.isMustChangePassword());
    }
}
