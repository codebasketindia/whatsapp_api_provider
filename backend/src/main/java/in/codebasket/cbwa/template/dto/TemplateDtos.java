package in.codebasket.cbwa.template.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TemplateDtos {

    private TemplateDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ButtonDto(String type, String text, String url, String phoneNumber, JsonNode example, String otpType,
                            String packageName, String signatureHash, String autofillText) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ComponentDto(String type, String format, String text, JsonNode example, List<@Valid ButtonDto> buttons,
                               String headerMediaFileUid, String headerHandle, Boolean addSecurityRecommendation,
                               Integer codeExpirationMinutes) {
    }

    public record WabaRef(Long id, String wabaId, String name) {
    }

    public record PartnerRef(Long id, String businessName) {
    }

    public record TemplateDto(Long id, String uid, String name, String language, String category, String previousCategory,
                              String parameterFormat, String status, String rejectedReason, String qualityScore, WabaRef waba,
                              PartnerRef partner, String libraryTemplateName, List<ComponentDto> components,
                              String adminReviewNote, String metaTemplateId, Instant submittedAt, Instant lastSyncedAt,
                              Instant createdAt, Instant updatedAt) {
    }

    public record TemplateRequest(Long wabaDbId, Long senderId, @NotBlank @Size(max = 512) String name,
                                  @NotBlank @Size(max = 10) String language, @NotBlank String category, String parameterFormat,
                                  @NotEmpty @Size(max = 4) List<@Valid ComponentDto> components, Boolean allowCategoryChange,
                                  boolean submit, Long partnerId) {
    }

    public record ReviewRequest(@Size(max = 500) String note, @Size(max = 500) String reason) {
    }

    public record SyncRequest(Long wabaDbId, Long senderId) {
    }

    public record SyncResult(int created, int updated, int deleted) {
    }

    public record DailyMetric(String date, long sent, long delivered, long read, long clicked) {
    }

    public record ButtonMetric(String button, String type, long count) {
    }

    public record Totals(long sent, long delivered, long read, long clicked) {
    }

    public record AnalyticsDto(Totals totals, List<DailyMetric> daily, List<ButtonMetric> buttons, String source) {
    }

    public record LibraryItemDto(String name, String category, String topic, String usecase, String industry, String language,
                                 String header, String body, List<Map<String, Object>> buttons, JsonNode bodyParams) {
    }

    public record LibraryUseRequest(Long wabaDbId, Long senderId, @NotBlank @Size(max = 128) String libraryTemplateName,
                                    @NotBlank @Size(max = 512) String name, @NotBlank @Size(max = 10) String language,
                                    String category, List<Map<String, Object>> buttonInputs) {
    }

    public record HeaderUploadDto(String fileUid, String headerHandle, String mimeType) {
    }

    public record CategoryRequest(@NotNull String category) {
    }
}
