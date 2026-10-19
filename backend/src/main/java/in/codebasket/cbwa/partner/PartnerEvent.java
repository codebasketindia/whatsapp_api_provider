package in.codebasket.cbwa.partner;

import java.util.Map;

/**
 * Domain event for partner-facing notifications (partner webhooks, live inbox). Published with
 * Spring's ApplicationEventPublisher; the partnerhook module forwards these to the partner's
 * webhook URL after the transaction commits.
 */
public record PartnerEvent(Long partnerId, String type, Map<String, Object> data) {

    public static final String MESSAGE_STATUS = "message.status";
    public static final String MESSAGE_INBOUND = "message.inbound";
    public static final String TEMPLATE_STATUS = "template.status";
    public static final String SENDER_STATUS = "sender.status";
    public static final String WALLET_LOW_BALANCE = "wallet.low_balance";
    public static final String TEST_PING = "test.ping";
}
