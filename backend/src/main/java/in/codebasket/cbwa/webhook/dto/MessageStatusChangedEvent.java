package in.codebasket.cbwa.webhook.dto;

import in.codebasket.cbwa.common.constants.ChargeStatus;
import in.codebasket.cbwa.common.constants.MessageStatus;

import java.math.BigDecimal;

/**
 * Published inside the transaction whenever an outbound message changes status (or its charge
 * changes). The campaign module listens to keep recipient and campaign counters in sync.
 */
public record MessageStatusChangedEvent(Long messageId, Long partnerId, Long campaignId, Long campaignRecipientId,
                                        MessageStatus oldStatus, MessageStatus newStatus, ChargeStatus chargeStatus,
                                        BigDecimal price, String errorMessage) {
}
