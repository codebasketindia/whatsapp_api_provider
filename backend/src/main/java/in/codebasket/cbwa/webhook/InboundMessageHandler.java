package in.codebasket.cbwa.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.contact.Contact;
import in.codebasket.cbwa.contact.ContactSource;
import in.codebasket.cbwa.contact.OptOut;
import in.codebasket.cbwa.contact.OptOutSource;
import in.codebasket.cbwa.messaging.ChargeStatus;
import in.codebasket.cbwa.messaging.ConversationWindow;
import in.codebasket.cbwa.messaging.ConversationWindowRepository;
import in.codebasket.cbwa.messaging.InboundMedia;
import in.codebasket.cbwa.messaging.InboundMediaRepository;
import in.codebasket.cbwa.messaging.InboxEvent;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageDirection;
import in.codebasket.cbwa.messaging.MessageRepository;
import in.codebasket.cbwa.messaging.MessageSource;
import in.codebasket.cbwa.messaging.MessageStatus;
import in.codebasket.cbwa.messaging.MessageStatusService;
import in.codebasket.cbwa.messaging.MessageType;
import in.codebasket.cbwa.messaging.lookup.MsgContactRepository;
import in.codebasket.cbwa.messaging.lookup.MsgOptOutRepository;
import in.codebasket.cbwa.partner.PartnerEvent;
import in.codebasket.cbwa.partner.PartnerSettings;
import in.codebasket.cbwa.partner.PartnerSettingsRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The "messages" webhook field: inbound messages, status updates, echoes and opt-out keywords. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboundMessageHandler {

    private static final Set<String> MEDIA_TYPES = Set.of("image", "video", "audio", "document", "sticker");

    private final MessageRepository messageRepository;
    private final ConversationWindowRepository windowRepository;
    private final InboundMediaRepository mediaRepository;
    private final MsgContactRepository contactRepository;
    private final MsgOptOutRepository optOutRepository;
    private final PartnerSettingsRepository partnerSettingsRepository;
    private final MessageStatusService statusService;
    private final AutoReplyService autoReplyService;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.MANDATORY)
    public void handleStatus(JsonNode st, Long partnerId) {
        statusService.applyWebhookStatus(st);
        // Meta: 131050 = user stopped marketing messages from this business -> record an opt-out
        if ("failed".equalsIgnoreCase(st.path("status").asText()) && partnerId != null) {
            for (JsonNode err : st.path("errors")) {
                if (err.path("code").asInt() == 131050) {
                    String phone = st.path("recipient_id").asText(null);
                    if (phone != null) {
                        optOut(partnerId, phone, null, OptOutSource.META_131050, null);
                    }
                }
            }
        }
    }

    /**
     * Stores one inbound message (deduplicated by wamid), opens/extends the 24h window, upserts the
     * contact and handles STOP/START keywords.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean handleInbound(PhoneNumber pn, Long partnerId, JsonNode msg, String profileName) {
        String wamid = msg.path("id").asText(null);
        String from = msg.path("from").asText(null);
        if (wamid == null || from == null || messageRepository.existsByWamid(wamid)) {
            return false;
        }
        String type = msg.path("type").asText("unknown");
        Instant ts = epochOrNow(msg.path("timestamp").asText(null));
        String preview = preview(msg, type);

        Contact contact = upsertContact(partnerId, from, profileName, ts);

        Message m = new Message();
        m.setUid(Ulids.next());
        m.setPartnerId(partnerId);
        m.setPhoneNumberId(pn.getId());
        m.setDirection(MessageDirection.INBOUND);
        m.setWaId(from);
        String region = PhoneUtil.regionOfDigits(from);
        m.setCountryCode("ZZ".equals(region) ? null : region);
        m.setContactId(contact.getId());
        m.setType(mapType(type));
        m.setPayload(msg.toString());
        m.setPreview(preview);
        m.setContextWamid(msg.path("context").path("id").asText(null));
        m.setWamid(wamid);
        m.setStatus(MessageStatus.RECEIVED);
        m.setPrice(MoneyUtil.ZERO);
        m.setChargeStatus(ChargeStatus.NONE);
        m.setSource(MessageSource.INBOX);
        if (msg.has("errors")) {
            JsonNode e = msg.path("errors").path(0);
            m.setErrorCode(e.hasNonNull("code") ? e.get("code").asInt() : null);
            m.setErrorTitle(trim(e.path("title").asText(null), 255));
        }
        messageRepository.save(m);

        if (MEDIA_TYPES.contains(type)) {
            JsonNode md = msg.path(type);
            InboundMedia im = new InboundMedia();
            im.setMessageId(m.getId());
            im.setPartnerId(partnerId);
            im.setMediaId(md.path("id").asText());
            im.setMimeType(md.path("mime_type").asText(null));
            im.setSha256(md.path("sha256").asText(null));
            im.setCaption(trim(md.path("caption").asText(null), 1024));
            im.setFilename(trim(md.path("filename").asText(null), 255));
            im.setDownloadStatus("PENDING");
            im.setAttempts(0);
            mediaRepository.save(im);
        }

        ConversationWindow w = windowRepository.findByPhoneNumberIdAndWaId(pn.getId(), from).orElseGet(() -> {
            ConversationWindow n = new ConversationWindow();
            n.setPartnerId(partnerId);
            n.setPhoneNumberId(pn.getId());
            n.setWaId(from);
            n.setUnreadCount(0);
            return n;
        });
        w.setPartnerId(partnerId);
        if (w.getLastInboundAt() == null || ts.isAfter(w.getLastInboundAt())) {
            w.setLastInboundAt(ts);
            w.setExpiresAt(ts.plus(Duration.ofHours(24)));
        }
        if (profileName != null) {
            w.setContactName(trim(profileName, 120));
        }
        w.setUnreadCount(w.getUnreadCount() + 1);
        w.setLastMessagePreview(trim(preview, 255));
        w.setLastMessageAt(Instant.now());
        windowRepository.save(w);

        if ("text".equals(type) || "button".equals(type)) {
            String text = "text".equals(type) ? msg.path("text").path("body").asText("") : msg.path("button").path("text").asText("");
            handleKeywords(partnerId, pn, from, contact, text.trim());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", m.getUid());
        data.put("wamid", wamid);
        data.put("from", from);
        data.put("senderId", pn.getId());
        data.put("senderPhoneNumber", pn.getWaNumber());
        data.put("profileName", profileName);
        data.put("type", type);
        data.put("text", preview);
        data.put("message", msg);
        data.put("contextId", m.getContextWamid());
        data.put("timestamp", ts.toString());
        events.publishEvent(new PartnerEvent(partnerId, PartnerEvent.MESSAGE_INBOUND, data));
        events.publishEvent(new InboxEvent(partnerId, InboxEvent.MESSAGE_NEW,
                Map.of("uid", m.getUid(), "senderId", pn.getId(), "waId", from, "direction", "INBOUND")));
        return true;
    }

    /** Messages sent from the WhatsApp Business App on a coexistence number (smb_message_echoes / history). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean storeEcho(PhoneNumber pn, Long partnerId, JsonNode msg, boolean outbound, MessageStatus status) {
        String wamid = msg.path("id").asText(null);
        if (wamid == null || messageRepository.existsByWamid(wamid)) {
            return false;
        }
        String type = msg.path("type").asText("unknown");
        String other = outbound ? msg.path("to").asText(null) : msg.path("from").asText(null);
        if (other == null) {
            return false;
        }
        Instant ts = epochOrNow(msg.path("timestamp").asText(null));
        Message m = new Message();
        m.setUid(Ulids.next());
        m.setPartnerId(partnerId);
        m.setPhoneNumberId(pn.getId());
        m.setDirection(outbound ? MessageDirection.OUTBOUND : MessageDirection.INBOUND);
        m.setWaId(other);
        m.setType(mapType(type));
        m.setPayload(msg.toString());
        m.setPreview(preview(msg, type));
        m.setWamid(wamid);
        m.setStatus(outbound ? status : MessageStatus.RECEIVED);
        m.setPrice(MoneyUtil.ZERO);
        m.setChargeStatus(ChargeStatus.NONE);
        m.setSource(MessageSource.SYSTEM);
        if (outbound) {
            m.setSentAt(ts);
        }
        messageRepository.save(m);
        return true;
    }

    private void handleKeywords(Long partnerId, PhoneNumber pn, String from, Contact contact, String text) {
        if (text.isEmpty() || text.length() > 40) {
            return;
        }
        PartnerSettings ps = partnerSettingsRepository.findById(partnerId).orElse(null);
        List<String> stop = ps == null || ps.getOptOutKeywords() == null ? List.of("STOP", "UNSUBSCRIBE") : ps.getOptOutKeywords();
        List<String> start = ps == null || ps.getOptInKeywords() == null ? List.of("START") : ps.getOptInKeywords();
        if (stop.stream().anyMatch(k -> k.equalsIgnoreCase(text))) {
            boolean added = optOut(partnerId, from, pn.getId(), OptOutSource.KEYWORD, text.toUpperCase());
            if (added && ps != null && ps.getOptOutAutoReply() != null && !ps.getOptOutAutoReply().isBlank()) {
                String reply = ps.getOptOutAutoReply();
                NotificationService.afterCommit(() -> autoReplyService.sendText(partnerId, pn.getId(), from, reply));
            }
        } else if (start.stream().anyMatch(k -> k.equalsIgnoreCase(text))) {
            optOutRepository.findByPartnerIdAndPhone(partnerId, from).ifPresent(optOutRepository::delete);
            if (contact.isOptedOut()) {
                contact.setOptedOut(false);
                contactRepository.save(contact);
            }
        }
    }

    /** Returns true when a new opt-out row was created. */
    private boolean optOut(Long partnerId, String phone, Long phoneNumberId, OptOutSource source, String keyword) {
        boolean added = false;
        if (!optOutRepository.existsByPartnerIdAndPhone(partnerId, phone)) {
            OptOut o = new OptOut();
            o.setPartnerId(partnerId);
            o.setPhone(phone);
            o.setPhoneNumberId(phoneNumberId);
            o.setSource(source);
            o.setKeyword(keyword);
            o.setCreatedAt(Instant.now());
            optOutRepository.save(o);
            added = true;
        }
        contactRepository.findByPartnerIdAndPhone(partnerId, phone).ifPresent(c -> {
            if (!c.isOptedOut()) {
                c.setOptedOut(true);
                contactRepository.save(c);
            }
        });
        return added;
    }

    private Contact upsertContact(Long partnerId, String phone, String name, Instant ts) {
        Contact c = contactRepository.findByPartnerIdAndPhone(partnerId, phone).orElseGet(() -> {
            Contact n = new Contact();
            n.setPartnerId(partnerId);
            n.setPhone(phone);
            String region = PhoneUtil.regionOfDigits(phone);
            n.setCountryCode("ZZ".equals(region) ? null : region);
            n.setSource(ContactSource.INBOUND);
            n.setOptedOut(optOutRepository.existsByPartnerIdAndPhone(partnerId, phone));
            return n;
        });
        if ((c.getName() == null || c.getName().isBlank()) && name != null) {
            c.setName(trim(name, 120));
        }
        c.setLastInboundAt(ts);
        return contactRepository.save(c);
    }

    static MessageType mapType(String type) {
        return switch (type) {
            case "text" -> MessageType.TEXT;
            case "image" -> MessageType.IMAGE;
            case "video" -> MessageType.VIDEO;
            case "audio" -> MessageType.AUDIO;
            case "document" -> MessageType.DOCUMENT;
            case "sticker" -> MessageType.STICKER;
            case "location" -> MessageType.LOCATION;
            case "contacts" -> MessageType.CONTACTS;
            case "reaction" -> MessageType.REACTION;
            case "interactive" -> MessageType.INTERACTIVE;
            case "button" -> MessageType.BUTTON;
            case "order" -> MessageType.ORDER;
            case "system" -> MessageType.SYSTEM;
            case "template" -> MessageType.TEMPLATE;
            default -> MessageType.UNSUPPORTED;
        };
    }

    static String preview(JsonNode msg, String type) {
        String p = switch (type) {
            case "text" -> msg.path("text").path("body").asText("");
            case "image", "video", "document", "audio", "sticker" -> {
                String cap = msg.path(type).path("caption").asText("");
                yield cap.isBlank() ? "[" + type + "]" : cap;
            }
            case "location" -> "[location] " + msg.path("location").path("name").asText(
                    msg.path("location").path("latitude").asText() + "," + msg.path("location").path("longitude").asText());
            case "contacts" -> "[contact] " + msg.path("contacts").path(0).path("name").path("formatted_name").asText("");
            case "reaction" -> msg.path("reaction").path("emoji").asText("[reaction]");
            case "interactive" -> {
                JsonNode i = msg.path("interactive");
                if (i.has("button_reply")) {
                    yield i.path("button_reply").path("title").asText();
                } else if (i.has("list_reply")) {
                    yield i.path("list_reply").path("title").asText();
                } else if (i.has("nfm_reply")) {
                    yield "[form reply]";
                }
                yield "[interactive]";
            }
            case "button" -> msg.path("button").path("text").asText("[button]");
            case "order" -> "[order]";
            case "system" -> msg.path("system").path("body").asText("[system]");
            case "template" -> "[template] " + msg.path("template").path("name").asText("");
            default -> "[unsupported message]";
        };
        return trim(p, 1000);
    }

    static Instant epochOrNow(String s) {
        try {
            return s == null ? Instant.now() : Instant.ofEpochSecond(Long.parseLong(s.trim()));
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }

    static String trim(String s, int max) {
        return s == null ? null : s.substring(0, Math.min(max, s.length()));
    }
}
