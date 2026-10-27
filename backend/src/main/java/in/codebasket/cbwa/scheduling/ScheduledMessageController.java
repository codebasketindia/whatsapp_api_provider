package in.codebasket.cbwa.scheduling;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.scheduling.ScheduledMessageService.RescheduleRequest;
import in.codebasket.cbwa.scheduling.ScheduledMessageService.ScheduleRequest;
import in.codebasket.cbwa.scheduling.ScheduledMessageService.ScheduledMessageDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Partner - Scheduled messages")
@RestController
@RequestMapping("/api/partner/scheduled-messages")
@RequiredArgsConstructor
public class ScheduledMessageController {

    private final ScheduledMessageService service;

    @GetMapping
    public ApiResponse<PageResponse<ScheduledMessageDto>> list(@RequestParam(required = false) ScheduledStatus status,
                                                               @RequestParam(required = false) Long senderId,
                                                               @RequestParam(required = false) String waId,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(SecurityUtils.currentPartnerId(), status, senderId, waId, from, to, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduledMessageDto> create(@Valid @RequestBody ScheduleRequest req) {
        return ApiResponse.created(service.create(SecurityUtils.currentPartnerId(), req), "Message scheduled");
    }

    @GetMapping("/{uid}")
    public ApiResponse<ScheduledMessageDto> get(@PathVariable String uid) {
        return ApiResponse.ok(service.get(SecurityUtils.currentPartnerId(), uid));
    }

    @PutMapping("/{uid}")
    public ApiResponse<ScheduledMessageDto> update(@PathVariable String uid, @Valid @RequestBody ScheduleRequest req) {
        return ApiResponse.ok(service.update(SecurityUtils.currentPartnerId(), uid, req), "Scheduled message saved");
    }

    @PostMapping("/{uid}/reschedule")
    public ApiResponse<ScheduledMessageDto> reschedule(@PathVariable String uid, @Valid @RequestBody RescheduleRequest req) {
        return ApiResponse.ok(service.reschedule(SecurityUtils.currentPartnerId(), uid, req.scheduledAt()), "Time changed");
    }

    @PostMapping("/{uid}/cancel")
    public ApiResponse<ScheduledMessageDto> cancel(@PathVariable String uid) {
        return ApiResponse.ok(service.cancel(SecurityUtils.currentPartnerId(), uid), "Scheduled message cancelled");
    }

    @PostMapping("/{uid}/send-now")
    public ApiResponse<ScheduledMessageDto> sendNow(@PathVariable String uid) {
        ScheduledMessageDto dto = service.sendNow(SecurityUtils.currentPartnerId(), uid);
        return ApiResponse.ok(dto, dto.status() == ScheduledStatus.SENT ? "Message sent" : "Message could not be sent");
    }
}
