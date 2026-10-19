package in.codebasket.cbwa.meta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class MetaSettingsDtos {

    private MetaSettingsDtos() {
    }

    public record MetaSettingsDto(String appId, boolean appSecretSet, String configId, String solutionId,
                                  String businessPortfolioId, String graphApiVersion, boolean verifyTokenSet,
                                  String billingMode, String extendedCreditLineId, String webhookUrl,
                                  Instant lastTestAt, Boolean lastTestOk) {
    }

    public record UpdateMetaSettingsRequest(
            @Pattern(regexp = "^$|^[0-9]{5,32}$", message = "App ID must be digits") String appId,
            @Size(max = 128) String appSecret,
            @Pattern(regexp = "^$|^[0-9]{5,32}$", message = "Configuration ID must be digits") String configId,
            @Pattern(regexp = "^$|^[0-9]{5,32}$", message = "Solution ID must be digits") String solutionId,
            @Pattern(regexp = "^$|^[0-9]{5,32}$", message = "Business portfolio ID must be digits") String businessPortfolioId,
            @NotBlank @Pattern(regexp = "^v[0-9]{1,2}\\.[0-9]$", message = "Use a version like v25.0") String graphApiVersion,
            @Size(min = 8, max = 128, message = "Verify token must be 8-128 characters") String verifyToken,
            @NotBlank @Pattern(regexp = "TECH_PROVIDER|SOLUTION_PARTNER") String billingMode,
            @Pattern(regexp = "^$|^[0-9]{5,32}$", message = "Credit line ID must be digits") String extendedCreditLineId) {
    }

    public record TestResult(boolean appOk, boolean tokenOk, boolean webhookSubscribed, List<String> details) {
    }
}
