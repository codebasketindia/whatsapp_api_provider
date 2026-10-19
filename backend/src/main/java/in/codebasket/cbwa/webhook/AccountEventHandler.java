package in.codebasket.cbwa.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.messaging.lookup.MsgCampaignRepository;
import in.codebasket.cbwa.messaging.lookup.MsgTemplateRepository;
import in.codebasket.cbwa.partner.PartnerEvent;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateStatus;
import in.codebasket.cbwa.waba.WabaStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Template, phone number and account webhook fields (F8): keeps our records in sync with Meta,
 * auto-pauses senders and their campaigns on quality drops / restrictions and alerts partner + admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventHandler {

    static final String QUALITY_PAUSE_REASON = "Paused automatically: WhatsApp quality rating dropped";
    static final String ACCOUNT_PAUSE_REASON = "Paused automatically: WhatsApp account restricted by Meta";

    private final MsgTemplateRepository templateRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final in.codebasket.cbwa.waba.WhatsappAccountRepository wabaRepository;
    private final MsgCampaignRepository campaignRepository;
    private final PartnerRepository partnerRepository;
    private final SenderAccessService senderAccess;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- templates

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean templateStatus(WhatsappAccount waba, JsonNode v) {
        Optional<Template> ot = findTemplate(waba, v);
        if (ot.isEmpty()) {
            return false;
        }
        Template t = ot.get();
        String event = v.path("event").asText("").toUpperCase();
        TemplateStatus status = switch (event) {
            case "APPROVED", "REINSTATED", "UNPAUSED" -> TemplateStatus.APPROVED;
            case "REJECTED" -> TemplateStatus.REJECTED;
            case "PENDING" -> TemplateStatus.PENDING;
            case "PAUSED" -> TemplateStatus.PAUSED;
            case "DISABLED" -> TemplateStatus.DISABLED;
            case "IN_APPEAL" -> TemplateStatus.IN_APPEAL;
            case "PENDING_DELETION" -> TemplateStatus.PENDING_DELETION;
            case "DELETED" -> TemplateStatus.DELETED;
            case "LIMIT_EXCEEDED" -> TemplateStatus.LIMIT_EXCEEDED;
            case "ARCHIVED" -> TemplateStatus.ARCHIVED;
            default -> null;
        };
        if (event.equals("FLAGGED")) {
            t.setQualityScore("RED");
        }
        if (status != null) {
            t.setStatus(status);
        }
        String reason = v.path("reason").asText(null);
        if (status == TemplateStatus.REJECTED || status == TemplateStatus.PAUSED || status == TemplateStatus.DISABLED) {
            String info = v.path("other_info").path("description").asText(null);
            t.setRejectedReason(trim(reason != null && !"NONE".equalsIgnoreCase(reason) ? reason + (info != null ? ": " + info : "") : info, 500));
        } else if (status == TemplateStatus.APPROVED) {
            t.setRejectedReason(null);
        }
        t.setLastSyncedAt(Instant.now());
        templateRepository.save(t);

        Long partnerId = t.getPartnerId() != null ? t.getPartnerId() : waba.getPartnerId();
        String nice = status == null ? event : status.name().replace('_', ' ').toLowerCase();
        String title = "Template " + t.getName() + " is " + nice;
        String body = "Your template \"" + t.getName() + "\" (" + t.getLanguage() + ") status changed to " + nice
                + (t.getRejectedReason() != null && status != TemplateStatus.APPROVED ? ". Reason: " + t.getRejectedReason() : ".");
        String severity = status == TemplateStatus.APPROVED ? NotificationService.SUCCESS
                : status == TemplateStatus.REJECTED || status == TemplateStatus.DISABLED || status == TemplateStatus.PAUSED
                ? NotificationService.WARNING : NotificationService.INFO;
        if (partnerId != null) {
            notifications.notifyPartner(partnerId, "TEMPLATE_STATUS", severity, title, body, "/partner/templates/" + t.getId(),
                    partnerEmail(partnerId));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("templateId", t.getId());
            data.put("name", t.getName());
            data.put("language", t.getLanguage());
            data.put("status", t.getStatus().name());
            data.put("event", event);
            data.put("reason", t.getRejectedReason());
            events.publishEvent(new PartnerEvent(partnerId, PartnerEvent.TEMPLATE_STATUS, data));
        }
        if (status == TemplateStatus.PAUSED || status == TemplateStatus.DISABLED) {
            notifications.notifyAdmins("TEMPLATE_STATUS", severity, title, body, "/admin/templates");
        }
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean templateQuality(WhatsappAccount waba, JsonNode v) {
        return findTemplate(waba, v).map(t -> {
            String q = v.path("new_quality_score").asText("UNKNOWN").toUpperCase();
            t.setQualityScore(q);
            templateRepository.save(t);
            Long partnerId = t.getPartnerId() != null ? t.getPartnerId() : waba.getPartnerId();
            if (partnerId != null && ("RED".equals(q) || "YELLOW".equals(q))) {
                notifications.notifyPartner(partnerId, "TEMPLATE_QUALITY", NotificationService.WARNING,
                        "Template quality is " + q.toLowerCase(), "Recipients are reacting negatively to \"" + t.getName()
                                + "\". Improve the content to avoid the template being paused.", "/partner/templates/" + t.getId(), null);
            }
            return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean templateCategory(WhatsappAccount waba, JsonNode v) {
        return findTemplate(waba, v).map(t -> {
            String next = v.path("new_category").asText(v.path("correct_category").asText(null));
            if (next != null) {
                try {
                    MessageCategory c = MessageCategory.valueOf(next.toUpperCase());
                    t.setPreviousCategory(t.getCategory().name());
                    t.setCategory(c);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown template category {}", next);
                }
            }
            templateRepository.save(t);
            Long partnerId = t.getPartnerId() != null ? t.getPartnerId() : waba.getPartnerId();
            if (partnerId != null && next != null) {
                notifications.notifyPartner(partnerId, "TEMPLATE_CATEGORY", NotificationService.INFO,
                        "Template category changed", "Meta changed the category of \"" + t.getName() + "\" to " + next
                                + ". Prices for this template follow the new category.", "/partner/templates/" + t.getId(), null);
            }
            return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean templateComponents(WhatsappAccount waba, JsonNode v) {
        return findTemplate(waba, v).map(t -> {
            ObjectNode payload = t.getMetaPayload() == null ? JsonUtil.MAPPER.createObjectNode()
                    : (ObjectNode) JsonUtil.readTree(t.getMetaPayload());
            payload.set("components_update", v);
            t.setMetaPayload(payload.toString());
            t.setLastSyncedAt(Instant.now());
            templateRepository.save(t);
            return true;
        }).orElse(false);
    }

    private Optional<Template> findTemplate(WhatsappAccount waba, JsonNode v) {
        String metaId = v.path("message_template_id").asText(null);
        Optional<Template> t = metaId == null ? Optional.empty() : templateRepository.findByMetaTemplateId(metaId);
        if (t.isEmpty() && v.hasNonNull("message_template_name") && v.hasNonNull("message_template_language")) {
            t = templateRepository.findByWhatsappAccountIdAndNameAndLanguage(waba.getId(),
                    v.get("message_template_name").asText(), v.get("message_template_language").asText());
            t.ifPresent(x -> {
                if (x.getMetaTemplateId() == null && metaId != null) {
                    x.setMetaTemplateId(metaId);
                }
            });
        }
        return t;
    }

    // ---------------------------------------------------------------- phone numbers

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean phoneQuality(WhatsappAccount waba, JsonNode v) {
        PhoneNumber pn = findPhone(waba, v.path("display_phone_number").asText(null)).orElse(null);
        if (pn == null) {
            return false;
        }
        String event = v.path("event").asText("").toUpperCase();
        String limit = v.path("current_limit").asText(null);
        if (limit != null) {
            pn.setMessagingLimitTier(limit);
        }
        switch (event) {
            case "FLAGGED" -> pn.setQualityRating("RED");
            case "UNFLAGGED" -> pn.setQualityRating("GREEN");
            default -> {
                // UPGRADE / DOWNGRADE / ONBOARDING change the messaging limit only
            }
        }
        pn.setLastSyncedAt(Instant.now());
        boolean pause = "FLAGGED".equals(event) || "DOWNGRADE".equals(event);
        if (pause && !pn.isPaused()) {
            pn.setPaused(true);
            pn.setPauseReason(QUALITY_PAUSE_REASON);
        } else if ("UNFLAGGED".equals(event) && pn.isPaused() && QUALITY_PAUSE_REASON.equals(pn.getPauseReason())) {
            pn.setPaused(false);
            pn.setPauseReason(null);
        }
        phoneNumberRepository.save(pn);
        int pausedCampaigns = pause ? campaignRepository.pauseActiveForSender(pn.getId(), "SENDER_QUALITY") : 0;
        String title = "Number " + pn.getDisplayPhoneNumber() + ": " + event.toLowerCase().replace('_', ' ');
        String body = pause
                ? "WhatsApp lowered the quality or limit of " + pn.getDisplayPhoneNumber() + ". Sending is paused"
                + (pausedCampaigns > 0 ? " and " + pausedCampaigns + " campaign(s) were paused" : "")
                + ". Review your recent messages, then resume from the number page."
                : "WhatsApp updated " + pn.getDisplayPhoneNumber() + " (" + event.toLowerCase() + (limit != null ? ", limit " + limit : "") + ").";
        alertSender(pn, "SENDER_QUALITY", pause ? NotificationService.ERROR : NotificationService.INFO, title, body, event, pause);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean phoneName(WhatsappAccount waba, JsonNode v) {
        PhoneNumber pn = findPhone(waba, v.path("display_phone_number").asText(null)).orElse(null);
        if (pn == null) {
            return false;
        }
        String decision = v.path("decision").asText("").toUpperCase();
        pn.setNameStatus(decision);
        if ("APPROVED".equals(decision) && v.hasNonNull("requested_verified_name")) {
            pn.setVerifiedName(v.get("requested_verified_name").asText());
        }
        pn.setNewNameStatus(null);
        phoneNumberRepository.save(pn);
        String reason = v.path("rejection_reason").asText(null);
        alertSender(pn, "SENDER_NAME", "APPROVED".equals(decision) ? NotificationService.SUCCESS : NotificationService.WARNING,
                "Display name " + decision.toLowerCase(), "Display name \"" + v.path("requested_verified_name").asText("")
                        + "\" was " + decision.toLowerCase() + (reason != null && !"NONE".equalsIgnoreCase(reason) ? ": " + reason : "."),
                "NAME_" + decision, false);
        return true;
    }

    // ---------------------------------------------------------------- account level

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean accountUpdate(WhatsappAccount waba, JsonNode v) {
        String event = v.path("event").asText("").toUpperCase();
        String banState = v.path("ban_info").path("waba_ban_state").asText("").toUpperCase();
        boolean pause = false;
        String severity = NotificationService.INFO;
        String body;
        if ("DISABLE".equals(banState) || "DISABLED_UPDATE".equals(event) && v.has("ban_info")) {
            waba.setStatus(WabaStatus.DISABLED);
            pause = true;
            severity = NotificationService.ERROR;
            body = "Meta disabled the WhatsApp account " + nz(waba.getName()) + ". Sending from its numbers is paused.";
        } else if ("REINSTATE".equals(banState)) {
            waba.setStatus(WabaStatus.ACTIVE);
            unpauseAccountSenders(waba);
            severity = NotificationService.SUCCESS;
            body = "Meta reinstated the WhatsApp account " + nz(waba.getName()) + ".";
        } else if ("SCHEDULE_FOR_DISABLE".equals(banState)) {
            severity = NotificationService.WARNING;
            body = "Meta scheduled the WhatsApp account " + nz(waba.getName()) + " to be disabled. Contact support urgently.";
        } else if (v.has("restriction_info") || "ACCOUNT_RESTRICTION".equals(event)) {
            waba.setStatus(WabaStatus.RESTRICTED);
            waba.setRestrictionInfo(v.path("restriction_info").toString());
            pause = true;
            severity = NotificationService.ERROR;
            body = "Meta restricted the WhatsApp account " + nz(waba.getName()) + ". Sending from its numbers is paused.";
        } else if ("PARTNER_REMOVED".equals(event)) {
            waba.setStatus(WabaStatus.DISCONNECTED);
            waba.setDisconnectedAt(Instant.now());
            pause = true;
            severity = NotificationService.ERROR;
            body = "Access to the WhatsApp account " + nz(waba.getName()) + " was removed. Please reconnect the number.";
        } else if ("ACCOUNT_VIOLATION".equals(event)) {
            severity = NotificationService.WARNING;
            body = "Meta reported a policy violation on " + nz(waba.getName()) + ": "
                    + v.path("violation_info").path("violation_type").asText("see WhatsApp Manager") + ".";
        } else {
            body = "WhatsApp account update: " + event.toLowerCase().replace('_', ' ') + ".";
        }
        mergeRaw(waba, "last_account_update", v);
        wabaRepository.save(waba);
        if (pause) {
            for (PhoneNumber pn : phoneNumberRepository.findByWhatsappAccountId(waba.getId())) {
                if (!pn.isPaused()) {
                    pn.setPaused(true);
                    pn.setPauseReason(ACCOUNT_PAUSE_REASON);
                    phoneNumberRepository.save(pn);
                }
                campaignRepository.pauseActiveForSender(pn.getId(), "SENDER_QUALITY");
            }
        }
        alertAccount(waba, "ACCOUNT_UPDATE", severity, "WhatsApp account update", body, event, pause);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean accountReview(WhatsappAccount waba, JsonNode v) {
        String decision = v.path("decision").asText("").toUpperCase();
        waba.setAccountReviewStatus(decision);
        wabaRepository.save(waba);
        alertAccount(waba, "ACCOUNT_REVIEW", "APPROVED".equals(decision) ? NotificationService.SUCCESS : NotificationService.WARNING,
                "Account review " + decision.toLowerCase(), "Meta's review of " + nz(waba.getName()) + " is " + decision.toLowerCase() + ".",
                "REVIEW_" + decision, false);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean accountAlert(WhatsappAccount waba, JsonNode v) {
        String severity = v.path("alert_severity").asText("INFORMATIONAL").toUpperCase();
        String desc = v.path("alert_description").asText(v.path("alert_type").asText("Account alert"));
        alertAccount(waba, "ACCOUNT_ALERT", "CRITICAL".equals(severity) ? NotificationService.ERROR
                        : "WARNING".equals(severity) ? NotificationService.WARNING : NotificationService.INFO,
                "WhatsApp account alert", desc, v.path("alert_type").asText("ALERT"), false);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean businessCapability(WhatsappAccount waba, JsonNode v) {
        mergeRaw(waba, "business_capability", v);
        wabaRepository.save(waba);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean security(WhatsappAccount waba, JsonNode v) {
        String event = v.path("event").asText("SECURITY").toUpperCase();
        alertAccount(waba, "SECURITY", NotificationService.WARNING, "Security event on WhatsApp number",
                "Meta reported a security event (" + event.toLowerCase().replace('_', ' ') + ") for "
                        + v.path("display_phone_number").asText(nz(waba.getName())) + ". If this was not you, contact support.",
                event, false);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    public Optional<PhoneNumber> findPhone(WhatsappAccount waba, String displayNumber) {
        if (displayNumber == null) {
            return Optional.empty();
        }
        String digits = displayNumber.replaceAll("[^0-9]", "");
        List<PhoneNumber> numbers = phoneNumberRepository.findByWhatsappAccountId(waba.getId());
        return numbers.stream().filter(p -> digits.equals(p.getWaNumber())
                || (p.getDisplayPhoneNumber() != null && digits.equals(p.getDisplayPhoneNumber().replaceAll("[^0-9]", "")))).findFirst();
    }

    private void unpauseAccountSenders(WhatsappAccount waba) {
        for (PhoneNumber pn : phoneNumberRepository.findByWhatsappAccountId(waba.getId())) {
            if (pn.isPaused() && ACCOUNT_PAUSE_REASON.equals(pn.getPauseReason())) {
                pn.setPaused(false);
                pn.setPauseReason(null);
                phoneNumberRepository.save(pn);
            }
        }
    }

    private void alertSender(PhoneNumber pn, String type, String severity, String title, String body, String event, boolean paused) {
        Optional<Long> partnerId = senderAccess.partnerOf(pn.getId());
        partnerId.ifPresent(pid -> {
            notifications.notifyPartner(pid, type, severity, title, body, "/partner/senders/" + pn.getId(),
                    NotificationService.ERROR.equals(severity) ? partnerEmail(pid) : null);
            events.publishEvent(new PartnerEvent(pid, PartnerEvent.SENDER_STATUS, senderData(pn, event, paused)));
        });
        if (!NotificationService.INFO.equals(severity) && !NotificationService.SUCCESS.equals(severity)) {
            notifications.notifyAdmins(type, severity, title, body, "/admin/whatsapp/senders/" + pn.getId());
        }
    }

    private void alertAccount(WhatsappAccount waba, String type, String severity, String title, String body, String event, boolean paused) {
        Set<Long> partners = new HashSet<>();
        if (waba.getPartnerId() != null) {
            partners.add(waba.getPartnerId());
        }
        List<PhoneNumber> numbers = phoneNumberRepository.findByWhatsappAccountId(waba.getId());
        numbers.forEach(pn -> senderAccess.partnerOf(pn.getId()).ifPresent(partners::add));
        for (Long pid : partners) {
            notifications.notifyPartner(pid, type, severity, title, body, "/partner/senders",
                    NotificationService.ERROR.equals(severity) ? partnerEmail(pid) : null);
            for (PhoneNumber pn : numbers) {
                if (senderAccess.partnerOf(pn.getId()).filter(pid::equals).isPresent()) {
                    events.publishEvent(new PartnerEvent(pid, PartnerEvent.SENDER_STATUS, senderData(pn, event, paused)));
                }
            }
        }
        notifications.notifyAdmins(type, severity, title, body, "/admin/whatsapp/accounts/" + waba.getId());
    }

    private static Map<String, Object> senderData(PhoneNumber pn, String event, boolean paused) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("senderId", pn.getId());
        data.put("phoneNumber", pn.getWaNumber());
        data.put("event", event);
        data.put("qualityRating", pn.getQualityRating());
        data.put("messagingLimitTier", pn.getMessagingLimitTier());
        data.put("paused", pn.isPaused() || paused);
        data.put("pauseReason", pn.getPauseReason());
        return data;
    }

    private static void mergeRaw(WhatsappAccount waba, String key, JsonNode v) {
        JsonNode raw = waba.getRaw() == null ? JsonUtil.MAPPER.createObjectNode() : JsonUtil.readTree(waba.getRaw());
        ObjectNode obj = raw.isObject() ? (ObjectNode) raw : JsonUtil.MAPPER.createObjectNode();
        obj.set(key, v);
        waba.setRaw(obj.toString());
    }

    private String partnerEmail(Long partnerId) {
        return partnerRepository.findById(partnerId).map(p -> p.getEmail()).orElse(null);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.substring(0, Math.min(max, s.length()));
    }
}
