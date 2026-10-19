package in.codebasket.cbwa.messaging;

import java.util.Map;

/** Live inbox push (SSE): delivered to the partner's open streams after the transaction commits. */
public record InboxEvent(Long partnerId, String type, Map<String, Object> data) {

    public static final String MESSAGE_NEW = "message.new";
    public static final String MESSAGE_STATUS = "message.status";
}
