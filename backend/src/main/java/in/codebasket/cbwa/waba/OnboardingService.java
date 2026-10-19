package in.codebasket.cbwa.waba;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaSettings;
import in.codebasket.cbwa.meta.MetaSettingsService;
import in.codebasket.cbwa.meta.MetaWabaApi;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.AssignmentType;
import in.codebasket.cbwa.sender.ChecklistService;
import in.codebasket.cbwa.sender.PartnerPhoneNumber;
import in.codebasket.cbwa.sender.PartnerPhoneNumberRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAssignmentService;
import in.codebasket.cbwa.sender.SenderMapper;
import in.codebasket.cbwa.sender.SenderStatus;
import in.codebasket.cbwa.sender.dto.SenderDtos.ConnectResultDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.StepDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-to-server onboarding of a WABA + phone number, shared by Embedded Signup (E5/E7) and
 * admin Manual Connect (M12). Fatal steps (WABA/number lookup) abort; the rest are recorded as
 * steps and surface in the "What to do next" checklist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    private final MetaWabaApi wabaApi;
    private final MetaSettingsService metaSettings;
    private final CryptoService crypto;
    private final WhatsappAccountRepository wabaRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PartnerRepository partnerRepository;
    private final SenderAssignmentService assignmentService;
    private final SenderMapper senderMapper;
    private final ChecklistService checklistService;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public enum Mode { NEW, EXISTING, COEXISTENCE }

    public record Command(Long partnerId, String token, TokenType tokenType, OnboardingType onboardingType, String wabaId,
                          String phoneNumberId, String businessId, Mode mode, String pin, boolean register,
                          List<StepDto> priorSteps) {
    }

    public ConnectResultDto connect(Command cmd) {
        List<StepDto> steps = new ArrayList<>(cmd.priorSteps() == null ? List.of() : cmd.priorSteps());
        boolean partnerOwned = cmd.partnerId() != null;

        // 1. WABA must be reachable with this token (fatal)
        JsonNode wabaJson = fatal(steps, "VERIFY_WABA", "WhatsApp account found",
                () -> wabaApi.getWaba(cmd.wabaId(), cmd.token()));
        // 2. Phone number must belong to that WABA (fatal)
        List<JsonNode> phones = fatal(steps, "FETCH_NUMBER", "Phone number found",
                () -> wabaApi.listPhoneNumbers(cmd.wabaId(), cmd.token()));
        JsonNode phoneJson = phones.stream().filter(p -> cmd.phoneNumberId().equals(p.path("id").asText())).findFirst()
                .orElseThrow(() -> AppException.field("phoneNumberId", "This phone number does not belong to the WhatsApp account"));

        // 3. Ownership conflicts before changing anything at Meta
        checkConflicts(cmd);

        // 4. Webhook subscription (non-fatal)
        boolean subscribed = optional(steps, "SUBSCRIBE_WEBHOOKS", "Message updates connected",
                () -> wabaApi.subscribeApp(cmd.wabaId(), cmd.token()));

        // 5. Register (or coexistence sync)
        boolean registered = "CONNECTED".equalsIgnoreCase(phoneJson.path("status").asText());
        boolean coexistence = cmd.mode() == Mode.COEXISTENCE;
        if (coexistence) {
            steps.add(new StepDto("REGISTER_NUMBER", SKIPPED, "Not needed - number stays on the WhatsApp Business App"));
            optional(steps, "SYNC_CONTACTS", "WhatsApp Business App contacts synced",
                    () -> wabaApi.smbAppDataSync(cmd.phoneNumberId(), "smb_app_state_sync", cmd.token()));
            optional(steps, "SYNC_HISTORY", "Chat history sync started",
                    () -> wabaApi.smbAppDataSync(cmd.phoneNumberId(), "history", cmd.token()));
        } else if (cmd.register() && cmd.pin() != null && !cmd.pin().isBlank()) {
            registered = optional(steps, "REGISTER_NUMBER", "Number registered for the Cloud API",
                    () -> wabaApi.register(cmd.phoneNumberId(), cmd.pin(), cmd.token())) || registered;
        } else {
            steps.add(new StepDto("REGISTER_NUMBER", SKIPPED, registered ? "Number already registered" : "No PIN given - register later"));
        }

        // 6. Credit line for Solution Partner mode (partner-owned only, non-fatal)
        MetaSettings ms = metaSettings.get();
        BillingMode billingMode = partnerOwned ? ms.getBillingMode() : BillingMode.PLATFORM;
        boolean creditShared = false;
        if (partnerOwned && billingMode == BillingMode.SOLUTION_PARTNER && ms.getExtendedCreditLineId() != null) {
            creditShared = optional(steps, "SHARE_CREDIT_LINE", "Billing attached",
                    () -> wabaApi.shareCreditLine(ms.getExtendedCreditLineId(), cmd.wabaId(), cmd.token()));
        }

        // 7. Persist
        final boolean finalRegistered = registered;
        final boolean finalSubscribed = subscribed;
        final boolean finalCreditShared = creditShared;
        PhoneNumber saved = tx.execute(st -> persist(cmd, wabaJson, phoneJson, billingMode, finalSubscribed, finalRegistered,
                coexistence, finalCreditShared));
        steps.add(new StepDto("SAVE", DONE, "Number saved"));

        WhatsappAccount waba = wabaRepository.findById(saved.getWhatsappAccountId()).orElseThrow();
        return new ConnectResultDto(senderMapper.toDto(saved), checklistService.build(saved, waba), steps);
    }

    private void checkConflicts(Command cmd) {
        wabaRepository.findByWabaId(cmd.wabaId()).ifPresent(w -> {
            if (cmd.partnerId() == null && w.getOwnerType() == WabaOwnerType.PARTNER && w.getStatus() != WabaStatus.DISCONNECTED) {
                throw AppException.conflict("This WhatsApp account is connected by a partner");
            }
            if (cmd.partnerId() != null && (w.getOwnerType() == WabaOwnerType.PLATFORM
                    || (w.getPartnerId() != null && !w.getPartnerId().equals(cmd.partnerId()) && w.getStatus() != WabaStatus.DISCONNECTED))) {
                throw AppException.conflict("This WhatsApp account is already connected to another account");
            }
        });
        phoneNumberRepository.findByPhoneNumberId(cmd.phoneNumberId())
                .flatMap(pn -> assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()))
                .ifPresent(a -> {
                    if (cmd.partnerId() == null || !a.getPartnerId().equals(cmd.partnerId())) {
                        throw AppException.conflict("This phone number is already connected to another account");
                    }
                });
    }

    private PhoneNumber persist(Command cmd, JsonNode wabaJson, JsonNode phoneJson, BillingMode billingMode, boolean subscribed,
                                boolean registered, boolean coexistence, boolean creditShared) {
        boolean partnerOwned = cmd.partnerId() != null;
        WhatsappAccount w = wabaRepository.findByWabaId(cmd.wabaId()).orElseGet(WhatsappAccount::new);
        boolean newWaba = w.getId() == null;
        w.setWabaId(cmd.wabaId());
        w.setOwnerType(partnerOwned ? WabaOwnerType.PARTNER : WabaOwnerType.PLATFORM);
        w.setPartnerId(cmd.partnerId());
        if (cmd.businessId() != null && !cmd.businessId().isBlank()) {
            w.setBusinessId(cmd.businessId());
        }
        WabaMapper.apply(w, wabaJson);
        w.setAccessTokenEnc(crypto.encrypt(cmd.token()));
        w.setTokenType(cmd.tokenType());
        w.setTokenStatus(TokenStatus.VALID);
        w.setTokenCheckedAt(Instant.now());
        w.setWebhookSubscribed(subscribed || (!newWaba && w.isWebhookSubscribed()));
        w.setOnboardingType(cmd.onboardingType());
        w.setBillingMode(billingMode);
        w.setCreditLineShared(creditShared || (!newWaba && w.isCreditLineShared()));
        w.setStatus(WabaStatus.ACTIVE);
        w.setDisconnectedAt(null);
        if (w.getConnectedAt() == null) {
            w.setConnectedAt(Instant.now());
        }
        wabaRepository.save(w);

        PhoneNumber pn = phoneNumberRepository.findByPhoneNumberId(cmd.phoneNumberId()).orElseGet(PhoneNumber::new);
        pn.setWhatsappAccountId(w.getId());
        pn.setPhoneNumberId(cmd.phoneNumberId());
        SenderMapper.apply(pn, phoneJson);
        pn.setSenderType(partnerOwned ? SenderType.PARTNER_OWN_SENDER : SenderType.PLATFORM_SENDER);
        pn.setCoexistence(coexistence);
        pn.setRegistered(registered);
        if (cmd.pin() != null && !cmd.pin().isBlank() && !coexistence) {
            pn.setPinEnc(crypto.encrypt(cmd.pin()));
        }
        pn.setStatus(registered || coexistence ? SenderStatus.CONNECTED : SenderStatus.PENDING_SETUP);
        pn.setPaused(false);
        pn.setPauseReason(null);
        pn.setConnectedAt(Instant.now());
        if (pn.getMaxMps() <= 0) {
            pn.setMaxMps(60);
        }
        phoneNumberRepository.saveAndFlush(pn);

        if (partnerOwned && assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).isEmpty()) {
            PartnerPhoneNumber a = new PartnerPhoneNumber();
            a.setPartnerId(cmd.partnerId());
            a.setPhoneNumberId(pn.getId());
            a.setAssignmentType(AssignmentType.OWN);
            a.setActive(true);
            a.setAssignedAt(Instant.now());
            a.setAssignedBy(in.codebasket.cbwa.auth.SecurityUtils.currentUserIdOrNull());
            assignmentRepository.saveAndFlush(a);
            if (assignmentRepository.findByPartnerIdAndActiveTrue(cmd.partnerId()).size() == 1) {
                assignmentService.makeDefault(cmd.partnerId(), pn.getId());
            }
        }
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("wabaId", cmd.wabaId());
        audit.put("phoneNumberId", cmd.phoneNumberId());
        audit.put("mode", cmd.mode() == null ? null : cmd.mode().name());
        audit.put("onboarding", cmd.onboardingType().name());
        auditService.log("SENDER_CONNECT", "SENDER", pn.getId(), null, audit, cmd.partnerId());
        String who = partnerOwned ? partnerRepository.findById(cmd.partnerId()).map(Partner::getBusinessName).orElse("A partner") : "Platform";
        notifications.notifyAdmins("SENDER_CONNECTED", NotificationService.INFO, "Number connected",
                who + " connected " + pn.getDisplayPhoneNumber() + " (" + pn.getVerifiedName() + ").", "/admin/whatsapp/senders/" + pn.getId());
        return pn;
    }

    private interface MetaCall<T> {
        T call();
    }

    private interface MetaAction {
        void run();
    }

    private <T> T fatal(List<StepDto> steps, String step, String okMessage, MetaCall<T> call) {
        try {
            T res = call.call();
            steps.add(new StepDto(step, DONE, okMessage));
            return res;
        } catch (MetaApiException e) {
            steps.add(new StepDto(step, FAILED, e.friendlyMessage()));
            throw e;
        }
    }

    private boolean optional(List<StepDto> steps, String step, String okMessage, MetaAction action) {
        try {
            action.run();
            steps.add(new StepDto(step, DONE, okMessage));
            return true;
        } catch (MetaApiException e) {
            log.warn("Onboarding step {} failed: {}", step, e.getMessage());
            steps.add(new StepDto(step, FAILED, e.friendlyMessage()));
            return false;
        }
    }
}
