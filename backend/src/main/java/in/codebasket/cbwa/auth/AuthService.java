package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.auth.dto.AuthDtos.ChallengeDto;
import in.codebasket.cbwa.auth.dto.AuthDtos.TokenDto;
import in.codebasket.cbwa.auth.dto.AuthDtos.TwoFactorChallenge;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.common.web.ClientIp;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.partner.PartnerStatus;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserRepository;
import in.codebasket.cbwa.user.UserStatus;
import in.codebasket.cbwa.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** BCrypt hash of a random value, used to keep timing equal when the email does not exist. */
    private volatile String dummyHash;

    private final UserRepository userRepository;
    private final PartnerRepository partnerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OtpService otpService;
    private final SettingsService settings;
    private final AuditService auditService;

    /** A1: returns TokenDto, or TwoFactorChallenge for admins with 2FA. */
    @Transactional(noRollbackFor = AppException.class)
    public Object login(String mobile, String email, String password) {
        User user = findLoginUser(mobile, email);
        if (user == null) {
            if (dummyHash == null) {
                dummyHash = passwordEncoder.encode(in.codebasket.cbwa.common.crypto.TokenGenerator.urlSafe(16));
            }
            passwordEncoder.matches(password, dummyHash);
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AppException(ErrorCode.AUTH_ACCOUNT_LOCKED, null, Map.of("lockedUntil", user.getLockedUntil()));
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int max = settings.getInt(SettingKeys.SECURITY, SettingKeys.MAX_LOGIN_ATTEMPTS, 5);
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= max) {
                int minutes = settings.getInt(SettingKeys.SECURITY, SettingKeys.LOCKOUT_MINUTES, 15);
                user.setLockedUntil(now.plusSeconds(minutes * 60L));
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
                log.warn("User {} locked after {} failed logins", user.getUid(), attempts);
                throw new AppException(ErrorCode.AUTH_ACCOUNT_LOCKED, null, Map.of("lockedUntil", user.getLockedUntil()));
            }
            userRepository.save(user);
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        ensureCanLogin(user);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        boolean twoFactor = user.getUserType() == UserType.ADMIN
                && (user.isTwoFactorEnabled() || settings.getBool(SettingKeys.SECURITY, SettingKeys.ADMIN_2FA_REQUIRED, false));
        if (twoFactor) {
            userRepository.save(user);
            String challengeId = otpService.create(user, OtpPurpose.LOGIN_2FA);
            return new TwoFactorChallenge(true, challengeId, maskEmail(user.getEmail()));
        }
        return completeLogin(user);
    }

    /** A2 */
    @Transactional(noRollbackFor = AppException.class)
    public TokenDto verifyTwoFactor(String challengeId, String otp) {
        Long userId = otpService.verify(challengeId, otp, OtpPurpose.LOGIN_2FA);
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.AUTH_OTP_INVALID));
        ensureCanLogin(user);
        return completeLogin(user);
    }

    /** A3 */
    @Transactional
    public int resendOtp(String challengeId) {
        Long userId = otpService.userIdOf(challengeId);
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.AUTH_OTP_INVALID));
        return otpService.resend(challengeId, user);
    }

    /** A4: rotates the refresh token. Reuse of an already-rotated token revokes the whole family. */
    @Transactional(noRollbackFor = AppException.class)
    public TokenDto refresh(String rawRefreshToken) {
        RefreshToken rt = refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_REFRESH_INVALID));
        Instant now = Instant.now();
        if (rt.getRevokedAt() != null) {
            if (rt.getReplacedById() != null) {
                log.warn("Refresh token reuse detected for user {} - revoking family", rt.getUserId());
                refreshTokenRepository.revokeFamily(rt.getFamilyId(), now);
            }
            throw new AppException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        if (rt.getExpiresAt().isBefore(now)) {
            throw new AppException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        User user = userRepository.findById(rt.getUserId()).orElseThrow(() -> new AppException(ErrorCode.AUTH_REFRESH_INVALID));
        try {
            ensureCanLogin(user);
        } catch (AppException e) {
            refreshTokenRepository.revokeFamily(rt.getFamilyId(), now);
            throw new AppException(ErrorCode.AUTH_REFRESH_INVALID, e.getMessage());
        }
        TokenDto dto = tokenService.issue(user, rt.getImpersonatorUserId(), rt.getFamilyId());
        RefreshToken next = tokenService.takeLastIssued();
        rt.setRevokedAt(now);
        rt.setReplacedById(next.getId());
        refreshTokenRepository.save(rt);
        return dto;
    }

    /** A5 */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(rawRefreshToken)).ifPresent(rt -> {
            AuthPrincipal p = SecurityUtils.currentOrNull();
            if (p == null || p.userId() == null || p.userId().equals(rt.getUserId())) {
                refreshTokenRepository.revokeFamily(rt.getFamilyId(), Instant.now());
            }
        });
    }

    /** A6: always succeeds (no account enumeration). */
    @Transactional
    public ChallengeDto forgotPassword(String mobile, String email) {
        return java.util.Optional.ofNullable(findLoginUser(mobile, email))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(u -> new ChallengeDto(otpService.create(u, OtpPurpose.PASSWORD_RESET)))
                .orElseGet(() -> new ChallengeDto(Ulids.next()));
    }

    /** A7 */
    @Transactional(noRollbackFor = AppException.class)
    public void resetPassword(String challengeId, String otp, String newPassword) {
        PasswordPolicy.check(newPassword);
        Long userId = otpService.verify(challengeId, otp, OtpPurpose.PASSWORD_RESET);
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.AUTH_OTP_INVALID));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        auditService.log("PASSWORD_RESET", "USER", user.getUid(), null, null);
    }

    /** A10: returns fresh tokens (old sessions revoked, mustChangePassword cleared). */
    @Transactional
    public TokenDto changePassword(String currentPassword, String newPassword) {
        AuthPrincipal p = SecurityUtils.current();
        if (p.userId() == null) {
            throw AppException.forbidden();
        }
        User user = userRepository.findById(p.userId()).orElseThrow(() -> AppException.notFound("User"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw AppException.field("newPassword", "New password must be different from the current password");
        }
        PasswordPolicy.check(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        auditService.log("PASSWORD_CHANGE", "USER", user.getUid(), null, null);
        return tokenService.issue(user, p.impersonatorUserId(), null);
    }

    private TokenDto completeLogin(User user) {
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(ClientIp.current());
        userRepository.save(user);
        return tokenService.issue(user, null, null);
    }

    private void ensureCanLogin(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (user.getUserType() == UserType.PARTNER) {
            Partner partner = partnerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.AUTH_ACCOUNT_DISABLED));
            if (partner.getStatus() != PartnerStatus.ACTIVE) {
                throw new AppException(ErrorCode.AUTH_ACCOUNT_DISABLED, "Your account is suspended. Please contact support");
            }
        }
    }

    /** Mobile number is the login id; email still works as a fallback for older clients. */
    private User findLoginUser(String mobile, String email) {
        if (mobile != null && !mobile.isBlank()) {
            if (mobile.contains("@")) {
                return userRepository.findByEmailIgnoreCase(mobile.trim()).orElse(null);
            }
            String digits = in.codebasket.cbwa.user.MobileNumbers.normaliseOrNull(mobile);
            return digits == null ? null : userRepository.findByMobile(digits).orElse(null);
        }
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        }
        throw AppException.field("mobile", "Enter your mobile number");
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(0, at));
        }
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}
