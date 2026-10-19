package in.codebasket.cbwa.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RatePlanDtos {

    private RatePlanDtos() {
    }

    public record ItemDto(@NotBlank @Pattern(regexp = "^([A-Z]{2}|\\*)$", message = "Use a 2-letter country code or *") String countryCode,
                          @NotBlank @Pattern(regexp = "MARKETING|UTILITY|AUTHENTICATION|AUTHENTICATION_INTERNATIONAL",
                                  message = "Unknown category") String category,
                          @NotNull @DecimalMin("0.0000") @DecimalMax("100000") @Digits(integer = 8, fraction = 4) BigDecimal price) {
    }

    public record RatePlanDto(Long id, String name, String description, boolean isDefault, boolean isCustom, Long partnerId,
                              String partnerName, BigDecimal sessionMessageFee, BigDecimal platformFeePerMessage,
                              BigDecimal platformFeeMonthlyPerNumber, String status, List<ItemDto> items, long partnerCount,
                              Instant createdAt, Instant updatedAt) {
    }

    public record RatePlanRequest(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description, boolean isDefault,
                                  Long partnerId,
                                  @NotNull @DecimalMin("0.0000") @Digits(integer = 8, fraction = 4) BigDecimal sessionMessageFee,
                                  @NotNull @DecimalMin("0.0000") @Digits(integer = 8, fraction = 4) BigDecimal platformFeePerMessage,
                                  @NotNull @DecimalMin("0.0000") @Digits(integer = 8, fraction = 4) BigDecimal platformFeeMonthlyPerNumber,
                                  @NotNull @Size(max = 2000) List<@Valid ItemDto> items) {
    }

    public record CloneRequest(@NotBlank @Size(max = 100) String name, Long partnerId) {
    }

    public record StatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|ARCHIVED") String status) {
    }
}
