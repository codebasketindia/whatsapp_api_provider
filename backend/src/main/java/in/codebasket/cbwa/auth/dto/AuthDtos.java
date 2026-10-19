package in.codebasket.cbwa.auth.dto;

import in.codebasket.cbwa.user.dto.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Size(max = 128) String password) {
    }

    public record TwoFactorRequest(@NotBlank String challengeId, @NotBlank @Pattern(regexp = "\\d{6}", message = "Enter the 6 digit code") String otp) {
    }

    public record ChallengeRequest(@NotBlank String challengeId) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String challengeId,
                                       @NotBlank @Pattern(regexp = "\\d{6}", message = "Enter the 6 digit code") String otp,
                                       @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record TokenDto(String accessToken, String refreshToken, int accessTokenExpiresIn, long refreshTokenExpiresIn,
                           UserDto user) {
    }

    public record TwoFactorChallenge(boolean twoFactorRequired, String challengeId, String maskedEmail) {
    }

    public record ChallengeDto(String challengeId) {
    }

    public record ResendDto(int resendAfterSeconds) {
    }
}
