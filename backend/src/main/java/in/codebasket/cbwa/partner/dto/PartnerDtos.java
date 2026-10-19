package in.codebasket.cbwa.partner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
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

public final class PartnerDtos {

    private PartnerDtos() {
    }

    public static final String GSTIN_REGEX = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    public static final String PAN_REGEX = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$";
    public static final String MOBILE_REGEX = "^\\+?[0-9]{10,15}$";

    public record AddressDto(@Size(max = 190) String line1, @Size(max = 190) String line2, @Size(max = 100) String city,
                             @Size(max = 100) String state,
                             @Pattern(regexp = "^$|^[0-9]{2}$", message = "GST state code is 2 digits, e.g. 27") String stateCode,
                             @Pattern(regexp = "^$|^[0-9A-Za-z -]{3,10}$", message = "Enter a valid PIN code") String pincode,
                             @Pattern(regexp = "^$|^[A-Z]{2}$", message = "Use a 2-letter country code") String country) {
    }

    public record RatePlanRef(Long id, String name) {
    }

    public record WalletRef(BigDecimal balance, BigDecimal held) {
    }

    public record PartnerDto(Long id, String uid, String partnerCode, String businessName, String contactName, String email,
                             String mobile, String gstin, String pan, AddressDto address, String website, String status,
                             String suspendReason, boolean kycVerified, boolean allowOwnNumber, RatePlanRef ratePlan,
                             WalletRef wallet, Instant createdAt) {
    }

    public record PartnerSettingsDto(BigDecimal lowBalanceThreshold, BigDecimal effectiveLowBalanceThreshold,
                                     List<String> optOutKeywords, Long defaultSenderId, boolean notifyEmail) {
    }

    public record PartnerStatsDto(long senders, long messagesToday, long messagesMonth, BigDecimal spendMonth) {
    }

    /** P3: PartnerDto fields flattened plus settings and stats. */
    public record PartnerDetailDto(Long id, String uid, String partnerCode, String businessName, String contactName,
                                   String email, String mobile, String gstin, String pan, AddressDto address, String website,
                                   String status, String suspendReason, boolean kycVerified, boolean allowOwnNumber,
                                   RatePlanRef ratePlan, WalletRef wallet, Instant createdAt, PartnerSettingsDto settings,
                                   PartnerStatsDto stats) {

        public static PartnerDetailDto of(PartnerDto p, PartnerSettingsDto settings, PartnerStatsDto stats) {
            return new PartnerDetailDto(p.id(), p.uid(), p.partnerCode(), p.businessName(), p.contactName(), p.email(),
                    p.mobile(), p.gstin(), p.pan(), p.address(), p.website(), p.status(), p.suspendReason(), p.kycVerified(),
                    p.allowOwnNumber(), p.ratePlan(), p.wallet(), p.createdAt(), settings, stats);
        }
    }

    public record CreatePartnerRequest(
            @NotBlank @Size(max = 190) String businessName,
            @NotBlank @Size(max = 120) String contactName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = MOBILE_REGEX, message = "Enter a valid mobile number") String mobile,
            @Pattern(regexp = GSTIN_REGEX, message = "Enter a valid 15 character GSTIN") String gstin,
            @Pattern(regexp = PAN_REGEX, message = "Enter a valid PAN") String pan,
            @Valid AddressDto address,
            @Size(max = 255) String website,
            Long ratePlanId,
            boolean allowOwnNumber,
            @DecimalMin(value = "0", message = "Must be zero or more") BigDecimal lowBalanceThreshold,
            @Size(min = 8, max = 72) String password,
            Boolean sendWelcomeEmail) {
    }

    public record UpdatePartnerRequest(
            @NotBlank @Size(max = 190) String businessName,
            @NotBlank @Size(max = 120) String contactName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = MOBILE_REGEX, message = "Enter a valid mobile number") String mobile,
            @Pattern(regexp = GSTIN_REGEX, message = "Enter a valid 15 character GSTIN") String gstin,
            @Pattern(regexp = PAN_REGEX, message = "Enter a valid PAN") String pan,
            @Valid AddressDto address,
            @Size(max = 255) String website,
            Long ratePlanId,
            boolean allowOwnNumber,
            @DecimalMin(value = "0", message = "Must be zero or more") BigDecimal lowBalanceThreshold) {
    }

    public record StatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status, @Size(max = 255) String reason) {
    }

    public record RatePlanAssignRequest(@NotNull Long ratePlanId) {
    }

    public record OwnNumberRequest(@NotNull Boolean allowed) {
    }

    public record KycRequest(@NotNull Boolean kycVerified) {
    }

    public record LoginAsRequest(@NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record SenderAssignRequest(@NotNull Long phoneNumberDbId, boolean isDefault) {
    }

    public record UsageRowDto(String key, String label, long total, long sent, long delivered, long read, long failed,
                              BigDecimal charged) {
    }

    /** Admin view of a partner API key - never contains the secret. */
    public record ApiKeyDto(Long id, String name, String apiKey, String secretLast4, List<String> ipWhitelist,
                            Integer rateLimitPerMinute, String status, Instant lastUsedAt, String lastUsedIp, Instant createdAt) {
    }

    public record ApiKeyPatchRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status,
                                     @Min(1) @Max(100000) Integer rateLimitPerMinute, Boolean clearRateLimit) {
    }

    /** SenderDto (contract §3) as returned by P11/P12. */
    public record SenderDto(Long id, String phoneNumberId, String displayPhoneNumber, String verifiedName, String nameStatus,
                            String newNameStatus, String qualityRating, String messagingLimitTier, String throughputLevel,
                            int maxMps, String metaStatus, Map<String, Object> health, String senderType,
                            boolean isCoexistence, boolean isRegistered, boolean isPaused, String pauseReason,
                            Integer dailySendCap, long sentToday, String status, WabaRef waba, PartnerRef partner,
                            boolean isDefault, Instant connectedAt, Instant lastSyncedAt) {
    }

    public record WabaRef(Long id, String wabaId, String name, String tokenStatus) {
    }

    public record PartnerRef(Long id, String businessName) {
    }

    /** R7: the partner's own rate plan. */
    public record PartnerRatePlanDto(Long id, String name, String description, String currency, BigDecimal sessionMessageFee,
                                     BigDecimal platformFeePerMessage, BigDecimal platformFeeMonthlyPerNumber,
                                     List<RateItemDto> items) {
    }

    public record RateItemDto(String countryCode, String category, BigDecimal price) {
    }
}
