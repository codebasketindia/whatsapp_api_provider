package in.codebasket.cbwa.ai;

import in.codebasket.cbwa.ai.AiService.AssistantDto;
import in.codebasket.cbwa.ai.AiService.AssistantRequest;
import in.codebasket.cbwa.ai.AiService.KnowledgeDto;
import in.codebasket.cbwa.ai.AiService.KnowledgeRequest;
import in.codebasket.cbwa.ai.AiService.MediaDto;
import in.codebasket.cbwa.ai.AiService.TestRequest;
import in.codebasket.cbwa.ai.AiService.TestResult;
import in.codebasket.cbwa.ai.AiService.UsageReport;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Partner - AI assistant")
@RestController
@RequiredArgsConstructor
public class AiController {

    private final AiService service;

    public record PauseRequest(@NotNull Boolean paused) {
    }

    @GetMapping("/api/partner/ai/assistant")
    public ApiResponse<AssistantDto> get() {
        return ApiResponse.ok(service.get(SecurityUtils.currentPartnerId()));
    }

    @PutMapping("/api/partner/ai/assistant")
    public ApiResponse<AssistantDto> update(@Valid @RequestBody AssistantRequest req) {
        return ApiResponse.ok(service.update(SecurityUtils.currentPartnerId(), req), "AI assistant saved");
    }

    @GetMapping("/api/partner/ai/knowledge")
    public ApiResponse<List<KnowledgeDto>> knowledge(@RequestParam(required = false) String search) {
        return ApiResponse.ok(service.knowledge(SecurityUtils.currentPartnerId(), search));
    }

    @PostMapping("/api/partner/ai/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KnowledgeDto> create(@Valid @RequestBody KnowledgeRequest req) {
        return ApiResponse.created(service.createKnowledge(SecurityUtils.currentPartnerId(), req), "Knowledge added");
    }

    @PutMapping("/api/partner/ai/knowledge/{id}")
    public ApiResponse<KnowledgeDto> update(@PathVariable Long id, @Valid @RequestBody KnowledgeRequest req) {
        return ApiResponse.ok(service.updateKnowledge(SecurityUtils.currentPartnerId(), id, req), "Knowledge saved");
    }

    @DeleteMapping("/api/partner/ai/knowledge/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteKnowledge(SecurityUtils.currentPartnerId(), id);
        return ApiResponse.ok(null, "Knowledge deleted");
    }

    @PostMapping("/api/partner/ai/knowledge/media")
    public ApiResponse<MediaDto> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.uploadMedia(SecurityUtils.currentPartnerId(), file), "File uploaded");
    }

    @PostMapping("/api/partner/ai/test")
    public ApiResponse<TestResult> test(@Valid @RequestBody TestRequest req) {
        return ApiResponse.ok(service.test(SecurityUtils.currentPartnerId(), req));
    }

    @GetMapping("/api/partner/ai/usage")
    public ApiResponse<UsageReport> usage(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) AiOutcome outcome,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.usage(SecurityUtils.currentPartnerId(), from, to, outcome, page, size, false));
    }

    @PatchMapping("/api/partner/inbox/conversations/{senderId}/{waId}/ai")
    public ApiResponse<Map<String, Object>> pause(@PathVariable Long senderId, @PathVariable String waId,
                                                  @Valid @RequestBody PauseRequest req) {
        return ApiResponse.ok(service.setChatPaused(SecurityUtils.currentPartnerId(), senderId, waId, req.paused()),
                req.paused() ? "AI replies paused for this chat" : "AI replies resumed for this chat");
    }

    @GetMapping("/api/admin/ai/usage")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ApiResponse<UsageReport> adminUsage(@RequestParam(required = false) Long partnerId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) AiOutcome outcome,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.usage(partnerId, from, to, outcome, page, size, true));
    }
}
