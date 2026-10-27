package in.codebasket.cbwa.scheduling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.messaging.ConversationWindowRepository;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessagePayloadBuilder;
import in.codebasket.cbwa.messaging.MessageRepository;
import in.codebasket.cbwa.messaging.MessageSource;
import in.codebasket.cbwa.messaging.OutboundCommand;
import in.codebasket.cbwa.messaging.OutboundMessageService;
import in.codebasket.cbwa.messaging.OutboundMessageServiceImpl;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.messaging.lookup.MsgContactRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SC1–SC7: messages (text, photo, video, document, template...) the partner wants sent later. Nothing is
 * held from the wallet until the moment of sending; then the normal send path prices, holds and queues it.
 */
@Slf4j
@Service
public class ScheduledMessageService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final Duration MIN_AHEAD = Duration.ofMinutes(1);
    static final Duration MAX_AHEAD = Duration.ofDays(90);

    private final ScheduledMessageRepository repository;
    private final SenderAccessService senderAccess;
    private final PhoneNumberRepository phoneNumberRepository;
    private final MessagePayloadBuilder payloadBuilder;
    private final OutboundMessageService outbound;
    private final ConversationWindowRepository windowRepository;
    private final MessageRepository messageRepository;
    private final MsgContactRepository contactRepository;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public ScheduledMessageService(ScheduledMessageRepository repository, SenderAccessService senderAccess,
                                   PhoneNumberRepository phoneNumberRepository, MessagePayloadBuilder payloadBuilder,
                                   OutboundMessageService outbound, ConversationWindowRepository windowRepository,
                                   MessageRepository messageRepository, MsgContactRepository contactRepository, ObjectMapper json,
                                   PlatformTransactionManager txManager) {
        this.repository = repository;
        this.senderAccess = senderAccess;
        this.phoneNumberRepository = phoneNumberRepository;
        this.payloadBuilder = payloadBuilder;
        this.outbound = outbound;
        this.windowRepository = windowRepository;
        this.messageRepository = messageRepository;
        this.contactRepository = contactRepository;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    public record ScheduleRequest(@NotNull Instant scheduledAt, @NotNull @Valid SendMessageRequest message) {
    }

    public record RescheduleRequest(@NotNull Instant scheduledAt) {
    }

    public record SenderRef(Long id, String displayPhoneNumber) {
    }

    public record ScheduledMessageDto(String uid, SenderRef sender, String to, String contactName, String type, String preview,
                                      SendMessageRequest request, Instant scheduledAt, ScheduledStatus status, String messageUid,
                                      String error, boolean windowOpenAtSchedule, Instant createdAt, Instant updatedAt) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ScheduledMessageDto> list(Long partnerId, ScheduledStatus status, Long senderId, String waId,
                                                  LocalDate from, LocalDate to, Integer page, Integer size) {
        Specification<ScheduledMessage> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("partnerId"), partnerId));
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (senderId != null) {
                ps.add(cb.equal(root.get("phoneNumberId"), senderId));
            }
            if (waId != null && !waId.isBlank()) {
                String digits = waId.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    ps.add(cb.like(root.get("waId"), "%" + digits + "%"));
                }
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), from.atStartOfDay(IST).toInstant()));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("scheduledAt"), to.plusDays(1).atStartOfDay(IST).toInstant()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Sort sort = status == ScheduledStatus.SCHEDULED ? Sort.by("scheduledAt").ascending() : Sort.by("scheduledAt").descending();
        Page<ScheduledMessage> result = repository.findAll(spec, Paging.of(page, size, null, Set.of(), sort));
        return PageResponse.of(new org.springframework.data.domain.PageImpl<>(toDtos(partnerId, result.getContent()),
                result.getPageable(), result.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public ScheduledMessageDto get(Long partnerId, String uid) {
        return toDtos(partnerId, List.of(find(partnerId, uid))).get(0);
    }

    @Transactional
    public ScheduledMessageDto create(Long partnerId, ScheduleRequest req) {
        ScheduledMessage s = new ScheduledMessage();
        s.setUid(Ulids.next());
        s.setPartnerId(partnerId);
        s.setStatus(ScheduledStatus.SCHEDULED);
        apply(partnerId, s, req);
        return toDtos(partnerId, List.of(repository.save(s))).get(0);
    }

    @Transactional
    public ScheduledMessageDto update(Long partnerId, String uid, ScheduleRequest req) {
        ScheduledMessage s = requireScheduled(find(partnerId, uid));
        apply(partnerId, s, req);
        return toDtos(partnerId, List.of(repository.save(s))).get(0);
    }

    @Transactional
    public ScheduledMessageDto reschedule(Long partnerId, String uid, Instant at) {
        ScheduledMessage s = requireScheduled(find(partnerId, uid));
        s.setScheduledAt(validTime(at));
        return toDtos(partnerId, List.of(repository.save(s))).get(0);
    }

    @Transactional
    public ScheduledMessageDto cancel(Long partnerId, String uid) {
        ScheduledMessage s = requireScheduled(find(partnerId, uid));
        s.setStatus(ScheduledStatus.CANCELLED);
        return toDtos(partnerId, List.of(repository.save(s))).get(0);
    }

    /** SC7: claims the row (SCHEDULED → SENDING) then sends it like the job would. */
    public ScheduledMessageDto sendNow(Long partnerId, String uid) {
        Long id = tx.execute(st -> {
            ScheduledMessage s = requireScheduled(repository.lockByUidAndPartnerId(uid, partnerId)
                    .orElseThrow(() -> AppException.notFound("Scheduled message")));
            s.setStatus(ScheduledStatus.SENDING);
            s.setAttemptedAt(Instant.now());
            return s.getId();
        });
        dispatch(id);
        return tx.execute(st -> get(partnerId, uid));
    }

    /** Used by the job: claims due rows with SKIP LOCKED so two app instances never send one twice. */
    public List<Long> claimDue(int limit) {
        List<Long> ids = tx.execute(st -> {
            List<Long> due = repository.lockDueIds(Instant.now(), limit);
            if (!due.isEmpty()) {
                repository.markSending(due, Instant.now());
            }
            return due;
        });
        return ids == null ? List.of() : ids;
    }

    /** Sends one SENDING row through the normal outbound path; failures are recorded on the row. */
    public void dispatch(Long id) {
        try {
            tx.executeWithoutResult(st -> {
                ScheduledMessage s = repository.findById(id).orElseThrow();
                if (s.getStatus() != ScheduledStatus.SENDING) {
                    return;
                }
                SendMessageRequest req = read(s.getRequest());
                Message m = outbound.enqueue(OutboundCommand.of(s.getPartnerId(), s.getPhoneNumberId(), s.getWaId(), req,
                        MessageSource.SCHEDULED));
                s.setStatus(ScheduledStatus.SENT);
                s.setMessageId(m.getId());
                s.setError(null);
            });
        } catch (RuntimeException e) {
            String msg = e instanceof AppException ae
                    ? (ae.getCode() == ErrorCode.WINDOW_CLOSED
                    ? "The customer's 24-hour window was closed at send time. Use a template message" : ae.getMessage())
                    : "Could not send: " + e.getMessage();
            log.info("Scheduled message {} failed: {}", id, msg);
            tx.executeWithoutResult(st -> repository.findById(id).ifPresent(s -> {
                s.setStatus(ScheduledStatus.FAILED);
                s.setError(msg.length() > 500 ? msg.substring(0, 500) : msg);
            }));
        }
    }

    /** Rows stuck in SENDING (app stopped mid-send) are failed rather than risk a double send. */
    @Transactional
    public int failStuck() {
        return repository.failStuck(Instant.now().minus(Duration.ofMinutes(10)),
                "Sending was interrupted. Please check the chat and schedule again if needed");
    }

    private void apply(Long partnerId, ScheduledMessage s, ScheduleRequest req) {
        SendMessageRequest m = req.message();
        if (m.senderId() == null) {
            throw AppException.field("message.senderId", "Choose a sender number");
        }
        if (m.to() == null || m.to().isBlank()) {
            throw AppException.field("message.to", "Enter the recipient's WhatsApp number");
        }
        if (m.type() == null || m.type().isBlank()) {
            throw AppException.field("message.type", "Choose the message type");
        }
        if ("reaction".equals(m.type())) {
            throw AppException.field("message.type", "Reactions cannot be scheduled");
        }
        PhoneNumber sender = senderAccess.requireUsableSender(partnerId, m.senderId());
        String to = OutboundMessageServiceImpl.normaliseTo(m.to());
        SendMessageRequest normalised = new SendMessageRequest(sender.getId(), null, to, m.type(), null, null, m.text(), m.media(),
                m.location(), m.contacts(), null, m.interactive(), m.template(), m.clientReference());
        // Validates the content now (template approved, variables, media present) so mistakes show up while scheduling.
        MessagePayloadBuilder.Built built = payloadBuilder.build(partnerId, sender, to, normalised);
        s.setPhoneNumberId(sender.getId());
        s.setWaId(to);
        s.setType(m.type());
        s.setRequest(write(normalised));
        s.setPreview(built.preview() == null ? null : built.preview().length() > 500 ? built.preview().substring(0, 500) : built.preview());
        s.setScheduledAt(validTime(req.scheduledAt()));
        s.setError(null);
    }

    private static Instant validTime(Instant at) {
        Instant now = Instant.now();
        if (at == null || at.isBefore(now.plus(MIN_AHEAD).minusSeconds(5))) {
            throw AppException.field("scheduledAt", "Choose a time at least 1 minute from now");
        }
        if (at.isAfter(now.plus(MAX_AHEAD))) {
            throw AppException.field("scheduledAt", "You can schedule up to 90 days ahead");
        }
        return at;
    }

    private ScheduledMessage find(Long partnerId, String uid) {
        return repository.findByUidAndPartnerId(uid, partnerId).orElseThrow(() -> AppException.notFound("Scheduled message"));
    }

    private static ScheduledMessage requireScheduled(ScheduledMessage s) {
        if (s.getStatus() != ScheduledStatus.SCHEDULED) {
            throw AppException.conflict("Only messages that are still scheduled can be changed");
        }
        return s;
    }

    private List<ScheduledMessageDto> toDtos(Long partnerId, List<ScheduledMessage> rows) {
        Map<Long, PhoneNumber> senders = phoneNumberRepository.findAllById(rows.stream().map(ScheduledMessage::getPhoneNumberId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(PhoneNumber::getId, Function.identity()));
        Map<Long, String> messageUids = messageRepository.findAllById(rows.stream().map(ScheduledMessage::getMessageId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Message::getId, Message::getUid));
        return rows.stream().map(s -> {
            PhoneNumber pn = senders.get(s.getPhoneNumberId());
            String name = contactRepository.findByPartnerIdAndPhone(partnerId, s.getWaId()).map(c -> c.getName()).orElse(null);
            boolean windowOpen = !"template".equals(s.getType()) ? windowRepository.findByPhoneNumberIdAndWaId(s.getPhoneNumberId(), s.getWaId())
                    .map(w -> w.getExpiresAt() != null && w.getExpiresAt().isAfter(s.getScheduledAt())).orElse(false) : true;
            return new ScheduledMessageDto(s.getUid(), pn == null ? null : new SenderRef(pn.getId(), pn.getDisplayPhoneNumber()), s.getWaId(),
                    name, s.getType(), s.getPreview(), read(s.getRequest()), s.getScheduledAt(), s.getStatus(),
                    s.getMessageId() == null ? null : messageUids.get(s.getMessageId()), s.getError(), windowOpen,
                    s.getCreatedAt(), s.getUpdatedAt());
        }).toList();
    }

    private String write(SendMessageRequest r) {
        try {
            return json.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private SendMessageRequest read(String s) {
        try {
            return json.readValue(s, SendMessageRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
