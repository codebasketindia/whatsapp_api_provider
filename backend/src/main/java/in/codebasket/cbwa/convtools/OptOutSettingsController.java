package in.codebasket.cbwa.convtools;

import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.convtools.dto.ConvToolsDtos.OptOutSettingsDto;
import in.codebasket.cbwa.partner.PartnerSettings;
import in.codebasket.cbwa.partner.PartnerSettingsRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** C12: partner-editable opt-out / opt-in keywords and auto reply (F5). */
@Tag(name = "Partner - Opt-out settings")
@RestController
@RequestMapping("/api/partner/settings/opt-out")
@RequiredArgsConstructor
public class OptOutSettingsController {

    private final PartnerSettingsRepository repository;
    private final AuditService auditService;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<OptOutSettingsDto> get() {
        return ApiResponse.ok(toDto(settings()));
    }

    @PutMapping
    @Transactional
    public ApiResponse<OptOutSettingsDto> update(@Valid @RequestBody OptOutSettingsDto req) {
        PartnerSettings ps = settings();
        OptOutSettingsDto old = toDto(ps);
        List<String> out = clean(req.optOutKeywords());
        List<String> in = clean(req.optInKeywords());
        if (out.isEmpty()) {
            throw AppException.field("optOutKeywords", "Add at least one opt-out keyword (for example STOP)");
        }
        Set<String> overlap = new HashSet<>(out);
        overlap.retainAll(in);
        if (!overlap.isEmpty()) {
            throw AppException.field("optInKeywords", "A keyword cannot be both opt-out and opt-in: " + String.join(", ", overlap));
        }
        ps.setOptOutKeywords(new ArrayList<>(out));
        ps.setOptInKeywords(new ArrayList<>(in));
        ps.setOptOutAutoReply(req.autoReply() == null || req.autoReply().isBlank() ? null : req.autoReply().trim());
        repository.save(ps);
        OptOutSettingsDto now = toDto(ps);
        auditService.log("OPT_OUT_SETTINGS_UPDATE", "PARTNER_SETTINGS", ps.getPartnerId(), old, now);
        return ApiResponse.ok(now, "Opt-out settings saved");
    }

    private PartnerSettings settings() {
        return repository.findById(SecurityUtils.currentPartnerId()).orElseThrow(() -> AppException.notFound("Settings"));
    }

    private static List<String> clean(List<String> words) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String w : words) {
            String t = w.trim().toUpperCase(Locale.ROOT);
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        return new ArrayList<>(set);
    }

    private static OptOutSettingsDto toDto(PartnerSettings ps) {
        return new OptOutSettingsDto(ps.getOptOutKeywords(), ps.getOptInKeywords(), ps.getOptOutAutoReply());
    }
}
