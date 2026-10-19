package in.codebasket.cbwa.waba;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.meta.MetaSettings;
import in.codebasket.cbwa.meta.MetaSettingsService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.sender.dto.SenderDtos.CompleteSignupRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.ConnectResultDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.EmbeddedSignupConfigDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.SignupEventRequest;
import in.codebasket.cbwa.sender.dto.SenderDtos.StepDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** F2: Embedded Signup (v4) - config for the JS SDK and the server-to-server completion. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddedSignupService {

    private final MetaSettingsService metaSettings;
    private final MetaGraphClient graphClient;
    private final PartnerRepository partnerRepository;
    private final OnboardingService onboardingService;
    private final SenderAccessService access;
    private final WhatsappAccountRepository wabaRepository;

    /** E4 */
    @Transactional(readOnly = true)
    public EmbeddedSignupConfigDto config(Long partnerId) {
        requireAllowed(partnerId);
        MetaSettings m = metaSettings.get();
        if (m.getAppId() == null || m.getEmbeddedSignupConfigId() == null) {
            throw new AppException(ErrorCode.META_NOT_CONFIGURED, "Connecting your own number is not set up yet. Please contact support");
        }
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("setup", Map.of());
        extras.put("sessionInfoVersion", "3");
        if (m.getSolutionId() != null) {
            extras.put("setup", Map.of("solutionID", m.getSolutionId()));
        }
        return new EmbeddedSignupConfigDto(true, m.getAppId(), m.getEmbeddedSignupConfigId(), m.getGraphApiVersion(),
                m.getSolutionId(), List.of("", "whatsapp_business_app_onboarding"), extras);
    }

    /** E5: the code is valid for ~30 seconds, so it is exchanged before anything else. */
    public ConnectResultDto complete(Long partnerId, CompleteSignupRequest req) {
        requireAllowed(partnerId);
        OnboardingService.Mode mode = req.mode() == null || req.mode().isBlank() ? OnboardingService.Mode.NEW
                : OnboardingService.Mode.valueOf(req.mode());
        if (mode != OnboardingService.Mode.COEXISTENCE && (req.pin() == null || req.pin().isBlank())) {
            throw AppException.field("pin", "Choose a 6-digit PIN for your number");
        }
        List<StepDto> steps = new ArrayList<>();
        String token = exchange(req.code(), steps);
        return onboardingService.connect(new OnboardingService.Command(partnerId, token, TokenType.BUSINESS_INTEGRATION,
                OnboardingType.EMBEDDED_SIGNUP, req.wabaId(), req.phoneNumberId(), req.businessId(), mode, req.pin(),
                mode != OnboardingService.Mode.COEXISTENCE, steps));
    }

    /** E7: re-run Embedded Signup to refresh an invalid token. */
    public ConnectResultDto reconnect(Long partnerId, Long senderId, CompleteSignupRequest req) {
        PhoneNumber pn = access.requirePartnerSender(partnerId, senderId);
        if (!pn.getPhoneNumberId().equals(req.phoneNumberId())) {
            throw AppException.field("phoneNumberId", "Please choose the same phone number in the Facebook window");
        }
        WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
        List<StepDto> steps = new ArrayList<>();
        String token = exchange(req.code(), steps);
        OnboardingService.Mode mode = pn.isCoexistence() ? OnboardingService.Mode.COEXISTENCE : OnboardingService.Mode.EXISTING;
        return onboardingService.connect(new OnboardingService.Command(partnerId, token, TokenType.BUSINESS_INTEGRATION,
                OnboardingType.EMBEDDED_SIGNUP, waba.getWabaId(), pn.getPhoneNumberId(), req.businessId(), mode,
                req.pin(), !pn.isRegistered() && req.pin() != null && !req.pin().isBlank(), steps));
    }

    /** E6: browser-side CANCEL/ERROR events, logged for support. */
    public void logEvent(Long partnerId, SignupEventRequest req) {
        log.info("Embedded signup {} for partner {} at step '{}': {} {}", req.event(), partnerId, req.currentStep(),
                req.errorCode(), req.errorMessage());
    }

    private String exchange(String code, List<StepDto> steps) {
        try {
            String token = graphClient.exchangeCode(code);
            if (token == null || token.isBlank()) {
                throw new MetaApiException(400, null, null, "Facebook did not return an access token", null, null);
            }
            steps.add(new StepDto("EXCHANGE_CODE", OnboardingService.DONE, "Facebook login confirmed"));
            return token;
        } catch (MetaApiException e) {
            steps.add(new StepDto("EXCHANGE_CODE", OnboardingService.FAILED, e.friendlyMessage()));
            throw new AppException(ErrorCode.META_API_ERROR,
                    "Facebook sign-in could not be confirmed (" + e.friendlyMessage() + "). Please try again",
                    Map.of("steps", steps, "metaCode", e.getCode() == null ? "" : e.getCode()));
        }
    }

    private void requireAllowed(Long partnerId) {
        Partner p = partnerRepository.findById(partnerId).orElseThrow(() -> AppException.notFound("Partner"));
        if (!p.isAllowOwnNumber()) {
            throw new AppException(ErrorCode.OWN_NUMBER_NOT_ALLOWED);
        }
    }
}
