package in.codebasket.cbwa.publicapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ApiKeyDtos {

    private ApiKeyDtos() {
    }

    /** ApiKeyDto from the contract; apiSecret is filled only in create/regenerate responses. */
    public record ApiKeyDto(Long id, String name, String apiKey, String secretLast4, List<String> ipWhitelist,
                            Integer rateLimitPerMinute, String status, Instant lastUsedAt, String lastUsedIp,
                            Instant createdAt, String apiSecret) {
    }

    public record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name, @Size(max = 20) List<String> ipWhitelist) {
    }

    public record UpdateApiKeyRequest(@Size(max = 100) String name, @Size(max = 20) List<String> ipWhitelist,
                                      @Pattern(regexp = "ACTIVE|DISABLED", message = "Status must be ACTIVE or DISABLED") String status) {
    }

    public record RegenerateRequest(@NotBlank String password) {
    }
}
