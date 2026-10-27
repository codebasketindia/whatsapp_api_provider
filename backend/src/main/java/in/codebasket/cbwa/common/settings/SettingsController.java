package in.codebasket.cbwa.common.settings;

import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.mail.MailService;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** ST1-ST4 + ST6 (public branding for the login page). Payment-gateway test (ST5) lives in the payment module. */
@Tag(name = "Admin - Settings")
@RestController
@RequiredArgsConstructor
public class SettingsController {

    /** API group names -> DB group codes. */
    public static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry("company", SettingKeys.COMPANY),
            Map.entry("gst", SettingKeys.GST),
            Map.entry("payment-gateway", SettingKeys.PAYMENT),
            Map.entry("smtp", SettingKeys.SMTP),
            Map.entry("charging", SettingKeys.CHARGING),
            Map.entry("wallet", SettingKeys.WALLET),
            Map.entry("rate-limit", SettingKeys.RATE_LIMIT),
            Map.entry("security", SettingKeys.SECURITY),
            Map.entry("storage", SettingKeys.STORAGE),
            Map.entry("retention", SettingKeys.RETENTION),
            Map.entry("ai", SettingKeys.AI));

    private final SettingsService settings;
    private final FileStorageService storage;
    private final MailService mailService;

    public record TestEmailRequest(@NotBlank @Email String to) {
    }

    @GetMapping("/api/admin/settings/{group}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> get(@PathVariable String group) {
        return ApiResponse.ok(withLogo(group, settings.getGroupForApi(code(group))));
    }

    @PutMapping("/api/admin/settings/{group}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> update(@PathVariable String group, @RequestBody Map<String, Object> values) {
        Map<String, Object> clean = new LinkedHashMap<>(values);
        clean.remove("logoUrl");
        clean.remove("logoFileUid");
        return ApiResponse.ok(withLogo(group, settings.updateGroup(code(group), clean)), "Settings saved");
    }

    @PostMapping(value = "/api/admin/settings/company/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, String>> logo(@RequestParam("file") MultipartFile file) {
        StoredFile f = storage.store(file, FileCategory.COMPANY_LOGO, null, Set.of("image/png", "image/jpeg"), 1024 * 1024);
        settings.set(SettingKeys.COMPANY, "logo_file_uid", f.getUid());
        return ApiResponse.ok(Map.of("logoUrl", logoUrl(f.getUid())), "Logo updated");
    }

    @PostMapping("/api/admin/settings/smtp/test")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Boolean>> testSmtp(@RequestBody @jakarta.validation.Valid TestEmailRequest req) {
        try {
            mailService.sendTest(req.to());
        } catch (Exception e) {
            throw AppException.badRequest("Email could not be sent: " + e.getMessage());
        }
        return ApiResponse.ok(Map.of("sent", true), "Test email sent");
    }

    @GetMapping("/api/public/branding")
    public ApiResponse<Map<String, Object>> branding() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("companyName", settings.getString(SettingKeys.COMPANY, "name", "CB WhatsApp Gateway"));
        String uid = settings.getString(SettingKeys.COMPANY, "logo_file_uid");
        out.put("logoUrl", uid == null ? null : "/api/public/branding/logo");
        out.put("supportEmail", settings.getString(SettingKeys.COMPANY, "support_email"));
        out.put("supportPhone", settings.getString(SettingKeys.COMPANY, "support_phone"));
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/public/branding/logo")
    public ResponseEntity<Resource> brandingLogo() {
        String uid = settings.getString(SettingKeys.COMPANY, "logo_file_uid");
        if (uid == null) {
            throw AppException.notFound("Logo");
        }
        StoredFile f = storage.get(uid);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getMimeType())).body(storage.load(f));
    }

    private Map<String, Object> withLogo(String group, Map<String, Object> values) {
        if ("company".equals(group)) {
            Object uid = values.get("logoFileUid");
            values.put("logoUrl", uid == null ? null : logoUrl(uid.toString()));
        }
        return values;
    }

    private static String logoUrl(String uid) {
        return "/api/public/branding/logo?v=" + uid;
    }

    private static String code(String group) {
        String code = GROUPS.get(group);
        if (code == null) {
            throw AppException.notFound("Settings group");
        }
        return code;
    }
}
