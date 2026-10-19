package in.codebasket.cbwa.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.auth.AuthPrincipal;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.web.ClientIp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Writes audit entries inside the caller's transaction (so a rolled-back action leaves no audit
 * row). Sensitive fields are masked before storing.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Set<String> SENSITIVE = Set.of("password", "token", "accesstoken", "secret", "pin",
            "apisecret", "appsecret", "keysecret", "webhooksecret", "verifytoken", "razorpaykeysecret",
            "razorpaywebhooksecret", "passwordhash", "secrethash", "pinenc", "accesstokenenc");

    private final AuditLogRepository repository;

    @Transactional
    public void log(String action, String entityType, Object entityId, Object oldValue, Object newValue) {
        log(action, entityType, entityId, oldValue, newValue, null);
    }

    @Transactional
    public void log(String action, String entityType, Object entityId, Object oldValue, Object newValue, Long partnerId) {
        AuditLog a = new AuditLog();
        AuthPrincipal p = SecurityUtils.currentOrNull();
        if (p != null) {
            a.setActorUserId(p.userId());
            a.setActorType(p.isApiKey() ? "API_KEY" : p.userType().name());
            a.setImpersonatorUserId(p.impersonatorUserId());
            if (partnerId == null && p.partnerId() != null) {
                partnerId = p.partnerId();
            }
        } else {
            a.setActorType("SYSTEM");
        }
        a.setPartnerId(partnerId);
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId == null ? null : String.valueOf(entityId));
        a.setOldValue(mask(oldValue));
        a.setNewValue(mask(newValue));
        a.setIp(ClientIp.current());
        a.setUserAgent(ClientIp.userAgent());
        a.setCreatedAt(Instant.now());
        repository.save(a);
    }

    static String mask(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode node = JsonUtil.MAPPER.valueToTree(value);
        maskNode(node);
        return node.toString();
    }

    private static void maskNode(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String k = e.getKey().toLowerCase().replace("_", "");
                if (SENSITIVE.contains(k)) {
                    e.setValue(obj.textNode("****"));
                } else {
                    maskNode(e.getValue());
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(AuditService::maskNode);
        }
    }
}
