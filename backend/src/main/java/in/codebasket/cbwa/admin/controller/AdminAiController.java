package in.codebasket.cbwa.admin.controller;

import in.codebasket.cbwa.common.constants.AiOutcome;
import in.codebasket.cbwa.common.service.ai.AiService;

import in.codebasket.cbwa.common.service.ai.AiService.AssistantDto;
import in.codebasket.cbwa.common.service.ai.AiService.AssistantRequest;
import in.codebasket.cbwa.common.service.ai.AiService.KnowledgeDto;
import in.codebasket.cbwa.common.service.ai.AiService.KnowledgeRequest;
import in.codebasket.cbwa.common.service.ai.AiService.MediaDto;
import in.codebasket.cbwa.common.service.ai.AiService.TestRequest;
import in.codebasket.cbwa.common.service.ai.AiService.TestResult;
import in.codebasket.cbwa.common.service.ai.AiService.UsageReport;
import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.common.dto.ApiResponse;
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

@Tag(name = "Admin - AI assistant")
@RestController
@RequiredArgsConstructor
public class AdminAiController {

    private final AiService service;

    public record PauseRequest(@NotNull Boolean paused) {
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
