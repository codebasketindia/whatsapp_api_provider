package in.codebasket.cbwa.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.messaging.MessageStatus;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Multi-tenant routing of a Meta webhook payload: entry.id = WABA ID, value.metadata.phone_number_id
 * identifies the sender; the active assignment of that sender identifies the partner.
 * Returns true when at least one change was applied (false -> log IGNORED).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetaWebhookHandler {

    private final WhatsappAccountRepository wabaRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final SenderAccessService senderAccess;
    private final InboundMessageHandler messageHandler;
    private final AccountEventHandler accountHandler;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean handle(JsonNode payload) {
        if (!"whatsapp_business_account".equals(payload.path("object").asText())) {
            return false;
        }
        boolean handled = false;
        for (JsonNode entry : payload.path("entry")) {
            String wabaId = entry.path("id").asText(null);
            WhatsappAccount waba = wabaId == null ? null : wabaRepository.findByWabaId(wabaId).orElse(null);
            for (JsonNode change : entry.path("changes")) {
                String field = change.path("field").asText("");
                JsonNode value = change.path("value");
                try {
                    handled |= route(field, value, waba);
                } catch (RuntimeException e) {
                    log.warn("Webhook field {} for WABA {} failed: {}", field, wabaId, e.getMessage());
                    throw e;
                }
            }
        }
        return handled;
    }

    private boolean route(String field, JsonNode value, WhatsappAccount waba) {
        switch (field) {
            case "messages" -> {
                return messages(value);
            }
            case "smb_message_echoes" -> {
                return echoes(value);
            }
            case "history" -> {
                return history(value);
            }
            default -> {
                // fall through to WABA-level fields
            }
        }
        if (waba == null) {
            log.info("Webhook field {} for unknown WABA ignored", field);
            return false;
        }
        return switch (field) {
            case "message_template_status_update" -> accountHandler.templateStatus(waba, value);
            case "message_template_quality_update" -> accountHandler.templateQuality(waba, value);
            case "message_template_components_update" -> accountHandler.templateComponents(waba, value);
            case "template_category_update" -> accountHandler.templateCategory(waba, value);
            case "phone_number_quality_update" -> accountHandler.phoneQuality(waba, value);
            case "phone_number_name_update" -> accountHandler.phoneName(waba, value);
            case "account_update" -> accountHandler.accountUpdate(waba, value);
            case "account_review_update" -> accountHandler.accountReview(waba, value);
            case "account_alerts" -> accountHandler.accountAlert(waba, value);
            case "business_capability_update" -> accountHandler.businessCapability(waba, value);
            case "security" -> accountHandler.security(waba, value);
            default -> {
                log.debug("Webhook field {} not handled", field);
                yield false;
            }
        };
    }

    private boolean messages(JsonNode value) {
        PhoneNumber pn = sender(value);
        if (pn == null) {
            return false;
        }
        Long partnerId = senderAccess.partnerOf(pn.getId()).orElse(null);
        boolean handled = false;
        for (JsonNode st : value.path("statuses")) {
            messageHandler.handleStatus(st, partnerId);
            handled = true;
        }
        if (value.has("messages")) {
            if (partnerId == null) {
                log.warn("Inbound message for unassigned sender {} ignored", pn.getPhoneNumberId());
            } else {
                Map<String, String> names = new HashMap<>();
                for (JsonNode c : value.path("contacts")) {
                    names.put(c.path("wa_id").asText(), c.path("profile").path("name").asText(null));
                }
                for (JsonNode msg : value.path("messages")) {
                    handled |= messageHandler.handleInbound(pn, partnerId, msg, names.get(msg.path("from").asText()));
                }
            }
        }
        for (JsonNode err : value.path("errors")) {
            log.warn("Meta webhook error for sender {}: {} {}", pn.getPhoneNumberId(), err.path("code").asText(), err.path("title").asText());
            handled = true;
        }
        return handled;
    }

    private boolean echoes(JsonNode value) {
        PhoneNumber pn = sender(value);
        Long partnerId = pn == null ? null : senderAccess.partnerOf(pn.getId()).orElse(null);
        if (partnerId == null) {
            return false;
        }
        boolean handled = false;
        for (JsonNode msg : value.path("message_echoes")) {
            handled |= messageHandler.storeEcho(pn, partnerId, msg, true, MessageStatus.SENT);
        }
        return handled;
    }

    private boolean history(JsonNode value) {
        PhoneNumber pn = sender(value);
        Long partnerId = pn == null ? null : senderAccess.partnerOf(pn.getId()).orElse(null);
        if (partnerId == null) {
            return false;
        }
        boolean handled = false;
        for (JsonNode h : value.path("history")) {
            for (JsonNode thread : h.path("threads")) {
                for (JsonNode msg : thread.path("messages")) {
                    boolean outbound = pn.getWaNumber() != null && pn.getWaNumber().equals(msg.path("from").asText());
                    if (outbound && !msg.has("to")) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) msg).put("to", thread.path("id").asText());
                    }
                    MessageStatus st = switch (msg.path("history_context").path("status").asText("").toUpperCase()) {
                        case "DELIVERED" -> MessageStatus.DELIVERED;
                        case "READ", "PLAYED" -> MessageStatus.READ;
                        case "ERROR" -> MessageStatus.FAILED;
                        default -> MessageStatus.SENT;
                    };
                    handled |= messageHandler.storeEcho(pn, partnerId, msg, outbound, st);
                }
            }
        }
        return handled;
    }

    private PhoneNumber sender(JsonNode value) {
        String pnid = value.path("metadata").path("phone_number_id").asText(null);
        if (pnid == null) {
            return null;
        }
        PhoneNumber pn = phoneNumberRepository.findByPhoneNumberId(pnid).orElse(null);
        if (pn == null) {
            log.info("Webhook for unknown phone number id {} ignored", pnid);
        }
        return pn;
    }
}
