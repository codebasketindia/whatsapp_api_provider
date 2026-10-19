package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.convtools.BlockStatus;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.messaging.lookup.MsgBlockedUserRepository;
import in.codebasket.cbwa.messaging.lookup.MsgContactRepository;
import in.codebasket.cbwa.messaging.lookup.MsgOptOutRepository;
import in.codebasket.cbwa.pricing.PriceResolver;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Validates, prices, holds and queues outbound messages. Runs inside the caller's transaction so
 * the HOLD ledger row, the message and its queue row commit (or roll back) together.
 */
@Service
@RequiredArgsConstructor
public class OutboundMessageServiceImpl implements OutboundMessageService {

    private final SenderAccessService senderAccess;
    private final PhoneNumberRepository phoneNumberRepository;
    private final MessagePayloadBuilder payloadBuilder;
    private final MessageRepository messageRepository;
    private final MessageQueueItemRepository queueRepository;
    private final ConversationWindowRepository windowRepository;
    private final MsgContactRepository contactRepository;
    private final MsgOptOutRepository optOutRepository;
    private final MsgBlockedUserRepository blockedUserRepository;
    private final PriceResolver priceResolver;
    private final WalletService walletService;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public Message enqueue(OutboundCommand cmd) {
        if (cmd.senderId() == null) {
            throw AppException.field("senderId", "Choose a sender number");
        }
        PhoneNumber sender = senderAccess.requireUsableSender(cmd.partnerId(), cmd.senderId());
        String to = normaliseTo(cmd.to());
        if (to.equals(sender.getWaNumber())) {
            throw new AppException(ErrorCode.RECIPIENT_INVALID, "You cannot send a message to the sender number itself");
        }

        if (sender.getDailySendCap() != null && sender.getDailySendCap() > 0) {
            long today = messageRepository.countByPhoneNumberIdAndDirectionAndCreatedAtGreaterThanEqual(sender.getId(),
                    MessageDirection.OUTBOUND, TimeUtil.startOfDayIst(TimeUtil.todayIst()));
            if (today >= sender.getDailySendCap()) {
                throw new AppException(ErrorCode.SENDER_DAILY_CAP_REACHED,
                        "Daily limit of " + sender.getDailySendCap() + " messages reached for this number");
            }
        }
        if (blockedUserRepository.existsByPhoneNumberIdAndWaIdAndStatus(sender.getId(), to, BlockStatus.BLOCKED)) {
            throw new AppException(ErrorCode.RECIPIENT_BLOCKED);
        }

        MessagePayloadBuilder.Built built = payloadBuilder.build(cmd.partnerId(), sender, to, cmd.request());

        if (built.template() == null) {
            // Free-form messages are only allowed inside the 24-hour customer service window.
            boolean open = windowRepository.findByPhoneNumberIdAndWaId(sender.getId(), to)
                    .map(w -> w.getExpiresAt() != null && w.getExpiresAt().isAfter(Instant.now()))
                    .orElse(false);
            if (!open) {
                throw new AppException(ErrorCode.WINDOW_CLOSED);
            }
        } else if (built.category() == MessageCategory.MARKETING && optOutRepository.existsByPartnerIdAndPhone(cmd.partnerId(), to)) {
            throw new AppException(ErrorCode.RECIPIENT_OPTED_OUT);
        }

        String country = PhoneUtil.regionOfDigits(to);
        BigDecimal price = priceResolver.priceFor(cmd.partnerId(), sender, built.category(), country);

        Message m = new Message();
        m.setUid(Ulids.next());
        m.setPartnerId(cmd.partnerId());
        m.setPhoneNumberId(sender.getId());
        m.setDirection(MessageDirection.OUTBOUND);
        m.setWaId(to);
        m.setCountryCode("ZZ".equals(country) ? null : country);
        m.setContactId(cmd.contactId() != null ? cmd.contactId()
                : contactRepository.findByPartnerIdAndPhone(cmd.partnerId(), to).map(c -> c.getId()).orElse(null));
        m.setCampaignId(cmd.campaignId());
        m.setCampaignRecipientId(cmd.campaignRecipientId());
        m.setType(built.type());
        if (built.template() != null) {
            m.setTemplateId(built.template().getId());
            m.setTemplateName(built.template().getName());
            m.setTemplateLanguage(built.template().getLanguage());
        }
        m.setCategory(built.category());
        m.setPayload(built.payload().toString());
        m.setPreview(built.preview());
        m.setContextWamid(built.contextWamid());
        m.setStatus(MessageStatus.QUEUED);
        m.setPrice(MoneyUtil.scale(price));
        m.setSource(cmd.source());
        m.setApiKeyId(cmd.apiKeyId());
        m.setClientReference(cmd.request().clientReference());
        if (price.signum() > 0) {
            walletService.hold(cmd.partnerId(), price, WalletRefType.MESSAGE, m.getUid(),
                    "Message to " + PhoneUtil.mask(to) + " (on hold)");
            m.setChargeStatus(ChargeStatus.HELD);
        } else {
            m.setChargeStatus(ChargeStatus.NONE);
        }
        messageRepository.save(m);

        MessageQueueItem q = new MessageQueueItem();
        q.setMessageId(m.getId());
        q.setPhoneNumberId(sender.getId());
        q.setPriority(cmd.priority() <= 0 ? 1 : cmd.priority());
        q.setStatus(QueueStatus.PENDING);
        q.setAttempts(0);
        q.setMaxAttempts(5);
        q.setNextAttemptAt(Instant.now());
        queueRepository.save(q);

        touchConversation(m);
        if (m.getSource() == MessageSource.INBOX || m.getSource() == MessageSource.DASHBOARD || m.getSource() == MessageSource.SYSTEM) {
            events.publishEvent(new InboxEvent(m.getPartnerId(), InboxEvent.MESSAGE_NEW,
                    Map.of("uid", m.getUid(), "senderId", m.getPhoneNumberId(), "waId", m.getWaId(), "direction", "OUTBOUND")));
        }
        return m;
    }

    @Override
    @Transactional(readOnly = true)
    public MessageCategory categoryOf(Long partnerId, Long senderId, SendMessageRequest request) {
        if (!"template".equals(request.type())) {
            return MessageCategory.SERVICE;
        }
        if (request.template() == null) {
            throw AppException.field("template", "Template details are required");
        }
        PhoneNumber sender = senderAccess.requirePartnerSender(partnerId, senderId);
        return payloadBuilder.resolveTemplate(partnerId, sender, request.template()).getCategory();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal unitPrice(Long partnerId, Long senderId, MessageCategory category, String toDigits) {
        PhoneNumber sender = phoneNumberRepository.findById(senderId).orElseThrow(() -> AppException.notFound("Sender"));
        String country = toDigits == null ? null : PhoneUtil.regionOfDigits(toDigits);
        return priceResolver.priceFor(partnerId, sender, category, "ZZ".equals(country) ? null : country);
    }

    /** Keeps the inbox list ordered by latest activity; never opens the 24h window (only inbound does). */
    private void touchConversation(Message m) {
        ConversationWindow w = windowRepository.findByPhoneNumberIdAndWaId(m.getPhoneNumberId(), m.getWaId()).orElseGet(() -> {
            ConversationWindow n = new ConversationWindow();
            n.setPartnerId(m.getPartnerId());
            n.setPhoneNumberId(m.getPhoneNumberId());
            n.setWaId(m.getWaId());
            n.setUnreadCount(0);
            return n;
        });
        w.setLastMessageAt(Instant.now());
        w.setLastMessagePreview(m.getPreview() == null ? null : m.getPreview().substring(0, Math.min(255, m.getPreview().length())));
        windowRepository.save(w);
    }

    public static String normaliseTo(String raw) {
        return PhoneUtil.parse(raw, "IN").map(PhoneUtil.Parsed::digits)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_INVALID, "The phone number '" + raw + "' is not valid"));
    }
}
