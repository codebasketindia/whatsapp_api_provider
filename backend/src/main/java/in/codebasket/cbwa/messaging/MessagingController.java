package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.messaging.dto.MessageDtos.CostEstimateDto;
import in.codebasket.cbwa.messaging.dto.MessageDtos.EstimateRequest;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MediaUploadDto;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MessageDto;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Tag(name = "Partner - Messages")
@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class MessagingController {

    private final PartnerMessagingService messagingService;
    private final MessageQueryService queryService;

    @PostMapping("/messages/estimate")
    public ApiResponse<CostEstimateDto> estimate(@Valid @RequestBody EstimateRequest req) {
        return ApiResponse.ok(messagingService.estimate(SecurityUtils.currentPartnerId(), req));
    }

    @PostMapping("/messages/send")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageDto> send(@Valid @RequestBody SendMessageRequest req) {
        return ApiResponse.created(messagingService.send(SecurityUtils.currentPartnerId(), req, MessageSource.DASHBOARD),
                "Message queued for sending");
    }

    @GetMapping("/messages")
    public ApiResponse<PageResponse<MessageDto>> list(@RequestParam(required = false) String direction,
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
        var f = new MessageQueryService.Filter(SecurityUtils.currentPartnerId(), direction, status, senderId, category, type,
                campaignUid, search, from, to);
        return ApiResponse.ok(queryService.list(f, page, size, sort, false));
    }

    @GetMapping("/messages/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String direction,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long senderId,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String campaignUid,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "csv") String format) {
        var f = new MessageQueryService.Filter(SecurityUtils.currentPartnerId(), direction, status, senderId, category, type,
                campaignUid, search, from, to);
        return file(queryService.export(f, format, false), format, "messages");
    }

    @GetMapping("/messages/{uid}")
    public ApiResponse<MessageDto> detail(@PathVariable String uid) {
        return ApiResponse.ok(queryService.detail(uid, SecurityUtils.currentPartnerId(), false));
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaUploadDto> upload(@RequestParam("file") MultipartFile file, @RequestParam("senderId") Long senderId) {
        return ApiResponse.ok(messagingService.uploadMedia(SecurityUtils.currentPartnerId(), senderId, file), "File uploaded");
    }

    static ResponseEntity<byte[]> file(byte[] bytes, String format, String name) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        String filename = name + "-" + LocalDate.now() + (xlsx ? ".xlsx" : ".csv");
        return ResponseEntity.ok()
                .contentType(xlsx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        : MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }
}
