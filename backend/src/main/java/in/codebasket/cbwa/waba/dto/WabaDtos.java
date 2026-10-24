package in.codebasket.cbwa.waba.dto;

import in.codebasket.cbwa.sender.dto.SenderDtos.ChecklistDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WabaDtos {

    private WabaDtos() {
    }

    public record PartnerRef(Long id, String businessName) {
    }

    public record WabaDto(Long id, String wabaId, String ownerType, PartnerRef partner, String name, String businessId,
                          String accountReviewStatus, String businessVerificationStatus, Boolean paymentMethodAttached,
                          String tokenStatus, Instant tokenCheckedAt, boolean webhookSubscribed, String onboardingType,
                          String billingMode, boolean creditLineShared, String status, Instant connectedAt) {
    }

    public record WabaDetailDto(WabaDto waba, List<SenderDto> senders, ChecklistDto checklist) {
    }

    public record AddWabaRequest(@NotBlank @Pattern(regexp = "^[0-9]{5,32}$", message = "WABA ID must be digits") String wabaId,
                                 @NotBlank @Size(max = 1024) @Pattern(regexp = TOKEN_REGEX, message = TOKEN_MESSAGE) String accessToken) {
    }

    public record TokenRequest(@NotBlank @Size(max = 1024) @Pattern(regexp = TOKEN_REGEX, message = TOKEN_MESSAGE) String accessToken) {
    }

    /** Meta access tokens are URL-safe; anything else would break the Authorization header. */
    public static final String TOKEN_REGEX = "^[A-Za-z0-9_\\-|.]+$";
    public static final String TOKEN_MESSAGE = "This is not a valid access token";

    public record TokenTestDto(boolean tokenValid, List<String> scopes, Instant expiresAt, boolean subscribed) {
    }

    public record HealthOverviewDto(List<Map<String, Object>> wabas, List<Map<String, Object>> senders, List<String> alerts) {
    }
}
