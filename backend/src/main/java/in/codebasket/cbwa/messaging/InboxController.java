package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.messaging.InboxService.ConversationDto;
import in.codebasket.cbwa.messaging.InboxService.ConversationMessages;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MessageDto;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "Partner - Inbox")
@RestController
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;
    private final InboxStreamService streamService;

    public record ReadRequest(Boolean typing) {
    }

    @GetMapping("/api/partner/inbox/conversations")
    public ApiResponse<PageResponse<ConversationDto>> conversations(@RequestParam(required = false) Long senderId,
                                                                    @RequestParam(required = false) String search,
                                                                    @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(inboxService.conversations(SecurityUtils.currentPartnerId(), senderId, search, unreadOnly, page, size));
    }

    @GetMapping("/api/partner/inbox/conversations/{senderId}/{waId}/messages")
    public ApiResponse<ConversationMessages> messages(@PathVariable Long senderId, @PathVariable String waId,
                                                      @RequestParam(required = false) String beforeUid,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(inboxService.messages(SecurityUtils.currentPartnerId(), senderId, waId, beforeUid, size));
    }

    @PostMapping("/api/partner/inbox/conversations/{senderId}/{waId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageDto> reply(@PathVariable Long senderId, @PathVariable String waId,
                                         @Valid @RequestBody SendMessageRequest req) {
        return ApiResponse.created(inboxService.reply(SecurityUtils.currentPartnerId(), senderId, waId, req), "Reply queued");
    }

    @PostMapping("/api/partner/inbox/conversations/{senderId}/{waId}/read")
    public ApiResponse<Void> read(@PathVariable Long senderId, @PathVariable String waId,
                                  @RequestBody(required = false) ReadRequest req) {
        inboxService.markRead(SecurityUtils.currentPartnerId(), senderId, waId, req != null && Boolean.TRUE.equals(req.typing()));
        return ApiResponse.ok(null);
    }

    /** IN6: short-lived ticket for the SSE stream. */
    @PostMapping("/api/partner/inbox/stream-ticket")
    public ApiResponse<Map<String, Object>> ticket() {
        return ApiResponse.ok(streamService.createTicket(SecurityUtils.currentPartnerId()));
    }

    /**
     * IN5: live stream authenticated by the ticket only (EventSource cannot send headers), so it is
     * served under the public /api/public/** path instead of /api/partner/inbox/stream.
     */
    @GetMapping(value = "/api/public/inbox-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket) {
        return streamService.open(ticket);
    }
}
