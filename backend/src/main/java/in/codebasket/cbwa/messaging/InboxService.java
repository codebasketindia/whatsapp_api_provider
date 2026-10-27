package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.contact.Contact;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MessageDto;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.messaging.lookup.MsgContactRepository;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.SenderAccessService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** IN1-IN4: conversation list, conversation messages, reply and mark-as-read. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

    private final ConversationWindowRepository windowRepository;
    private final MessageRepository messageRepository;
    private final MessageMapper mapper;
    private final MsgContactRepository contactRepository;
    private final SenderAccessService senderAccess;
    private final PartnerMessagingService messagingService;
    private final MetaGraphClient graph;

    public record ContactRef(Long id, String name) {
    }

    public record ConversationDto(Long senderId, String waId, ContactRef contact, String contactName, String lastMessagePreview,
                                  Instant lastMessageAt, int unreadCount, boolean windowOpen, Instant windowExpiresAt,
                                  boolean aiPaused, boolean needsHuman) {
    }

    public record ConversationMessages(List<MessageDto> items, boolean hasMore, boolean windowOpen, Instant windowExpiresAt) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationDto> conversations(Long partnerId, Long senderId, String search, boolean unreadOnly,
                                                       Integer page, Integer size) {
        Specification<ConversationWindow> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("partnerId"), partnerId));
            if (senderId != null) {
                ps.add(cb.equal(root.get("phoneNumberId"), senderId));
            }
            if (unreadOnly) {
                ps.add(cb.greaterThan(root.get("unreadCount"), 0));
            }
            if (search != null && !search.isBlank()) {
                String s = search.trim().toLowerCase();
                String digits = s.replaceAll("[^0-9]", "");
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("contactName")), "%" + s + "%"));
                if (!digits.isEmpty()) {
                    or.add(cb.like(root.get("waId"), "%" + digits + "%"));
                }
                ps.add(cb.or(or.toArray(Predicate[]::new)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<ConversationWindow> result = windowRepository.findAll(spec,
                Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "lastMessageAt")));
        Map<String, Contact> contacts = new java.util.HashMap<>();
        result.forEach(w -> contactRepository.findByPartnerIdAndPhone(partnerId, w.getWaId()).ifPresent(c -> contacts.put(w.getWaId(), c)));
        Instant now = Instant.now();
        return PageResponse.of(result, w -> {
            Contact c = contacts.get(w.getWaId());
            boolean open = w.getExpiresAt() != null && w.getExpiresAt().isAfter(now);
            return new ConversationDto(w.getPhoneNumberId(), w.getWaId(), c == null ? null : new ContactRef(c.getId(), c.getName()),
                    c != null && c.getName() != null ? c.getName() : w.getContactName(), w.getLastMessagePreview(), w.getLastMessageAt(),
                    w.getUnreadCount(), open, open ? w.getExpiresAt() : null, w.isAiPaused(), w.isNeedsHuman());
        });
    }

    @Transactional(readOnly = true)
    public ConversationMessages messages(Long partnerId, Long senderId, String waId, String beforeUid, Integer size) {
        senderAccess.requirePartnerSender(partnerId, senderId);
        int n = size == null || size < 1 ? 30 : Math.min(size, 100);
        Long beforeId = null;
        if (beforeUid != null && !beforeUid.isBlank()) {
            beforeId = messageRepository.findByUidAndPartnerId(beforeUid, partnerId).map(Message::getId)
                    .orElseThrow(() -> AppException.notFound("Message"));
        }
        List<Message> page = messageRepository.conversation(partnerId, senderId, waId, beforeId, PageRequest.of(0, n + 1));
        boolean hasMore = page.size() > n;
        List<Message> items = new ArrayList<>(hasMore ? page.subList(0, n) : page);
        Collections.reverse(items);
        var w = windowRepository.findByPhoneNumberIdAndWaId(senderId, waId).orElse(null);
        boolean open = w != null && w.getExpiresAt() != null && w.getExpiresAt().isAfter(Instant.now());
        return new ConversationMessages(mapper.toDtos(items, false), hasMore, open, open ? w.getExpiresAt() : null);
    }

    @Transactional
    public MessageDto reply(Long partnerId, Long senderId, String waId, SendMessageRequest req) {
        SendMessageRequest r = new SendMessageRequest(senderId, null, waId, req.type(), req.replyToUid(), req.replyToWamid(),
                req.text(), req.media(), req.location(), req.contacts(), req.reaction(), req.interactive(), req.template(),
                req.clientReference());
        MessageDto sent = messagingService.send(partnerId, r, MessageSource.INBOX);
        windowRepository.findByPhoneNumberIdAndWaId(senderId, waId).filter(ConversationWindow::isNeedsHuman).ifPresent(w -> {
            w.setNeedsHuman(false);
            windowRepository.save(w);
        });
        return sent;
    }

    /** Not transactional on purpose: the Meta call must not run while the window row is locked. */
    public void markRead(Long partnerId, Long senderId, String waId, boolean typing) {
        PhoneNumber pn = senderAccess.requirePartnerSender(partnerId, senderId);
        windowRepository.findByPhoneNumberIdAndWaId(senderId, waId).ifPresent(w -> {
            if (w.getUnreadCount() != 0) {
                w.setUnreadCount(0);
                windowRepository.save(w);
            }
        });
        messageRepository.findFirstByPhoneNumberIdAndWaIdAndDirectionOrderByIdDesc(senderId, waId, MessageDirection.INBOUND)
                .filter(m -> m.getWamid() != null)
                .ifPresent(m -> {
                    var body = new java.util.LinkedHashMap<String, Object>();
                    body.put("messaging_product", "whatsapp");
                    body.put("status", "read");
                    body.put("message_id", m.getWamid());
                    if (typing) {
                        body.put("typing_indicator", Map.of("type", "text"));
                    }
                    try {
                        graph.post("/" + pn.getPhoneNumberId() + "/messages", body, senderAccess.accessToken(pn));
                    } catch (MetaApiException e) {
                        // Read receipts are best effort; the inbox unread counter is already cleared.
                        log.info("Mark-as-read failed for {}: {}", m.getUid(), e.getMessage());
                    }
                });
    }

    static <T> Map<Long, T> byId(List<T> list, Function<T, Long> id) {
        return list.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
