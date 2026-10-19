package in.codebasket.cbwa.webhook;

import in.codebasket.cbwa.messaging.MessageSource;
import in.codebasket.cbwa.messaging.OutboundCommand;
import in.codebasket.cbwa.messaging.OutboundMessageService;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Automatic replies (e.g. opt-out confirmation). Runs in its own transaction after the webhook
 * transaction commits, so a failure (no balance, no price) never undoes the opt-out itself.
 */
@Slf4j
@Service
public class AutoReplyService {

    private final OutboundMessageService outbound;
    private final TransactionTemplate tx;

    public AutoReplyService(OutboundMessageService outbound, PlatformTransactionManager txManager) {
        this.outbound = outbound;
        this.tx = new TransactionTemplate(txManager);
    }

    public void sendText(Long partnerId, Long senderId, String to, String text) {
        try {
            SendMessageRequest req = new SendMessageRequest(senderId, null, to, "text", null, null,
                    new SendMessageRequest.Text(text, false), null, null, null, null, null, null, null);
            tx.executeWithoutResult(s -> outbound.enqueue(OutboundCommand.of(partnerId, senderId, to, req, MessageSource.SYSTEM)));
        } catch (RuntimeException e) {
            log.info("Auto reply to partner {} contact not sent: {}", partnerId, e.getMessage());
        }
    }
}
