package in.codebasket.cbwa.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.meta.MetaErrorMapper;
import in.codebasket.cbwa.partner.PartnerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies Meta status webhooks (sent / delivered / read / failed) to outbound messages.
 * Idempotent: one message_status_events row per (wamid, status); the message row is locked while
 * status, timestamps and charging are updated. Status never moves backwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStatusService {

    private final MessageRepository messageRepository;
    private final MessageStatusEventRepository eventRepository;
    private final ChargingService chargingService;
    private final ApplicationEventPublisher events;

    /** @param st one element of value.statuses[] from the webhook */
    @Transactional
    public void applyWebhookStatus(JsonNode st) {
        String wamid = st.path("id").asText(null);
        String status = st.path("status").asText("").toLowerCase();
        if (wamid == null || status.isEmpty()) {
            return;
        }
        if (eventRepository.existsByWamidAndStatus(wamid, status)) {
            return;
        }
        Message m = messageRepository.lockByWamid(wamid).orElse(null);
        // re-check under the lock (another worker may have just inserted it)
        if (m != null && eventRepository.existsByWamidAndStatus(wamid, status)) {
            return;
        }
        MessageStatusEvent e = new MessageStatusEvent();
        e.setMessageId(m == null ? null : m.getId());
        e.setWamid(wamid);
        e.setStatus(status);
        JsonNode err = st.path("errors").path(0);
        if (!err.isMissingNode()) {
            e.setErrorCode(err.hasNonNull("code") ? err.get("code").asInt() : null);
            e.setErrorTitle(trim(err.path("title").asText(err.path("message").asText(null))));
        }
        if (st.has("pricing")) {
            e.setPricing(st.get("pricing").toString());
        }
        e.setEventTimestamp(epoch(st.path("timestamp").asText(null)));
        e.setRaw(st.toString());
        e.setCreatedAt(Instant.now());
        eventRepository.save(e);
        if (m == null) {
            // The send response has not stored the wamid yet; replayPending() applies it later.
            log.debug("Status {} for unknown wamid {} stored for replay", status, wamid);
            return;
        }
        apply(m, e);
    }

    /** Called after the worker stored the wamid: applies statuses that arrived earlier. */
    @Transactional
    public void replayPending(Message lockedMessage) {
        List<MessageStatusEvent> pending = eventRepository.findByWamidAndMessageIdIsNullOrderByIdAsc(lockedMessage.getWamid());
        for (MessageStatusEvent e : pending) {
            e.setMessageId(lockedMessage.getId());
            eventRepository.save(e);
            apply(lockedMessage, e);
        }
    }

    private void apply(Message m, MessageStatusEvent e) {
        MessageStatus old = m.getStatus();
        ChargeStatus oldCharge = m.getChargeStatus();
        MessageStatus incoming = switch (e.getStatus()) {
            case "sent" -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            case "failed" -> MessageStatus.FAILED;
            case "deleted" -> MessageStatus.DELETED;
            default -> null;
        };
        if (incoming == null) {
            return;
        }
        if (e.getPricing() != null) {
            JsonNode pr = JsonUtil.readTree(e.getPricing());
            if (pr.has("billable")) {
                m.setBillable(pr.get("billable").asBoolean());
            }
            if (pr.hasNonNull("category")) {
                m.setPricingCategory(pr.get("category").asText().toUpperCase());
            }
            if (pr.hasNonNull("type")) {
                m.setPricingType(pr.get("type").asText().toUpperCase());
            } else if (pr.hasNonNull("pricing_model")) {
                m.setPricingType(pr.get("pricing_model").asText().toUpperCase());
            }
        }
        Instant at = e.getEventTimestamp() != null ? e.getEventTimestamp() : Instant.now();
        if (incoming == MessageStatus.FAILED) {
            if (old != MessageStatus.READ && old != MessageStatus.FAILED) {
                m.setStatus(MessageStatus.FAILED);
                m.setFailedAt(at);
                m.setErrorCode(e.getErrorCode());
                m.setErrorTitle(e.getErrorTitle());
                m.setErrorMessage(trim(MetaErrorMapper.friendly(e.getErrorCode(), e.getErrorTitle(), null)));
            }
        } else if (incoming == MessageStatus.DELETED) {
            m.setStatus(MessageStatus.DELETED);
        } else if (old != MessageStatus.FAILED && old != MessageStatus.DELETED
                && ChargingService.rank(incoming) > ChargingService.rank(old)) {
            m.setStatus(incoming);
        }
        // Timestamps fill in even if statuses arrive out of order
        if (incoming == MessageStatus.SENT || incoming == MessageStatus.DELIVERED || incoming == MessageStatus.READ) {
            if (m.getSentAt() == null) {
                m.setSentAt(at);
            }
        }
        if ((incoming == MessageStatus.DELIVERED || incoming == MessageStatus.READ) && m.getDeliveredAt() == null) {
            m.setDeliveredAt(at);
        }
        if (incoming == MessageStatus.READ && m.getReadAt() == null) {
            m.setReadAt(at);
        }
        chargingService.onStatus(m);
        messageRepository.save(m);
        if (old != m.getStatus() || oldCharge != m.getChargeStatus()) {
            publish(m, old);
        }
    }

    /** Domain + partner + inbox events for a status change. */
    public void publish(Message m, MessageStatus oldStatus) {
        events.publishEvent(new MessageStatusChangedEvent(m.getId(), m.getPartnerId(), m.getCampaignId(), m.getCampaignRecipientId(),
                oldStatus, m.getStatus(), m.getChargeStatus(), m.getPrice(), m.getErrorMessage()));
        if (oldStatus == m.getStatus()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", m.getUid());
        data.put("wamid", m.getWamid());
        data.put("to", m.getWaId());
        data.put("status", m.getStatus().name().toLowerCase());
        data.put("timestamp", Instant.now().toString());
        if (m.getStatus() == MessageStatus.FAILED) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", m.getErrorCode());
            err.put("message", m.getErrorMessage());
            data.put("error", err);
        } else {
            data.put("error", null);
        }
        data.put("price", MoneyUtil.str(m.getPrice()));
        data.put("chargeStatus", m.getChargeStatus().name());
        data.put("clientReference", m.getClientReference());
        events.publishEvent(new PartnerEvent(m.getPartnerId(), PartnerEvent.MESSAGE_STATUS, data));
        events.publishEvent(new InboxEvent(m.getPartnerId(), InboxEvent.MESSAGE_STATUS,
                Map.of("uid", m.getUid(), "senderId", m.getPhoneNumberId(), "waId", m.getWaId(), "status", m.getStatus().name())));
    }

    static Instant epoch(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(s.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.substring(0, Math.min(255, s.length()));
    }
}
