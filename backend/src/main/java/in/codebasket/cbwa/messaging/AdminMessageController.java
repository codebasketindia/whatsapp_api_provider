package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MessageDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** ML1-ML3: message logs across all partners. */
@Tag(name = "Admin - Message logs")
@RestController
@RequestMapping("/api/admin/messages")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_MESSAGE_LOG_VIEW')")
public class AdminMessageController {

    private final MessageQueryService queryService;

    @GetMapping
    public ApiResponse<PageResponse<MessageDto>> list(@RequestParam(required = false) Long partnerId,
                                                      @RequestParam(required = false) String direction,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long senderId,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) String campaignUid,
                                                      @RequestParam(required = false) String search,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size,
                                                      @RequestParam(required = false) String sort) {
        var f = new MessageQueryService.Filter(partnerId, direction, status, senderId, category, type, campaignUid, search, from, to);
        return ApiResponse.ok(queryService.list(f, page, size, sort, true));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long partnerId,
                                         @RequestParam(required = false) String direction,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long senderId,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String campaignUid,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "csv") String format) {
        var f = new MessageQueryService.Filter(partnerId, direction, status, senderId, category, type, campaignUid, search, from, to);
        return MessagingController.file(queryService.export(f, format, true), format, "message-logs");
    }

    @GetMapping("/{uid}")
    public ApiResponse<MessageDto> detail(@PathVariable String uid) {
        return ApiResponse.ok(queryService.detail(uid, null, true));
    }
}
