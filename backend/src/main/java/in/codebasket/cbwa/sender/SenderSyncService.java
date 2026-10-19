package in.codebasket.cbwa.sender;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaWabaApi;
import in.codebasket.cbwa.partner.PartnerEvent;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.waba.TokenStatus;
import in.codebasket.cbwa.waba.WabaMapper;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Refreshes WABA and phone-number data from Meta and applies the health rules:
 * quality RED or a restricted/flagged/banned number -> sender auto-paused, its running campaigns
 * paused, partner + admins alerted (F8/F9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SenderSyncService {

    private static final Set<String> BAD_STATUSES = Set.of("FLAGGED", "RESTRICTED", "BANNED", "DISCONNECTED", "DELETED", "RATE_LIMITED");

    private final MetaWabaApi wabaApi;
    private final CryptoService crypto;
    private final WhatsappAccountRepository wabaRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PartnerRepository partnerRepository;
    private final SenderCampaignRepository campaignRepository;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    /** Re-reads the WABA and all its numbers. Meta calls happen before the short write transaction. */
    public void syncWaba(WhatsappAccount waba) {
        String token = crypto.decrypt(waba.getAccessTokenEnc());
        JsonNode wabaJson;
        List<JsonNode> phones;
        try {
            wabaJson = wabaApi.getWaba(waba.getWabaId(), token);
            phones = wabaApi.listPhoneNumbers(waba.getWabaId(), token);
        } catch (MetaApiException e) {
            if (e.isTokenInvalid()) {
                tx.executeWithoutResult(st -> markTokenInvalid(waba.getId()));
            }
            throw e;
        }
        tx.executeWithoutResult(st -> persistWaba(waba.getId(), wabaJson, phones));
    }

    @Transactional
    public void persistWaba(Long wabaDbId, JsonNode wabaJson, List<JsonNode> phones) {
        WhatsappAccount w = wabaRepository.findById(wabaDbId).orElseThrow();
        WabaMapper.apply(w, wabaJson);
        w.setTokenStatus(TokenStatus.VALID);
        w.setTokenCheckedAt(Instant.now());
        wabaRepository.save(w);
        for (JsonNode p : phones) {
            phoneNumberRepository.findByPhoneNumberId(p.path("id").asText()).ifPresent(pn -> applyAndCheck(pn, p));
        }
    }

    public void syncSender(PhoneNumber pn) {
        WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
        JsonNode json;
        try {
            json = wabaApi.getPhone(pn.getPhoneNumberId(), crypto.decrypt(waba.getAccessTokenEnc()));
        } catch (MetaApiException e) {
            if (e.isTokenInvalid()) {
                tx.executeWithoutResult(st -> markTokenInvalid(waba.getId()));
            }
            throw e;
        }
        tx.executeWithoutResult(st -> persistPhone(pn.getId(), json));
    }

    @Transactional
    public void persistPhone(Long senderId, JsonNode json) {
        phoneNumberRepository.findById(senderId).ifPresent(pn -> applyAndCheck(pn, json));
    }

    @Transactional
    public void markTokenInvalid(Long wabaDbId) {
        wabaRepository.findById(wabaDbId).ifPresent(w -> {
            boolean changed = w.getTokenStatus() != TokenStatus.INVALID;
            w.setTokenStatus(TokenStatus.INVALID);
            w.setTokenCheckedAt(Instant.now());
            wabaRepository.save(w);
            if (changed) {
                alertTokenInvalid(w);
            }
        });
    }

    public void alertTokenInvalid(WhatsappAccount w) {
        String title = "WhatsApp access expired";
        String body = "Access to WhatsApp account " + (w.getName() == null ? w.getWabaId() : w.getName())
                + " has expired or was removed. Reconnect the number to keep sending messages.";
        notifications.notifyAdmins("WABA_TOKEN_INVALID", NotificationService.ERROR, title, body, "/admin/whatsapp/accounts/" + w.getId());
        if (w.getPartnerId() != null) {
            String email = partnerRepository.findById(w.getPartnerId()).map(p -> p.getEmail()).orElse(null);
            notifications.notifyPartner(w.getPartnerId(), "WABA_TOKEN_INVALID", NotificationService.ERROR, title, body,
                    "/partner/senders", email);
        }
    }

    /** Applies Meta fields and the auto-pause rule. Must run inside a transaction. */
    void applyAndCheck(PhoneNumber pn, JsonNode json) {
        String oldQuality = pn.getQualityRating();
        String oldStatus = pn.getMetaStatus();
        SenderMapper.apply(pn, json);
        if (pn.getStatus() == SenderStatus.PENDING_SETUP && "CONNECTED".equalsIgnoreCase(pn.getMetaStatus())) {
            pn.setRegistered(true);
            pn.setStatus(SenderStatus.CONNECTED);
        }
        phoneNumberRepository.save(pn);
        checkHealth(pn, oldQuality, oldStatus);
    }

    /** Pauses the sender (once) when quality drops to RED or Meta restricts the number. */
    public void checkHealth(PhoneNumber pn, String oldQuality, String oldStatus) {
        boolean red = "RED".equals(pn.getQualityRating()) && !"RED".equals(oldQuality);
        String status = pn.getMetaStatus() == null ? "" : pn.getMetaStatus().toUpperCase();
        boolean restricted = BAD_STATUSES.contains(status) && !status.equalsIgnoreCase(oldStatus == null ? "" : oldStatus);
        if (!red && !restricted) {
            if (!pn.getQualityRating().equals(oldQuality == null ? "" : oldQuality) || !status.equalsIgnoreCase(oldStatus == null ? "" : oldStatus)) {
                publishStatus(pn);
            }
            return;
        }
        String reason = red ? "Quality rating dropped to RED" : "Meta status changed to " + status;
        if (!pn.isPaused()) {
            pn.setPaused(true);
            pn.setPauseReason(reason);
            phoneNumberRepository.save(pn);
        }
        int paused = campaignRepository.pauseActiveForSender(pn.getId(), "SENDER_QUALITY");
        String body = "Sender " + pn.getDisplayPhoneNumber() + " was paused: " + reason + "."
                + (paused > 0 ? " " + paused + " running campaign(s) were paused." : "")
                + " Review your message content and opt-in practices before resuming.";
        notifications.notifyAdmins("SENDER_HEALTH", NotificationService.ERROR, "Sender paused", body, "/admin/whatsapp/senders/" + pn.getId());
        assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).ifPresent(a -> {
            String email = partnerRepository.findById(a.getPartnerId()).map(p -> p.getEmail()).orElse(null);
            notifications.notifyPartner(a.getPartnerId(), "SENDER_HEALTH", NotificationService.ERROR, "Sender paused", body,
                    "/partner/senders/" + pn.getId(), email);
        });
        log.warn("Sender {} auto-paused: {}", pn.getPhoneNumberId(), reason);
        publishStatus(pn);
    }

    private void publishStatus(PhoneNumber pn) {
        assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).ifPresent(a -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("senderId", pn.getId());
            data.put("phoneNumber", pn.getDisplayPhoneNumber());
            data.put("qualityRating", pn.getQualityRating());
            data.put("metaStatus", pn.getMetaStatus());
            data.put("isPaused", pn.isPaused());
            data.put("pauseReason", pn.getPauseReason());
            events.publishEvent(new PartnerEvent(a.getPartnerId(), PartnerEvent.SENDER_STATUS, data));
        });
    }
}
