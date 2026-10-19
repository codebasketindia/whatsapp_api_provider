package in.codebasket.cbwa.template;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.meta.MetaTemplateApi;
import in.codebasket.cbwa.template.dto.TemplateDtos.SyncResult;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WhatsappAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TP7 / TP17: pulls all templates of a WABA from Meta and upserts them by Meta id or
 * (WABA, name, language). In full mode, templates missing at Meta are marked DELETED and
 * unknown Meta templates are imported; in partner mode only the partner's own rows are updated.
 */
@Service
@RequiredArgsConstructor
public class TemplateSyncService {

    private final TemplateRepository templateRepository;
    private final TemplateStore store;
    private final MetaTemplateApi templateApi;
    private final CryptoService crypto;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    public SyncResult sync(WhatsappAccount waba, Long onlyPartnerId) {
        List<JsonNode> remote = templateApi.listAll(waba.getWabaId(), crypto.decrypt(waba.getAccessTokenEnc()));
        boolean full = onlyPartnerId == null || waba.getOwnerType() == WabaOwnerType.PARTNER;
        return tx.execute(st -> {
            int created = 0;
            int updated = 0;
            int deleted = 0;
            Set<String> remoteIds = new HashSet<>();
            for (JsonNode r : remote) {
                String metaId = r.path("id").asText();
                remoteIds.add(metaId);
                Template t = templateRepository.findByMetaTemplateId(metaId)
                        .or(() -> templateRepository.findByWhatsappAccountIdAndNameAndLanguage(waba.getId(),
                                r.path("name").asText(), r.path("language").asText()))
                        .orElse(null);
                if (t != null && onlyPartnerId != null && !full && !onlyPartnerId.equals(t.getPartnerId())) {
                    continue;
                }
                if (t == null) {
                    if (!full) {
                        continue;
                    }
                    t = new Template();
                    t.setUid(Ulids.next());
                    t.setWhatsappAccountId(waba.getId());
                    t.setPartnerId(waba.getOwnerType() == WabaOwnerType.PARTNER ? waba.getPartnerId() : null);
                    t.setName(r.path("name").asText());
                    t.setLanguage(r.path("language").asText());
                    created++;
                } else {
                    updated++;
                }
                apply(t, r);
                templateRepository.save(t);
                store.replaceComponents(t, TemplatePayloads.fromMeta(r.path("components")));
            }
            if (full) {
                for (Template local : templateRepository.findByWhatsappAccountId(waba.getId())) {
                    if (local.getMetaTemplateId() != null && !remoteIds.contains(local.getMetaTemplateId())
                            && local.getStatus() != TemplateStatus.DELETED) {
                        local.setStatus(TemplateStatus.DELETED);
                        templateRepository.save(local);
                        deleted++;
                    }
                }
            }
            auditService.log("TEMPLATE_SYNC", "WABA", waba.getId(), null,
                    Map.of("created", created, "updated", updated, "deleted", deleted), onlyPartnerId);
            return new SyncResult(created, updated, deleted);
        });
    }

    static void apply(Template t, JsonNode r) {
        t.setMetaTemplateId(r.path("id").asText());
        String cat = r.path("category").asText("UTILITY").toUpperCase();
        try {
            MessageCategory mc = MessageCategory.valueOf(cat);
            if (t.getCategory() != null && t.getCategory() != mc) {
                t.setPreviousCategory(t.getCategory().name());
            }
            t.setCategory(mc);
        } catch (IllegalArgumentException ignored) {
            if (t.getCategory() == null) {
                t.setCategory(MessageCategory.UTILITY);
            }
        }
        if (r.hasNonNull("previous_category")) {
            t.setPreviousCategory(r.get("previous_category").asText());
        }
        t.setStatus(TemplateService.mapStatus(r.path("status").asText(null), t.getStatus() == null ? TemplateStatus.PENDING : t.getStatus()));
        String reason = r.path("rejected_reason").asText(null);
        t.setRejectedReason(reason == null || "NONE".equalsIgnoreCase(reason) ? null : reason);
        String quality = r.path("quality_score").path("score").asText(r.path("quality_score").asText("UNKNOWN"));
        t.setQualityScore(quality == null || quality.isBlank() ? "UNKNOWN" : quality.toUpperCase());
        String pf = r.path("parameter_format").asText("POSITIONAL").toUpperCase();
        t.setParameterFormat("NAMED".equals(pf) ? ParameterFormat.NAMED : ParameterFormat.POSITIONAL);
        t.setMetaPayload(r.toString());
        t.setLastSyncedAt(Instant.now());
    }
}
