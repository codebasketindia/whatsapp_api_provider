package in.codebasket.cbwa.messaging.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Same body for dashboard send (MS2), inbox reply (IN3) and public API (PU1) - see contract §3.
 * Exactly one payload object matching {@code type} must be present.
 */
public record SendMessageRequest(
        Long senderId,
        /** Public API: sender phone number (digits) instead of senderId. */
        String from,
        @Size(max = 20) String to,
        @NotBlank @Pattern(regexp = "text|image|video|audio|document|sticker|location|contacts|reaction|interactive|template",
                message = "Unsupported message type") String type,
        String replyToUid,
        String replyToWamid,
        @Valid Text text,
        @Valid Media media,
        @Valid Location location,
        List<JsonNode> contacts,
        @Valid Reaction reaction,
        JsonNode interactive,
        @Valid TemplateRef template,
        @Size(max = 100) String clientReference) {

    public record Text(@NotBlank @Size(max = 4096) String body, Boolean previewUrl) {
    }

    /** One of fileUid (uploaded via MS6), metaMediaId or a public https link. */
    public record Media(String fileUid, String metaMediaId, @Size(max = 2000) String link,
                        @Size(max = 1024) String caption, @Size(max = 240) String filename) {
    }

    public record Location(Double latitude, Double longitude, @Size(max = 1000) String name, @Size(max = 1000) String address) {
    }

    public record Reaction(String messageUid, String messageWamid, @Size(max = 16) String emoji) {
    }

    public record TemplateRef(Long templateId, String name, String language, @Valid TemplateVariables variables,
                              Map<String, String> namedVariables) {
    }

    public record TemplateVariables(List<String> body, HeaderVariable header, List<ButtonVariable> buttons) {
    }

    /** Header parameter: text value, or media (fileUid / metaMediaId / link), or location. */
    public record HeaderVariable(@Pattern(regexp = "text|image|video|document|location") String type, String text,
                                 String fileUid, String metaMediaId, String link, String filename,
                                 Double latitude, Double longitude, String name, String address) {
    }

    /** Button parameter: url suffix, quick reply payload, copy_code or OTP code. */
    public record ButtonVariable(int index, @Pattern(regexp = "url|quick_reply|copy_code|otp|flow") String subType, String value) {
    }
}
