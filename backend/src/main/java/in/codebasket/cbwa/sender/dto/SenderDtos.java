package in.codebasket.cbwa.sender.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SenderDtos {

    private SenderDtos() {
    }

    public record HealthError(String entityType, Integer code, String description, String solution) {
    }

    public record Health(String canSendMessage, List<HealthError> errors) {
    }

    public record WabaRef(Long id, String wabaId, String name, String tokenStatus) {
    }

    public record PartnerRef(Long id, String businessName) {
    }

    public record SenderDto(Long id, String phoneNumberId, String displayPhoneNumber, String verifiedName, String nameStatus,
                            String newNameStatus, String qualityRating, String messagingLimitTier, String throughputLevel,
                            int maxMps, String metaStatus, Health health, String senderType, boolean isCoexistence,
                            boolean isRegistered, boolean isPaused, String pauseReason, Integer dailySendCap, long sentToday,
                            String status, WabaRef waba, PartnerRef partner, boolean isDefault, Instant connectedAt,
                            Instant lastSyncedAt) {
    }

    public record ChecklistItem(String key, String status, String title, String help, String actionUrl) {
    }

    public record ChecklistDto(List<ChecklistItem> items) {
    }

    public record SenderDetailDto(SenderDto sender, ChecklistDto checklist) {
    }

    public record StepDto(String step, String status, String message) {
    }

    public record ConnectResultDto(SenderDto sender, ChecklistDto checklist, List<StepDto> steps) {
    }

    public record UpdateSenderRequest(@Min(1) @Max(10_000_000) Integer dailySendCap, @Min(1) @Max(1000) Integer maxMps,
                                      Boolean isPaused, @Size(max = 255) String pauseReason, Boolean clearDailyCap) {
    }

    public record AssignRequest(@NotNull Long partnerId, boolean isDefault) {
    }

    public record ReasonRequest(@Size(max = 255) String reason) {
    }

    public record ConfirmRequest(Boolean confirm) {
    }

    public record EmbeddedSignupConfigDto(boolean allowed, String appId, String configId, String graphApiVersion,
                                          String solutionId, List<String> featureTypes, Map<String, Object> extras) {
    }

    public record CompleteSignupRequest(@NotBlank @Size(max = 1024) String code,
                                        @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String wabaId,
                                        @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String phoneNumberId,
                                        @Pattern(regexp = "^$|^[0-9]{5,32}$") String businessId,
                                        @Pattern(regexp = "NEW|EXISTING|COEXISTENCE") String mode,
                                        @Pattern(regexp = "^$|^[0-9]{6}$", message = "PIN must be 6 digits") String pin) {
    }

    public record SignupEventRequest(@NotBlank @Pattern(regexp = "CANCEL|ERROR") String event, @Size(max = 120) String currentStep,
                                     @Size(max = 1000) String errorMessage, @Size(max = 64) String errorCode,
                                     @Size(max = 128) String sessionId) {
    }

    public record ManualConnectRequest(Long partnerId, @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String wabaId,
                                       @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String phoneNumberId,
                                       @NotBlank @Size(max = 1024) String accessToken,
                                       @Pattern(regexp = "^$|^[0-9]{6}$", message = "PIN must be 6 digits") String pin,
                                       boolean register) {
    }

    public record RequestCodeRequest(@NotBlank @Pattern(regexp = "SMS|VOICE") String method,
                                     @Pattern(regexp = "^$|^[a-z]{2}(_[A-Z]{2})?$") String language) {
    }

    public record VerifyCodeRequest(@NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Enter the 6 digit code") String code) {
    }

    public record PinRequest(@NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "PIN must be 6 digits") String pin) {
    }

    public record BusinessProfileDto(@Size(max = 139) String about, @Size(max = 512) String description,
                                     @Size(max = 256) String address, @Size(max = 128) String email,
                                     @Size(max = 2, message = "At most 2 websites") List<@Size(max = 256) String> websites,
                                     @Size(max = 32) String vertical, String profilePictureUrl) {
    }

    public record HealthDto(String quality, String tier, String throughput, Health health, String nameStatus,
                            String tokenStatus, List<String> alerts) {
    }

    public record MetaAnalyticsRow(String date, String category, String pricingType, String country, long volume, BigDecimal cost) {
    }

    public record OurAnalyticsRow(String date, long sent, long delivered, long read, long failed, BigDecimal charged) {
    }

    public record AnalyticsDto(List<MetaAnalyticsRow> meta, List<OurAnalyticsRow> ours) {
    }
}
