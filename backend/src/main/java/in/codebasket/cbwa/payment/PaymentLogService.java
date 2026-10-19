package in.codebasket.cbwa.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Stores gateway requests/responses/webhooks per order (in its own transaction so failed attempts
 * are kept). Card numbers, UPI ids and bank account data are masked; secrets are never passed in.
 */
@Service
@RequiredArgsConstructor
public class PaymentLogService {

    private static final Set<String> MASK = Set.of("vpa", "card_id", "bank_account", "acquirer_data", "token_id",
            "upi", "customer_id");

    private final PaymentGatewayLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long orderId, GatewayType gateway, String direction, String event, Integer httpStatus, Object payload) {
        PaymentGatewayLog l = new PaymentGatewayLog();
        l.setPaymentOrderId(orderId);
        l.setGateway(gateway);
        l.setDirection(direction);
        l.setEvent(event.length() > 64 ? event.substring(0, 64) : event);
        l.setHttpStatus(httpStatus);
        l.setPayload(payload == null ? null : sanitize(JsonUtil.MAPPER.valueToTree(payload)).toString());
        l.setCreatedAt(Instant.now());
        repository.save(l);
    }

    /** Removes card/UPI/bank details from a gateway payload (keeps card last4/network only). */
    public static JsonNode sanitize(JsonNode node) {
        if (node == null) {
            return null;
        }
        JsonNode copy = node.deepCopy();
        clean(copy);
        return copy;
    }

    private static void clean(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                if (MASK.contains(key) && !e.getValue().isNull()) {
                    e.setValue(obj.textNode("****"));
                } else if ("card".equals(key) && e.getValue().isObject()) {
                    ObjectNode card = obj.objectNode();
                    card.set("last4", e.getValue().path("last4"));
                    card.set("network", e.getValue().path("network"));
                    card.set("type", e.getValue().path("type"));
                    e.setValue(card);
                } else {
                    clean(e.getValue());
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(PaymentLogService::clean);
        }
    }
}
