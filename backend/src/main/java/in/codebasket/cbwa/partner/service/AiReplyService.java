package in.codebasket.cbwa.partner.service;

import in.codebasket.cbwa.common.entity.AiKnowledgeItem;
import in.codebasket.cbwa.common.entity.AiUsageLog;

import in.codebasket.cbwa.common.constants.AiOutcome;
import in.codebasket.cbwa.common.entity.AiAssistant;
import in.codebasket.cbwa.common.repository.AiAssistantRepository;
import in.codebasket.cbwa.common.repository.AiKnowledgeItemRepository;
import in.codebasket.cbwa.common.repository.AiUsageLogRepository;
import in.codebasket.cbwa.common.service.ai.AiModelClient;
import in.codebasket.cbwa.common.service.ai.AiService;

import in.codebasket.cbwa.common.constants.SettingKeys;
import in.codebasket.cbwa.common.service.settings.SettingsService;
import in.codebasket.cbwa.common.entity.StoredFile;
import in.codebasket.cbwa.common.repository.StoredFileRepository;
import in.codebasket.cbwa.common.repository.ConversationWindowRepository;
import in.codebasket.cbwa.common.dto.InboxEvent;
import in.codebasket.cbwa.common.entity.Message;
import in.codebasket.cbwa.common.constants.MessageDirection;
import in.codebasket.cbwa.common.repository.MessageRepository;
import in.codebasket.cbwa.common.constants.MessageSource;
import in.codebasket.cbwa.common.constants.MessageType;
import in.codebasket.cbwa.common.dto.OutboundCommand;
import in.codebasket.cbwa.common.service.messaging.OutboundMessageService;
import in.codebasket.cbwa.common.dto.SendMessageRequest;
import in.codebasket.cbwa.common.dto.PartnerEvent;
import in.codebasket.cbwa.common.entity.PartnerSettings;
import in.codebasket.cbwa.common.repository.PartnerSettingsRepository;
import in.codebasket.cbwa.common.service.travel.TravelService;
import in.codebasket.cbwa.common.constants.WalletRefType;
import in.codebasket.cbwa.common.service.wallet.WalletService;
import in.codebasket.cbwa.common.entity.WalletTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Answers customer messages with the partner's AI assistant. Runs after the webhook transaction
 * commits, on a background thread, so Meta always gets its 200 immediately. Every decision is written
 * to ai_usage_logs (one row per inbound message, which also stops a message being answered twice).
 */
@Slf4j
@Service
public class AiReplyService {

    private static final Set<MessageType> ANSWERABLE = Set.of(MessageType.TEXT, MessageType.BUTTON, MessageType.INTERACTIVE,
            MessageType.IMAGE, MessageType.VIDEO, MessageType.DOCUMENT);

    private final AiService aiService;
    private final AiAssistantRepository assistantRepository;
    private final AiKnowledgeItemRepository knowledgeRepository;
    private final AiUsageLogRepository usageRepository;
    private final AiModelClient model;
    private final TravelService travel;
    private final in.codebasket.cbwa.common.service.registration.RegistrationService registrations;
    private final SettingsService settings;
    private final MessageRepository messageRepository;
    private final ConversationWindowRepository windowRepository;
    private final PartnerSettingsRepository partnerSettingsRepository;
    private final StoredFileRepository fileRepository;
    private final OutboundMessageService outbound;
    private final WalletService walletService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public AiReplyService(AiService aiService, AiAssistantRepository assistantRepository, AiKnowledgeItemRepository knowledgeRepository,
                          AiUsageLogRepository usageRepository, AiModelClient model, SettingsService settings,
                          MessageRepository messageRepository, ConversationWindowRepository windowRepository,
                          PartnerSettingsRepository partnerSettingsRepository, StoredFileRepository fileRepository,
                          OutboundMessageService outbound, WalletService walletService, ApplicationEventPublisher events,
                          PlatformTransactionManager txManager, TravelService travel,
                          in.codebasket.cbwa.common.service.registration.RegistrationService registrations) {
        this.travel = travel;
        this.registrations = registrations;
        this.aiService = aiService;
        this.assistantRepository = assistantRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.usageRepository = usageRepository;
        this.model = model;
        this.settings = settings;
        this.messageRepository = messageRepository;
        this.windowRepository = windowRepository;
        this.partnerSettingsRepository = partnerSettingsRepository;
        this.fileRepository = fileRepository;
        this.outbound = outbound;
        this.walletService = walletService;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPartnerEvent(PartnerEvent event) {
        if (!PartnerEvent.MESSAGE_INBOUND.equals(event.type()) || !(event.data().get("id") instanceof String uid)) {
            return;
        }
        try {
            handle(event.partnerId(), uid);
        } catch (RuntimeException e) {
            log.warn("AI reply for message {} failed: {}", uid, e.getMessage());
        }
    }

    /** Public for tests: decides and (maybe) answers one inbound message. Returns the logged outcome or null. */
    public AiOutcome handle(Long partnerId, String inboundUid) {
        AiService.Platform platform = aiService.platform();
        if (platform == null) {
            return null;
        }
        AiAssistant a = assistantRepository.findById(partnerId).orElse(null);
        if (a == null || !a.isEnabled()) {
            return null;
        }
        Message in = messageRepository.findByUidAndPartnerId(inboundUid, partnerId).orElse(null);
        if (in == null || in.getDirection() != MessageDirection.INBOUND
                || (a.getSenderIds() != null && !a.getSenderIds().contains(in.getPhoneNumberId()))
                || usageRepository.existsByInboundMessageId(in.getId())) {
            return null;
        }
        var window = windowRepository.findByPhoneNumberIdAndWaId(in.getPhoneNumberId(), in.getWaId()).orElse(null);
        if (window == null || window.isAiPaused() || window.getExpiresAt() == null || window.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        String text = in.getPreview() == null ? "" : in.getPreview().trim();
        if (isKeyword(partnerId, text) || registrations.claimed(partnerId, in.getWaId(), text).isPresent()) {
            return null; // opt-out/opt-in words and pass claim codes are handled elsewhere
        }
        if (!ANSWERABLE.contains(in.getType()) || text.isEmpty()) {
            return finish(in, AiOutcome.SKIPPED, "Customer sent a " + in.getType().name().toLowerCase(Locale.ROOT)
                    + "; left for your team", null, true, false);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (a.getHandoverKeywords() != null && a.getHandoverKeywords().stream()
                .anyMatch(k -> !k.isBlank() && lower.contains(k.toLowerCase(Locale.ROOT)))) {
            return finish(in, AiOutcome.HANDOVER, "Customer asked for a person", null, true, true, AiService.fallbackText(a));
        }
        int cap = Math.min(a.getMaxRepliesPerChatPerDay(), settings.getInt(SettingKeys.AI, "max_replies_per_chat_per_day", 20));
        if (usageRepository.countRepliesSince(in.getPhoneNumberId(), in.getWaId(), Instant.now().minus(Duration.ofHours(24))) >= cap) {
            return finish(in, AiOutcome.SKIPPED, "Daily AI reply limit reached for this chat", null, true, false);
        }
        BigDecimal fee = aiService.feePerReply();
        if (fee.signum() > 0 && walletService.get(partnerId).getBalance().compareTo(fee) < 0) {
            return finish(in, AiOutcome.SKIPPED, "Wallet balance too low for an AI reply", null, true, false);
        }

        // Everything the AI may say is read from the database now, for this answer.
        List<AiKnowledgeItem> items = knowledgeRepository.findByPartnerIdAndActiveTrueOrderBySortOrderAscIdAsc(partnerId);
        List<TravelService.PackageDto> packages = a.isUsePackages() ? travel.activeForAi(partnerId) : List.of();
        String context = aiService.customerContext(a, partnerId, in.getWaId());
        int historySize = Math.max(0, Math.min(20, settings.getInt(SettingKeys.AI, "history_messages", 6)));
        List<AiModelClient.Turn> history = new ArrayList<>();
        if (historySize > 0) {
            List<Message> earlier = new ArrayList<>(messageRepository.conversation(partnerId, in.getPhoneNumberId(), in.getWaId(),
                    in.getId(), PageRequest.of(0, historySize)));
            Collections.reverse(earlier);
            earlier.forEach(m -> history.add(new AiModelClient.Turn(m.getDirection() == MessageDirection.INBOUND, m.getPreview())));
        }
        AiModelClient.AiResult r;
        try {
            r = model.complete(aiService.request(a, items, packages, platform, context, history, text));
        } catch (RuntimeException e) {
            log.info("AI call for partner {} failed: {}", partnerId, e.getMessage());
            return finish(in, AiOutcome.ERROR, "AI service did not answer", null, true, false);
        }
        if (r.handover() || r.reply() == null || r.reply().isBlank()) {
            String reason = r.reason() == null || r.reason().isBlank() ? "Answer not in your knowledge" : r.reason();
            return finish(in, AiOutcome.FALLBACK, reason, r, true, a.isPauseOnFallback(), AiService.fallbackText(a));
        }
        return reply(in, r, aiService.attachments(r.attachmentIds(), items, packages), fee);
    }

    private AiOutcome reply(Message in, AiModelClient.AiResult r, List<AiService.Attachment> attachments, BigDecimal fee) {
        String replyText = r.reply().length() > 4000 ? r.reply().substring(0, 4000) : r.reply();
        try {
            return tx.execute(s -> {
                Message sent = outbound.enqueue(OutboundCommand.of(in.getPartnerId(), in.getPhoneNumberId(), in.getWaId(),
                        text(in, replyText), MessageSource.AI));
                for (AiService.Attachment item : attachments) {
                    StoredFile f = fileRepository.findById(item.fileId()).orElse(null);
                    if (f != null) {
                        outbound.enqueue(OutboundCommand.of(in.getPartnerId(), in.getPhoneNumberId(), in.getWaId(),
                                media(in, f, item.title()), MessageSource.AI));
                    }
                }
                WalletTransaction wt = fee.signum() > 0
                        ? walletService.debit(in.getPartnerId(), fee, WalletRefType.AI_REPLY, in.getUid(), "AI reply")
                        : null;
                AiUsageLog l = log(in, AiOutcome.REPLIED, r.reason(), r);
                l.setReplyMessageId(sent.getId());
                l.setReplyPreview(cut(replyText, 500));
                l.setCharged(fee.signum() > 0 ? fee : BigDecimal.ZERO);
                l.setWalletTransactionId(wt == null ? null : wt.getId());
                usageRepository.save(l);
                return AiOutcome.REPLIED;
            });
        } catch (RuntimeException e) {
            log.info("AI reply for {} not sent: {}", in.getUid(), e.getMessage());
            return finish(in, AiOutcome.ERROR, cut("Reply not sent: " + e.getMessage(), 255), r, true, false);
        }
    }

    private AiOutcome finish(Message in, AiOutcome outcome, String reason, AiModelClient.AiResult r, boolean needsHuman, boolean pause) {
        return finish(in, outcome, reason, r, needsHuman, pause, null);
    }

    /**
     * Logs the outcome, flags the chat for a human and optionally sends the partner's fallback message,
     * all in one transaction. If the fallback cannot be sent (no balance, no price) it is retried without
     * the message so the log and the handover are never lost.
     */
    private AiOutcome finish(Message in, AiOutcome outcome, String reason, AiModelClient.AiResult r, boolean needsHuman,
                             boolean pause, String fallback) {
        try {
            if (fallback != null && !fallback.isBlank()) {
                try {
                    tx.executeWithoutResult(s -> {
                        Message sent = outbound.enqueue(OutboundCommand.of(in.getPartnerId(), in.getPhoneNumberId(), in.getWaId(),
                                text(in, fallback), MessageSource.AI));
                        AiUsageLog l = log(in, outcome, reason, r);
                        l.setReplyMessageId(sent.getId());
                        l.setReplyPreview(cut(fallback, 500));
                        record(in, l, needsHuman, pause);
                    });
                    return outcome;
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    throw e;
                } catch (RuntimeException e) {
                    String why = cut(reason + " (fallback not sent: " + e.getMessage() + ")", 255);
                    tx.executeWithoutResult(s -> record(in, log(in, outcome, why, r), needsHuman, pause));
                    return outcome;
                }
            }
            tx.executeWithoutResult(s -> record(in, log(in, outcome, reason, r), needsHuman, pause));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return null; // another thread already handled this message
        }
        return outcome;
    }

    private void record(Message in, AiUsageLog l, boolean needsHuman, boolean pause) {
        usageRepository.save(l);
        windowRepository.findByPhoneNumberIdAndWaId(in.getPhoneNumberId(), in.getWaId()).ifPresent(w -> {
            w.setNeedsHuman(w.isNeedsHuman() || needsHuman);
            w.setAiPaused(w.isAiPaused() || pause);
            windowRepository.save(w);
        });
        if (needsHuman) {
            events.publishEvent(new InboxEvent(in.getPartnerId(), InboxEvent.MESSAGE_NEW,
                    Map.of("uid", in.getUid(), "senderId", in.getPhoneNumberId(), "waId", in.getWaId(), "direction", "INBOUND",
                            "needsHuman", true)));
        }
    }

    private AiUsageLog log(Message in, AiOutcome outcome, String reason, AiModelClient.AiResult r) {
        AiUsageLog l = new AiUsageLog();
        l.setPartnerId(in.getPartnerId());
        l.setPhoneNumberId(in.getPhoneNumberId());
        l.setWaId(in.getWaId());
        l.setInboundMessageId(in.getId());
        AiService.Platform p = r == null ? null : aiService.platform();
        l.setModel(p == null ? null : p.model());
        l.setOutcome(outcome);
        l.setReason(cut(reason, 255));
        l.setInboundPreview(cut(in.getPreview(), 255));
        if (r != null) {
            l.setInputTokens(r.inputTokens());
            l.setOutputTokens(r.outputTokens());
            l.setCacheReadTokens(r.cacheReadTokens());
            l.setCacheWriteTokens(r.cacheWriteTokens());
        }
        l.setCharged(BigDecimal.ZERO);
        l.setCreatedAt(Instant.now());
        return l;
    }

    private boolean isKeyword(Long partnerId, String text) {
        PartnerSettings ps = partnerSettingsRepository.findById(partnerId).orElse(null);
        List<String> words = new ArrayList<>(ps == null || ps.getOptOutKeywords() == null ? List.of("STOP", "UNSUBSCRIBE") : ps.getOptOutKeywords());
        words.addAll(ps == null || ps.getOptInKeywords() == null ? List.of("START") : ps.getOptInKeywords());
        return words.stream().anyMatch(k -> k.equalsIgnoreCase(text));
    }

    private static SendMessageRequest text(Message in, String body) {
        return new SendMessageRequest(in.getPhoneNumberId(), null, in.getWaId(), "text", null, null,
                new SendMessageRequest.Text(body, false), null, null, null, null, null, null, null);
    }

    private static SendMessageRequest media(Message in, StoredFile f, String caption) {
        String type = AiService.mediaType(f.getMimeType());
        return new SendMessageRequest(in.getPhoneNumberId(), null, in.getWaId(), type, null, null, null,
                new SendMessageRequest.Media(f.getUid(), null, null, cut(caption, 1024),
                        "document".equals(type) ? cut(f.getOriginalName(), 240) : null),
                null, null, null, null, null, null);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
