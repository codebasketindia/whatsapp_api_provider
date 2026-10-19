package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.messaging.dto.SendMessageRequest;

/**
 * Input to {@link OutboundMessageService#enqueue}. `to` must already be normalised (E.164 digits).
 * priority: 1 = interactive/API (sent first), 5 = campaign bulk.
 */
public record OutboundCommand(Long partnerId, Long senderId, String to, SendMessageRequest request, MessageSource source,
                              Long apiKeyId, Long campaignId, Long campaignRecipientId, Long contactId, int priority) {

    public static OutboundCommand of(Long partnerId, Long senderId, String to, SendMessageRequest request, MessageSource source) {
        return new OutboundCommand(partnerId, senderId, to, request, source, null, null, null, null, 1);
    }
}
