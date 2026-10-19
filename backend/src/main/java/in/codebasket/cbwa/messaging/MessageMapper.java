package in.codebasket.cbwa.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.campaign.Campaign;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.storage.StoredFileRepository;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.contact.Contact;
import in.codebasket.cbwa.messaging.dto.MessageDtos.CampaignRef;
import in.codebasket.cbwa.messaging.dto.MessageDtos.ContactRef;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MediaInfo;
import in.codebasket.cbwa.messaging.dto.MessageDtos.MessageDto;
import in.codebasket.cbwa.messaging.dto.MessageDtos.PartnerRef;
import in.codebasket.cbwa.messaging.dto.MessageDtos.SenderRef;
import in.codebasket.cbwa.messaging.dto.MessageDtos.StatusHistoryItem;
import in.codebasket.cbwa.messaging.dto.MessageDtos.TemplateInfo;
import in.codebasket.cbwa.messaging.lookup.MsgCampaignRepository;
import in.codebasket.cbwa.messaging.lookup.MsgContactRepository;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds MessageDto lists with batched lookups (senders, contacts, campaigns, media, partners). */
@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final PhoneNumberRepository phoneNumberRepository;
    private final MsgContactRepository contactRepository;
    private final MsgCampaignRepository campaignRepository;
    private final InboundMediaRepository inboundMediaRepository;
    private final StoredFileRepository storedFileRepository;
    private final PartnerRepository partnerRepository;
    private final MessageStatusEventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<MessageDto> toDtos(List<Message> messages, boolean includePartner) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, PhoneNumber> senders = byId(phoneNumberRepository.findAllById(ids(messages, Message::getPhoneNumberId)), PhoneNumber::getId);
        Map<Long, Contact> contacts = byId(contactRepository.findByIdIn(ids(messages, Message::getContactId)), Contact::getId);
        Map<Long, Campaign> campaigns = byId(campaignRepository.findByIdIn(ids(messages, Message::getCampaignId)), Campaign::getId);
        Map<Long, InboundMedia> media = new HashMap<>();
        Set<Long> inboundIds = messages.stream().filter(m -> m.getDirection() == MessageDirection.INBOUND).map(Message::getId)
                .collect(Collectors.toSet());
        if (!inboundIds.isEmpty()) {
            inboundMediaRepository.findByMessageIdIn(inboundIds).forEach(im -> media.put(im.getMessageId(), im));
        }
        Map<Long, StoredFile> files = byId(storedFileRepository.findAllById(media.values().stream().map(InboundMedia::getFileId)
                .filter(Objects::nonNull).collect(Collectors.toSet())), StoredFile::getId);
        Map<Long, Partner> partners = includePartner
                ? byId(partnerRepository.findAllById(ids(messages, Message::getPartnerId)), Partner::getId) : Map.of();
        return messages.stream().map(m -> toDto(m, senders.get(m.getPhoneNumberId()), contacts.get(m.getContactId()),
                campaigns.get(m.getCampaignId()), media.get(m.getId()), files, partners.get(m.getPartnerId()), null)).toList();
    }

    @Transactional(readOnly = true)
    public MessageDto toDetail(Message m, boolean includePartner) {
        MessageDto base = toDtos(List.of(m), includePartner).get(0);
        List<StatusHistoryItem> history = new java.util.ArrayList<>();
        history.add(new StatusHistoryItem(m.getDirection() == MessageDirection.INBOUND ? "RECEIVED" : "QUEUED", m.getCreatedAt(), null, null));
        eventRepository.findByMessageIdOrderByIdAsc(m.getId()).forEach(e -> history.add(new StatusHistoryItem(e.getStatus().toUpperCase(),
                e.getEventTimestamp() != null ? e.getEventTimestamp() : e.getCreatedAt(), e.getErrorCode(), e.getErrorTitle())));
        return withHistory(base, history);
    }

    private MessageDto toDto(Message m, PhoneNumber sender, Contact contact, Campaign campaign, InboundMedia im,
                             Map<Long, StoredFile> files, Partner partner, List<StatusHistoryItem> history) {
        boolean out = m.getDirection() == MessageDirection.OUTBOUND;
        MediaInfo mediaInfo = null;
        if (im != null) {
            StoredFile f = im.getFileId() == null ? null : files.get(im.getFileId());
            mediaInfo = new MediaInfo(f == null ? null : "/api/files/" + f.getUid(), im.getMimeType(), im.getFilename(),
                    im.getCaption(), im.getDownloadStatus());
        }
        JsonNode payload = m.getPayload() == null ? null : JsonUtil.readTree(m.getPayload());
        return new MessageDto(m.getUid(), m.getDirection().name(),
                sender == null ? null : new SenderRef(sender.getId(), sender.getDisplayPhoneNumber()),
                m.getWaId(), out ? m.getWaId() : null, out ? null : m.getWaId(),
                contact == null ? null : new ContactRef(contact.getId(), contact.getName()),
                m.getType().name(), m.getCategory() == null ? null : m.getCategory().name(),
                m.getTemplateName() == null ? null : new TemplateInfo(m.getTemplateName(), m.getTemplateLanguage()),
                m.getPreview(), payload, m.getContextWamid(), m.getWamid(), m.getStatus().name(), m.getErrorCode(),
                m.getErrorMessage(), m.getPrice(), m.getChargeStatus().name(), m.getBillable(), m.getPricingCategory(),
                m.getSource().name(), m.getClientReference(),
                campaign == null ? null : new CampaignRef(campaign.getUid(), campaign.getName()),
                partner == null ? null : new PartnerRef(partner.getId(), partner.getBusinessName()),
                mediaInfo, m.getCreatedAt(), m.getSentAt(), m.getDeliveredAt(), m.getReadAt(), m.getFailedAt(), history);
    }

    private static MessageDto withHistory(MessageDto d, List<StatusHistoryItem> h) {
        return new MessageDto(d.uid(), d.direction(), d.sender(), d.waId(), d.to(), d.from(), d.contact(), d.type(), d.category(),
                d.template(), d.preview(), d.payload(), d.contextWamid(), d.wamid(), d.status(), d.errorCode(), d.errorMessage(),
                d.price(), d.chargeStatus(), d.billable(), d.pricingCategory(), d.source(), d.clientReference(), d.campaign(),
                d.partner(), d.media(), d.createdAt(), d.sentAt(), d.deliveredAt(), d.readAt(), d.failedAt(), h);
    }

    private static Set<Long> ids(List<Message> messages, Function<Message, Long> f) {
        return messages.stream().map(f).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(List<T> list, Function<T, Long> id) {
        return list.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }
}
