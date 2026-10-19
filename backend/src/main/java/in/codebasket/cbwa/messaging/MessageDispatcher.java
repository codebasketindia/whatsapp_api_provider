package in.codebasket.cbwa.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaErrorMapper;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.sender.SenderStatus;
import in.codebasket.cbwa.waba.TokenStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

/**
 * Sends one claimed queue item to Meta. The Meta call happens outside any transaction; the result
 * is then recorded in a short transaction with the message row locked.
 */
@Slf4j
@Component
public class MessageDispatcher {

    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final MessageQueueItemRepository queueRepository;
    private final MessageRepository messageRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final PartnerRepository partnerRepository;
    private final SenderAccessService senderAccess;
    private final MetaGraphClient graph;
    private final FileStorageService storage;
    private final ChargingService chargingService;
    private final MessageStatusService statusService;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public MessageDispatcher(MessageQueueItemRepository queueRepository, MessageRepository messageRepository,
                             PhoneNumberRepository phoneNumberRepository, WhatsappAccountRepository wabaRepository,
                             PartnerRepository partnerRepository, SenderAccessService senderAccess, MetaGraphClient graph,
                             FileStorageService storage, ChargingService chargingService, MessageStatusService statusService,
                             NotificationService notifications, PlatformTransactionManager txManager) {
        this.queueRepository = queueRepository;
        this.messageRepository = messageRepository;
        this.phoneNumberRepository = phoneNumberRepository;
        this.wabaRepository = wabaRepository;
        this.partnerRepository = partnerRepository;
        this.senderAccess = senderAccess;
        this.graph = graph;
        this.storage = storage;
        this.chargingService = chargingService;
        this.statusService = statusService;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
    }

    public void dispatch(Long queueId) {
        MessageQueueItem q = queueRepository.findById(queueId).orElse(null);
        if (q == null || q.getStatus() != QueueStatus.PROCESSING) {
            return;
        }
        Message m = messageRepository.findById(q.getMessageId()).orElse(null);
        if (m == null || m.getStatus() != MessageStatus.QUEUED) {
            tx.executeWithoutResult(s -> markDone(queueId));
            return;
        }
        PhoneNumber pn = phoneNumberRepository.findById(m.getPhoneNumberId()).orElse(null);
        if (pn == null || pn.getStatus() != SenderStatus.CONNECTED || pn.isPaused()) {
            String why = pn == null || pn.getStatus() != SenderStatus.CONNECTED ? "Sender number is not connected" : "Sender number is paused";
            tx.executeWithoutResult(s -> fail(queueId, m.getId(), null, why, null, false, null));
            return;
        }
        ObjectNode payload = (ObjectNode) JsonUtil.readTree(m.getPayload());
        String uploadedPayload = null;
        try {
            String token = senderAccess.accessToken(pn);
            if (resolveFiles(payload, pn, token)) {
                uploadedPayload = payload.toString();
            }
            JsonNode res = graph.post("/" + pn.getPhoneNumberId() + "/messages", payload, token);
            String wamid = res.path("messages").path(0).path("id").asText(null);
            if (wamid == null) {
                throw new MetaApiException(200, null, null, "WhatsApp did not return a message id", null, null);
            }
            String finalPayload = uploadedPayload;
            tx.executeWithoutResult(s -> succeed(queueId, m.getId(), wamid, finalPayload));
        } catch (MetaApiException e) {
            String finalPayload = uploadedPayload;
            tx.executeWithoutResult(s -> fail(queueId, m.getId(), e.getCode(), e.friendlyMessage(),
                    e.getMessage(), e.isRetryable(), finalPayload));
            if (e.isTokenInvalid()) {
                markTokenInvalid(pn);
            }
        } catch (AppException e) {
            tx.executeWithoutResult(s -> fail(queueId, m.getId(), null, e.getMessage(), null, false, null));
        } catch (RuntimeException e) {
            log.error("Unexpected error sending message {}", m.getUid(), e);
            tx.executeWithoutResult(s -> fail(queueId, m.getId(), null, "Temporary error - will retry", e.getMessage(), true, null));
        }
    }

    /** Uploads {"_fileUid": ...} media objects to Meta and replaces them with {"id": ...}. */
    private boolean resolveFiles(JsonNode node, PhoneNumber pn, String token) {
        boolean changed = false;
        if (node instanceof ObjectNode obj) {
            if (obj.has(MessagePayloadBuilder.FILE_UID_KEY)) {
                StoredFile f = storage.get(obj.get(MessagePayloadBuilder.FILE_UID_KEY).asText());
                String mediaId = graph.uploadMedia(pn.getPhoneNumberId(), storage.readBytes(f), f.getMimeType(), f.getOriginalName(), token);
                obj.remove(MessagePayloadBuilder.FILE_UID_KEY);
                obj.put("id", mediaId);
                return true;
            }
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                changed |= resolveFiles(it.next().getValue(), pn, token);
            }
        } else if (node != null && node.isArray()) {
            for (JsonNode child : node) {
                changed |= resolveFiles(child, pn, token);
            }
        }
        return changed;
    }

    private void succeed(Long queueId, Long messageId, String wamid, String newPayload) {
        Message m = messageRepository.lockById(messageId).orElseThrow();
        MessageStatus old = m.getStatus();
        m.setWamid(wamid);
        if (newPayload != null) {
            m.setPayload(newPayload);
        }
        if (m.getStatus() == MessageStatus.QUEUED) {
            m.setStatus(MessageStatus.ACCEPTED);
        }
        messageRepository.save(m);
        markDone(queueId);
        statusService.publish(m, old);
        statusService.replayPending(m);
    }

    private void fail(Long queueId, Long messageId, Integer code, String friendly, String raw, boolean retryable, String newPayload) {
        MessageQueueItem q = queueRepository.findById(queueId).orElseThrow();
        Message m = messageRepository.lockById(messageId).orElseThrow();
        if (newPayload != null) {
            m.setPayload(newPayload);
        }
        int attempts = q.getAttempts() + 1;
        q.setAttempts(attempts);
        q.setLastError(trim(raw != null ? raw : friendly, 500));
        q.setLockedBy(null);
        q.setLockedAt(null);
        if (retryable && attempts < q.getMaxAttempts()) {
            long seconds = Math.min(MAX_BACKOFF.toSeconds(), (1L << attempts) * 5L);
            q.setStatus(QueueStatus.PENDING);
            q.setNextAttemptAt(Instant.now().plusSeconds(seconds));
            queueRepository.save(q);
            messageRepository.save(m);
            return;
        }
        q.setStatus(QueueStatus.DEAD);
        queueRepository.save(q);
        MessageStatus old = m.getStatus();
        m.setStatus(MessageStatus.FAILED);
        m.setFailedAt(Instant.now());
        m.setErrorCode(code);
        m.setErrorTitle(trim(raw, 255));
        m.setErrorMessage(trim(friendly != null ? friendly : MetaErrorMapper.friendly(code, raw, null), 255));
        chargingService.release(m, "Released - message not sent");
        messageRepository.save(m);
        statusService.publish(m, old);
    }

    private void markDone(Long queueId) {
        queueRepository.findById(queueId).ifPresent(q -> {
            q.setStatus(QueueStatus.DONE);
            q.setLockedBy(null);
            q.setLockedAt(null);
            queueRepository.save(q);
        });
    }

    private void markTokenInvalid(PhoneNumber pn) {
        try {
            tx.executeWithoutResult(s -> {
                WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
                if (waba.getTokenStatus() == TokenStatus.INVALID) {
                    return;
                }
                waba.setTokenStatus(TokenStatus.INVALID);
                waba.setTokenCheckedAt(Instant.now());
                wabaRepository.save(waba);
                String title = "WhatsApp access expired";
                String body = "Messages from " + pn.getDisplayPhoneNumber() + " cannot be sent because WhatsApp access expired. Please reconnect the number.";
                senderAccess.partnerOf(pn.getId()).ifPresent(pid -> notifications.notifyPartner(pid, "TOKEN_INVALID",
                        NotificationService.ERROR, title, body, "/partner/senders/" + pn.getId(),
                        partnerRepository.findById(pid).map(p -> p.getEmail()).orElse(null)));
                notifications.notifyAdmins("TOKEN_INVALID", NotificationService.ERROR, title, body, "/admin/whatsapp/senders/" + pn.getId());
            });
        } catch (RuntimeException e) {
            log.warn("Could not mark token invalid for WABA {}", pn.getWhatsappAccountId(), e);
        }
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.substring(0, Math.min(max, s.length()));
    }
}
