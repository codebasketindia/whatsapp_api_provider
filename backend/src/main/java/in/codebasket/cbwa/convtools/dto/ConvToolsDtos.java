package in.codebasket.cbwa.convtools.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ConvToolsDtos {

    private ConvToolsDtos() {
    }

    public record QrDto(Long id, String code, String prefilledMessage, String deepLinkUrl, String shortLink, String pngUrl,
                        String svgUrl, Instant createdAt) {
    }

    public record QrRequest(@NotBlank @Size(max = 140, message = "Message can be at most 140 characters") String prefilledMessage) {
    }

    public record CommandDto(@NotBlank @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use letters, digits, - and _") String name,
                             @NotBlank @Size(max = 256) String description) {
    }

    public record ConversationalDto(boolean enableWelcomeMessage,
                                    @NotNull @Size(max = 4, message = "At most 4 ice breakers") List<@NotBlank @Size(max = 80) String> prompts,
                                    @NotNull @Size(max = 30, message = "At most 30 commands") List<@Valid CommandDto> commands) {
    }

    public record BlockedUserDto(String waId, String status, Instant blockedAt) {
    }

    public record WaIdsRequest(@NotEmpty @Size(max = 100) List<@Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Invalid number") String> waIds) {
    }

    public record FailedDto(String waId, String reason) {
    }

    public record BlockResult(List<String> blocked, List<String> unblocked, List<FailedDto> failed) {
    }

    public record OptOutSettingsDto(@NotNull @Size(max = 20) List<@NotBlank @Size(max = 32) String> optOutKeywords,
                                    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 32) String> optInKeywords,
                                    @Size(max = 1024) String autoReply) {
    }
}
