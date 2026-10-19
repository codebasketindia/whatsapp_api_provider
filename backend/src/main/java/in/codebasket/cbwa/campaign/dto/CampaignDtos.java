package in.codebasket.cbwa.campaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CampaignDtos {

    private CampaignDtos() {
    }

    public record UploadDto(String fileUid, List<String> columns, List<List<String>> sampleRows, int rowCount) {
    }

    public record AudienceRequest(@NotBlank @Pattern(regexp = "FILE|GROUPS|CONTACTS") String type, String fileUid,
                                  List<Long> groupIds, List<Long> contactIds) {
    }

    /**
     * Header media for templates with an IMAGE / VIDEO / DOCUMENT / LOCATION header:
     * type + one of fileUid / link / metaMediaId, or latitude/longitude for LOCATION.
     */
    public record HeaderMedia(@Pattern(regexp = "image|video|document|location") String type, String fileUid, String link,
                              String metaMediaId, String filename, Double latitude, Double longitude, String name,
                              String address) {
    }

    /**
     * phone: file column (FILE audience). name: optional file column saved as contact name.
     * variables: template variable key ("1", "2" or a named variable, "header", "button_0") -> source:
     * a file column / contact field name, "{{attribute}}" (contact field or attribute) or "=static text".
     */
    public record Mapping(String phone, String name, Map<String, String> variables, @Valid HeaderMedia headerMedia) {
    }

    public record CampaignRequest(@NotBlank @Size(max = 150) String name, @NotNull Long senderId, @NotNull Long templateId,
                                  @NotNull @Valid AudienceRequest audience, @Valid Mapping mapping,
                                  @Pattern(regexp = "^[A-Za-z]{2}$", message = "Use a 2 letter country code") String defaultCountry,
                                  Boolean saveContacts) {
    }

    public record LaunchRequest(Instant scheduleAt, Boolean confirmCost) {
    }

    public record ReasonRequest(@Size(max = 255) String reason) {
    }

    public record SenderRef(Long id, String displayPhoneNumber, String verifiedName) {
    }

    public record TemplateRef(Long id, String name, String language, String category, String status) {
    }

    public record PartnerRef(Long id, String businessName) {
    }

    public record Counts(int total, int valid, int invalid, int skipped, int queued, int sent, int delivered, int read, int failed) {
    }

    public record CampaignDto(Long id, String uid, String name, SenderRef sender, TemplateRef template, String audienceType,
                              Object audience, Object mapping, Object headerMedia, String defaultCountry, boolean saveContacts,
                              String status, String pauseReason, Instant scheduledAt, Instant startedAt, Instant completedAt,
                              Instant preparedAt, Counts counts, BigDecimal unitPrice, BigDecimal estimatedCost,
                              BigDecimal chargedAmount, BigDecimal refundedAmount, PartnerRef partner, Instant createdAt) {
    }

    public record CountryLine(String countryCode, long count, BigDecimal unitPrice, BigDecimal subtotal) {
    }

    /** Same shape as the contract's CostEstimateDto. */
    public record CostEstimateDto(String category, List<CountryLine> countryBreakdown, long recipients, BigDecimal total,
                                  BigDecimal available, BigDecimal balanceAfter, boolean sufficient) {
    }

    public record PrepareCounts(int total, int valid, int invalid, int optedOut, int blocked, int duplicates, int missingVariables) {
    }

    public record PreviewLine(String phone, String renderedText) {
    }

    public record PrepareResult(PrepareCounts counts, List<PreviewLine> preview, CostEstimateDto estimate) {
    }

    public record RecipientDto(Long id, int rowNo, String phone, String status, String skipReason, String errorMessage,
                               String messageUid, Instant updatedAt) {
    }
}
