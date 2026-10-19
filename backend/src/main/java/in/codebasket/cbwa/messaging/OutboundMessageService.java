package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.domain.MessageCategory;

import java.math.BigDecimal;

/**
 * Cross-module contract for sending (implemented by the messaging module, used by dashboard send,
 * inbox, campaigns and the public API).
 *
 * enqueue() runs in the caller's transaction and: validates the payload, resolves the template
 * (must be APPROVED for template sends), enforces the 24h window for free-form messages, blocks
 * opted-out recipients for MARKETING templates and blocked users, checks the sender daily cap,
 * prices the message (PriceResolver) and HOLDs the price in the wallet, then saves the Message
 * (status QUEUED) and its message_queue row. Throws AppException with contract error codes.
 */
public interface OutboundMessageService {

    Message enqueue(OutboundCommand command);

    /** Category used for pricing: SERVICE for free-form messages, the template's category otherwise. */
    MessageCategory categoryOf(Long partnerId, Long senderId, in.codebasket.cbwa.messaging.dto.SendMessageRequest request);

    /** Unit price for one message of this request to this recipient (no wallet change). */
    BigDecimal unitPrice(Long partnerId, Long senderId, MessageCategory category, String toDigits);
}
