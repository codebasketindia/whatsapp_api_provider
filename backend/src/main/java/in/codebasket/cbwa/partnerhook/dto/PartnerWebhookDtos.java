package in.codebasket.cbwa.partnerhook.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PartnerWebhookDtos {

    private PartnerWebhookDtos() {
    }

    /** D6/D7 response. `secret` is filled only when the webhook is first created. */
    public record WebhookConfigDto(String url, List<String> events, boolean isActive, boolean secretSet,
                                   Instant lastSuccessAt, int consecutiveFailures, String secret) {
    }

    public record WebhookConfigRequest(@NotBlank @Size(max = 500) String url, @NotNull List<String> events, Boolean isActive) {
    }

    public record SecretDto(String secret) {
    }

    public record TestRequest(String eventType) {
    }

    public record TestResultDto(String status, Integer responseCode, Integer durationMs, String responseBody) {
    }

    public record DeliveryLogDto(Long id, String eventId, String eventType, JsonNode payload, int attempt, String status,
                                 Integer responseCode, String responseBody, Integer durationMs, Instant nextRetryAt,
                                 Instant createdAt, Instant updatedAt) {
    }

    public record DeveloperInfoDto(String baseUrl, int rateLimitPerMinute, String swaggerUrl, String postmanUrl) {
    }
}
