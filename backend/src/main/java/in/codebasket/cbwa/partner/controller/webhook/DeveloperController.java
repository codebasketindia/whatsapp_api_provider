package in.codebasket.cbwa.partner.controller.webhook;

import in.codebasket.cbwa.partner.service.webhook.PostmanCollectionBuilder;

import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.common.constants.SettingKeys;
import in.codebasket.cbwa.common.service.settings.SettingsService;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.partner.dto.PartnerWebhookDtos.DeveloperInfoDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** D12 developer info + Postman collection download (addition to the contract). */
@Tag(name = "Partner - Developer")
@RestController
@RequestMapping("/api/partner/developer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTNER')")
public class DeveloperController {

    private final AppProperties props;
    private final SettingsService settings;
    private final PostmanCollectionBuilder postman;

    @GetMapping("/info")
    public ApiResponse<DeveloperInfoDto> info() {
        return ApiResponse.ok(new DeveloperInfoDto(baseUrl(),
                settings.getInt(SettingKeys.RATE_LIMIT, SettingKeys.PUBLIC_API_PER_MINUTE, 600),
                "/swagger-ui.html", "/api/partner/developer/postman"));
    }

    @GetMapping("/postman")
    public ResponseEntity<String> postman() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cb-whatsapp-gateway.postman_collection.json\"")
                .body(postman.build(baseUrl()));
    }

    private String baseUrl() {
        return props.getPublicBaseUrl() + "/api/v1/public";
    }
}
