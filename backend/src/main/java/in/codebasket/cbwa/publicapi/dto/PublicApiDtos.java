package in.codebasket.cbwa.publicapi.dto;

import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Request/response shapes of the Partner Public API (contract §4.16). */
public final class PublicApiDtos {

    private PublicApiDtos() {
    }

    public record SendResult(String id, String status, BigDecimal price, String to, String clientReference) {
    }

    public record BulkRecipient(@NotBlank @Size(max = 20) String to, Map<String, String> variables,
                                List<String> bodyVariables, @Size(max = 100) String clientReference) {
    }

    public record BulkRequest(Long senderId, String from, @Valid @jakarta.validation.constraints.NotNull SendMessageRequest.TemplateRef template,
                              @NotEmpty @Size(max = 1000) List<@Valid BulkRecipient> recipients) {
    }

    public record Accepted(String to, String id, BigDecimal price, String clientReference) {
    }

    public record Rejected(String to, String code, String message, String clientReference) {
    }

    public record BulkResult(List<Accepted> accepted, List<Rejected> rejected, BigDecimal totalCost) {
    }

    public record PublicSender(Long id, String phoneNumber, String displayPhoneNumber, String verifiedName, String quality,
                               String status, boolean paused, boolean isDefault) {
    }

    public record Balance(BigDecimal balance, BigDecimal held, String currency) {
    }
}
