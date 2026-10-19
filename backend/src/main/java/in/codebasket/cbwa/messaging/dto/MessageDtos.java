package in.codebasket.cbwa.messaging.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MessageDtos {

    private MessageDtos() {
    }

    public record SenderRef(Long id, String displayPhoneNumber) {
    }

    public record ContactRef(Long id, String name) {
    }

    public record TemplateInfo(String name, String language) {
    }

    public record CampaignRef(String uid, String name) {
    }

    public record PartnerRef(Long id, String businessName) {
    }

    public record MediaInfo(String url, String mimeType, String filename, String caption, String downloadStatus) {
    }

    public record StatusHistoryItem(String status, Instant at, Integer errorCode, String errorTitle) {
    }

    /** Contract §3 MessageDto. `to` is set for outbound, `from` for inbound; `waId` always. */
    public record MessageDto(String uid, String direction, SenderRef sender, String waId, String to, String from,
                             ContactRef contact, String type, String category, TemplateInfo template, String preview,
                             JsonNode payload, String contextWamid, String wamid, String status, Integer errorCode,
                             String errorMessage, BigDecimal price, String chargeStatus, Boolean billable,
                             String pricingCategory, String source, String clientReference, CampaignRef campaign,
                             PartnerRef partner, MediaInfo media, Instant createdAt, Instant sentAt, Instant deliveredAt,
                             Instant readAt, Instant failedAt, List<StatusHistoryItem> statusHistory) {
    }

    /** MS1: either a list of recipients or count + countryCode. */
    public record EstimateRequest(@NotNull Long senderId, @Size(max = 100000) List<String> recipients,
                                  @Min(1) @Max(1000000) Integer count, @Size(max = 2) String countryCode,
                                  @NotBlank String type, Long templateId, String templateName, String templateLanguage) {
    }

    public record CountryBreakdown(String countryCode, long count, BigDecimal unitPrice, BigDecimal subtotal) {
    }

    public record CostEstimateDto(String category, List<CountryBreakdown> countryBreakdown, long recipients, long invalid,
                                  BigDecimal total, BigDecimal available, BigDecimal balanceAfter, boolean sufficient) {
    }

    public record MediaUploadDto(String fileUid, String metaMediaId, String mimeType, long size) {
    }
}
