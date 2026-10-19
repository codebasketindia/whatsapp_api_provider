package in.codebasket.cbwa.auth;

import in.codebasket.cbwa.auth.dto.AuthDtos.ChallengeDto;
import in.codebasket.cbwa.auth.dto.AuthDtos.ChallengeRequest;
import in.codebasket.cbwa.auth.dto.AuthDtos.ForgotPasswordRequest;
import in.codebasket.cbwa.auth.dto.AuthDtos.LoginRequest;
import in.codebasket.cbwa.auth.dto.AuthDtos.LogoutRequest;
import in.codebasket.cbwa.auth.dto.AuthDtos.RefreshRequest;
import in.codebasket.cbwa.auth.dto.AuthDtos.ResendDto;
import in.codebasket.cbwa.auth.dto.AuthDtos.ResetPasswordRequest;
import in.codebasket.cbwa.auth.dto.AuthDtos.TokenDto;
import in.codebasket.cbwa.auth.dto.AuthDtos.TwoFactorChallenge;
import in.codebasket.cbwa.auth.dto.AuthDtos.TwoFactorRequest;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.web.ClientIp;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    @PostMapping("/login")
    public ApiResponse<Object> login(@Valid @RequestBody LoginRequest req) {
        rateLimiter.check("login", ClientIp.current());
        Object result = authService.login(req.email(), req.password());
        if (result instanceof TwoFactorChallenge c) {
            return ApiResponse.custom("AUTH_2FA_REQUIRED", "Enter the code sent to " + c.maskedEmail(), c);
        }
        return ApiResponse.ok(result, "Logged in");
    }

    @PostMapping("/login/2fa")
    public ApiResponse<TokenDto> verify(@Valid @RequestBody TwoFactorRequest req) {
        rateLimiter.check("otp", ClientIp.current());
        return ApiResponse.ok(authService.verifyTwoFactor(req.challengeId(), req.otp()), "Logged in");
    }

    @PostMapping("/otp/resend")
    public ApiResponse<ResendDto> resend(@Valid @RequestBody ChallengeRequest req) {
        rateLimiter.check("otp-resend", ClientIp.current());
        return ApiResponse.ok(new ResendDto(authService.resendOtp(req.challengeId())), "A new code has been sent");
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenDto> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest req) {
        authService.logout(req == null ? null : req.refreshToken());
        return ApiResponse.ok(null, "Logged out");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<ChallengeDto> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        rateLimiter.check("forgot", ClientIp.current());
        return ApiResponse.ok(authService.forgotPassword(req.email()),
                "If this email is registered, a reset code has been sent");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        rateLimiter.check("otp", ClientIp.current());
        authService.resetPassword(req.challengeId(), req.otp(), req.newPassword());
        return ApiResponse.ok(null, "Password changed. Please log in");
    }
}
