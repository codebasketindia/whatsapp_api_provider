package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.crypto.HashUtil;
import in.codebasket.cbwa.common.crypto.TokenGenerator;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.mail.EmailTemplates;
import in.codebasket.cbwa.common.mail.MailService;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 6-digit email OTPs for password reset and admin 2FA: 10 min validity, 5 attempts, 3 resends. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    public static final int VALID_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESENDS = 3;
    private static final int RESEND_COOLDOWN_SECONDS = 30;

    private final UserOtpRepository repository;
    private final MailService mailService;
    private final AppProperties props;

    @Transactional
    public String create(User user, OtpPurpose purpose) {
        String otp = TokenGenerator.numericOtp(6);
        UserOtp o = new UserOtp();
        o.setUserId(user.getId());
        o.setChallengeId(Ulids.next());
        o.setPurpose(purpose);
        o.setOtpHash(hash(o.getChallengeId(), otp));
        o.setExpiresAt(Instant.now().plusSeconds(VALID_MINUTES * 60L));
        o.setCreatedAt(Instant.now());
        repository.save(o);
        send(user, purpose, otp);
        return o.getChallengeId();
    }

    /** Verifies and consumes the OTP; returns the user id it belongs to. */
    @Transactional(noRollbackFor = AppException.class)
    public Long verify(String challengeId, String otp, OtpPurpose purpose) {
        UserOtp o = repository.findByChallengeId(challengeId)
                .filter(x -> x.getPurpose() == purpose)
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_OTP_INVALID));
        if (o.getConsumedAt() != null || o.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.AUTH_OTP_EXPIRED);
        }
        if (o.getAttempts() >= MAX_ATTEMPTS) {
            throw new AppException(ErrorCode.AUTH_OTP_EXPIRED, "Too many wrong attempts. Please request a new code");
        }
        if (!HashUtil.constantTimeEquals(hash(challengeId, otp), o.getOtpHash())) {
            o.setAttempts(o.getAttempts() + 1);
            repository.save(o);
            throw new AppException(ErrorCode.AUTH_OTP_INVALID);
        }
        o.setConsumedAt(Instant.now());
        repository.save(o);
        return o.getUserId();
    }

    @Transactional
    public int resend(String challengeId, User user) {
        UserOtp o = repository.findByChallengeId(challengeId).orElseThrow(() -> new AppException(ErrorCode.AUTH_OTP_INVALID));
        if (o.getConsumedAt() != null) {
            throw new AppException(ErrorCode.AUTH_OTP_EXPIRED);
        }
        if (o.getResendCount() >= MAX_RESENDS) {
            throw new AppException(ErrorCode.RATE_LIMITED, "Too many resend attempts. Please start again");
        }
        Instant lastSent = o.getExpiresAt().minusSeconds(VALID_MINUTES * 60L);
        if (lastSent.plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(Instant.now())) {
            throw new AppException(ErrorCode.RATE_LIMITED, "Please wait a few seconds before requesting a new code");
        }
        String otp = TokenGenerator.numericOtp(6);
        o.setOtpHash(hash(challengeId, otp));
        o.setAttempts(0);
        o.setResendCount(o.getResendCount() + 1);
        o.setExpiresAt(Instant.now().plusSeconds(VALID_MINUTES * 60L));
        repository.save(o);
        send(user, o.getPurpose(), otp);
        return RESEND_COOLDOWN_SECONDS;
    }

    public Long userIdOf(String challengeId) {
        return repository.findByChallengeId(challengeId).map(UserOtp::getUserId)
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_OTP_INVALID));
    }

    private void send(User user, OtpPurpose purpose, String otp) {
        String title = purpose == OtpPurpose.LOGIN_2FA ? "Your login code" : "Reset your password";
        String intro = purpose == OtpPurpose.LOGIN_2FA ? "Use this code to finish logging in:" : "Use this code to reset your password:";
        String html = EmailTemplates.otp(title, intro, otp, VALID_MINUTES);
        if (props.isDevMode()) {
            // Dev only: lets developers log in without SMTP. Never enabled in the prod profile.
            log.info("[DEV] OTP for {} ({}): {}", user.getEmail(), purpose, otp);
        }
        NotificationService.afterCommit(() -> mailService.sendAsync(user.getEmail(), title, html));
    }

    private static String hash(String challengeId, String otp) {
        return HashUtil.sha256Hex(challengeId + ":" + otp);
    }
}
