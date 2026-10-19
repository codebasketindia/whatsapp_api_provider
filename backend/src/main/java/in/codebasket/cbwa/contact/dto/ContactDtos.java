package in.codebasket.cbwa.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ContactDtos {

    private ContactDtos() {
    }

    public record ContactRequest(@NotBlank @Size(max = 20) String phone,
                                 @Size(max = 120) String name,
                                 @Email @Size(max = 190) String email,
                                 @Size(max = 30) List<@Size(max = 50) String> tags,
                                 @Size(max = 50) Map<String, @Size(max = 500) String> attributes,
                                 List<Long> groupIds) {
    }

    public record GroupRef(Long id, String name) {
    }

    public record ContactDto(Long id, String phone, String name, String email, String countryCode, List<String> tags,
                             Map<String, String> attributes, boolean optedOut, boolean isBlocked, Instant lastInboundAt,
                             String source, List<GroupRef> groups, Instant createdAt) {
    }

    public record IdsRequest(@NotEmpty @Size(max = 1000) List<Long> ids) {
    }

    public record ContactIdsRequest(@NotEmpty @Size(max = 5000) List<Long> contactIds) {
    }

    public record OptOutRequest(@Size(max = 255) String reason) {
    }

    public record OptInRequest(Boolean confirmConsent) {
    }

    public record OptOutDto(Long id, String phone, String source, String keyword, Instant createdAt) {
    }

    public record TagCount(String tag, long count) {
    }

    public record GroupRequest(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description) {
    }

    public record GroupDto(Long id, String name, String description, int memberCount, Instant createdAt) {
    }

    public record ImportPreview(List<String> columns, List<List<String>> sampleRows, int rowCount) {
    }

    /** C5 mapping: column names of the uploaded file. attributes maps column -> attribute key. */
    public record ImportMapping(String phone, String name, String email, String tags, Map<String, String> attributes) {
    }

    public record ImportResult(int total, int created, int updated, int invalid, int duplicates, String reportFileUid) {
    }
}
