package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.messaging.ChargeStatus;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageDirection;
import in.codebasket.cbwa.messaging.MessageRepository;
import in.codebasket.cbwa.messaging.MessageStatus;
import in.codebasket.cbwa.messaging.MessageType;
import in.codebasket.cbwa.messaging.OutboundCommand;
import in.codebasket.cbwa.pricing.PriceResolver;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateRepository;
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletService;

import java.math.BigDecimal;

/**
 * TEST-ONLY stand-in for the messaging engine's OutboundMessageService.enqueue: prices the message,
 * HOLDs the price in the wallet and saves a QUEUED Message row, so campaign flows (dispatch, refunds,
 * progress, reports) can be tested without the real worker / Meta API.
 */
public class TestOutboundStub {

    private final MessageRepository messageRepository;
    private final TemplateRepository templateRepository;
    private final PriceResolver priceResolver;
    private final SenderAccessService senderAccess;
    private final WalletService walletService;

    public TestOutboundStub(MessageRepository messageRepository, TemplateRepository templateRepository, PriceResolver priceResolver,
                            SenderAccessService senderAccess, WalletService walletService) {
        this.messageRepository = messageRepository;
        this.templateRepository = templateRepository;
        this.priceResolver = priceResolver;
        this.senderAccess = senderAccess;
        this.walletService = walletService;
    }

    public Message enqueue(OutboundCommand cmd) {
        var sender = senderAccess.requireUsableSender(cmd.partnerId(), cmd.senderId());
        Template t = templateRepository.findById(cmd.request().template().templateId()).orElseThrow();
        String country = PhoneUtil.regionOfDigits(cmd.to());
        BigDecimal price = priceResolver.priceFor(cmd.partnerId(), sender, t.getCategory(), country);
        Message m = new Message();
        m.setUid(Ulids.next());
        m.setPartnerId(cmd.partnerId());
        m.setPhoneNumberId(cmd.senderId());
        m.setDirection(MessageDirection.OUTBOUND);
        m.setWaId(cmd.to());
        m.setCountryCode(country);
        m.setContactId(cmd.contactId());
        m.setCampaignId(cmd.campaignId());
        m.setCampaignRecipientId(cmd.campaignRecipientId());
        m.setType(MessageType.TEMPLATE);
        m.setTemplateId(t.getId());
        m.setTemplateName(t.getName());
        m.setTemplateLanguage(t.getLanguage());
        m.setCategory(t.getCategory());
        m.setStatus(MessageStatus.QUEUED);
        m.setPrice(price);
        m.setChargeStatus(price.signum() > 0 ? ChargeStatus.HELD : ChargeStatus.NONE);
        m.setSource(cmd.source());
        if (price.signum() > 0) {
            walletService.hold(cmd.partnerId(), price, WalletRefType.MESSAGE, m.getUid(), "Campaign message");
        }
        return messageRepository.save(m);
    }

    public BigDecimal unitPrice(Long partnerId, Long senderId, MessageCategory category, String to) {
        var sender = senderAccess.requirePartnerSender(partnerId, senderId);
        return priceResolver.priceFor(partnerId, sender, category, PhoneUtil.regionOfDigits(to));
    }
}
