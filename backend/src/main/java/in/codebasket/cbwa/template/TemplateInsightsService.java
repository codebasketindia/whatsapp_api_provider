package in.codebasket.cbwa.template;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaTemplateApi;
import in.codebasket.cbwa.sender.SenderStatsRepository;
import in.codebasket.cbwa.template.dto.TemplateDtos.AnalyticsDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.ButtonMetric;
import in.codebasket.cbwa.template.dto.TemplateDtos.DailyMetric;
import in.codebasket.cbwa.template.dto.TemplateDtos.LibraryItemDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.LibraryUseRequest;
import in.codebasket.cbwa.template.dto.TemplateDtos.TemplateDto;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WabaStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** TP8 template analytics (Meta template_analytics with our logs as fallback) and TP9/TP10 template library. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateInsightsService {

    private final TemplateRepository templateRepository;
    private final TemplateAnalyticsRepository analyticsRepository;
    private final TemplateStore store;
    private final TemplateService templateService;
    private final WhatsappAccountRepository wabaRepository;
    private final MetaTemplateApi templateApi;
    private final SenderStatsRepository statsRepository;
    private final CryptoService crypto;
    private final TransactionTemplate tx;

    public AnalyticsDto analytics(Template t, LocalDate from, LocalDate to) {
        Instant[] range = TimeUtil.range(from, to, 30);
        Map<String, long[]> daily = new TreeMap<>();
        Map<String, ButtonMetric> buttons = new LinkedHashMap<>();
        String source = "META";
        boolean metaOk = false;
        if (t.getMetaTemplateId() != null) {
            WhatsappAccount waba = wabaRepository.findById(t.getWhatsappAccountId()).orElseThrow();
            String token = crypto.decrypt(waba.getAccessTokenEnc());
            try {
                try {
                    templateApi.enableInsights(waba.getWabaId(), token);
                } catch (MetaApiException ignored) {
                    // already enabled or not permitted - analytics call below decides
                }
                JsonNode res = templateApi.analytics(waba.getWabaId(), t.getMetaTemplateId(), range[0].getEpochSecond(),
                        range[1].getEpochSecond() - 1, token);
                for (JsonNode set : res.path("data")) {
                    for (JsonNode dp : set.path("data_points")) {
                        String date = TimeUtil.toIstDate(Instant.ofEpochSecond(dp.path("start").asLong())).toString();
                        long clicked = 0;
                        for (JsonNode c : dp.path("clicked")) {
                            long n = c.path("count").asLong();
                            clicked += n;
                            String key = c.path("type").asText() + "|" + c.path("button_content").asText();
                            ButtonMetric prev = buttons.get(key);
                            buttons.put(key, new ButtonMetric(c.path("button_content").asText(), c.path("type").asText(),
                                    (prev == null ? 0 : prev.count()) + n));
                        }
                        daily.put(date, new long[]{dp.path("sent").asLong(), dp.path("delivered").asLong(), dp.path("read").asLong(), clicked});
                    }
                }
                metaOk = true;
                cache(t.getId(), daily, res);
            } catch (MetaApiException e) {
                log.debug("Template analytics unavailable for {}: {}", t.getId(), e.getMessage());
            }
        }
        if (!metaOk) {
            source = "LOGS";
            for (Object[] r : statsRepository.templateDailyStats(t.getId(), range[0], range[1])) {
                daily.put(String.valueOf(r[0]), new long[]{num(r[1]), num(r[2]), num(r[3]), 0});
            }
        }
        long s = 0;
        long d = 0;
        long rd = 0;
        long c = 0;
        List<DailyMetric> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : daily.entrySet()) {
            long[] v = e.getValue();
            rows.add(new DailyMetric(e.getKey(), v[0], v[1], v[2], v[3]));
            s += v[0];
            d += v[1];
            rd += v[2];
            c += v[3];
        }
        return new AnalyticsDto(new in.codebasket.cbwa.template.dto.TemplateDtos.Totals(s, d, rd, c), rows,
                new ArrayList<>(buttons.values()), source);
    }

    private void cache(Long templateId, Map<String, long[]> daily, JsonNode raw) {
        tx.executeWithoutResult(st -> {
            for (Map.Entry<String, long[]> e : daily.entrySet()) {
                LocalDate date = LocalDate.parse(e.getKey());
                TemplateAnalytics a = analyticsRepository.findByTemplateIdAndDate(templateId, date).orElseGet(() -> {
                    TemplateAnalytics n = new TemplateAnalytics();
                    n.setTemplateId(templateId);
                    n.setDate(date);
                    n.setCost(MoneyUtil.ZERO);
                    return n;
                });
                a.setSent((int) e.getValue()[0]);
                a.setDelivered((int) e.getValue()[1]);
                a.setReadCount((int) e.getValue()[2]);
                a.setClicked(JsonUtil.toJson(Map.of("total", e.getValue()[3])));
                analyticsRepository.save(a);
            }
        });
    }

    /** TP9 */
    public List<LibraryItemDto> library(Long partnerId, String search, String topic, String usecase, String industry, String language) {
        WhatsappAccount waba = tokenWaba(partnerId);
        Map<String, Object> q = new LinkedHashMap<>();
        put(q, "search", search);
        put(q, "topic", topic);
        put(q, "usecase", usecase);
        put(q, "industry", industry);
        put(q, "language", language);
        q.put("limit", 100);
        JsonNode res = templateApi.library(q, crypto.decrypt(waba.getAccessTokenEnc()));
        List<LibraryItemDto> out = new ArrayList<>();
        for (JsonNode n : res.path("data")) {
            List<Map<String, Object>> buttons = new ArrayList<>();
            for (JsonNode b : n.path("buttons")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", b.path("type").asText(null));
                m.put("text", b.path("text").asText(null));
                if (b.has("url")) {
                    m.put("url", b.path("url").asText(null));
                }
                if (b.has("phone_number")) {
                    m.put("phoneNumber", b.path("phone_number").asText(null));
                }
                buttons.add(m);
            }
            out.add(new LibraryItemDto(n.path("name").asText(), n.path("category").asText(null), n.path("topic").asText(null),
                    n.path("usecase").asText(null), n.path("industry").isArray() ? n.path("industry").toString() : n.path("industry").asText(null),
                    n.path("language").asText(null), n.path("header").asText(null), n.path("body").asText(null), buttons,
                    n.has("body_params") ? n.get("body_params") : null));
        }
        return out;
    }

    /** TP10: create a template from the library (pre-approved utility/authentication templates). */
    public TemplateDto useLibrary(Long partnerId, LibraryUseRequest req) {
        WhatsappAccount waba = templateService.partnerWaba(partnerId, req.wabaDbId(), req.senderId());
        if (!TemplateValidator.NAME.matcher(req.name()).matches()) {
            throw AppException.field("name", "Use only lowercase letters, numbers and underscores (e.g. order_update)");
        }
        String category = req.category() == null || req.category().isBlank() ? "UTILITY" : req.category().toUpperCase();
        tx.executeWithoutResult(st -> templateRepository.findByWhatsappAccountIdAndNameAndLanguage(waba.getId(), req.name(), req.language())
                .ifPresent(t -> {
                    throw AppException.field("name", "A template with this name and language already exists");
                }));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", req.name());
        payload.put("language", req.language());
        payload.put("category", category);
        payload.put("library_template_name", req.libraryTemplateName());
        if (req.buttonInputs() != null && !req.buttonInputs().isEmpty()) {
            payload.put("library_template_button_inputs", req.buttonInputs());
        }
        String token = crypto.decrypt(waba.getAccessTokenEnc());
        JsonNode res = templateApi.create(waba.getWabaId(), payload, token);
        JsonNode full = null;
        try {
            full = templateApi.get(res.path("id").asText(), token);
        } catch (MetaApiException ignored) {
            // components will arrive with the next sync
        }
        final JsonNode details = full;
        Long id = tx.execute(st -> {
            Template t = new Template();
            t.setUid(Ulids.next());
            t.setPartnerId(waba.getOwnerType() == WabaOwnerType.PARTNER ? waba.getPartnerId() : partnerId);
            t.setWhatsappAccountId(waba.getId());
            t.setName(req.name());
            t.setLanguage(req.language());
            t.setCategory(MessageCategory.valueOf(category));
            t.setParameterFormat(ParameterFormat.POSITIONAL);
            t.setLibraryTemplateName(req.libraryTemplateName());
            t.setMetaTemplateId(res.path("id").asText(null));
            t.setStatus(TemplateService.mapStatus(res.path("status").asText(null), TemplateStatus.PENDING));
            t.setSubmittedAt(Instant.now());
            if (details != null) {
                TemplateSyncService.apply(t, details);
            }
            templateRepository.save(t);
            if (details != null) {
                store.replaceComponents(t, TemplatePayloads.fromMeta(details.path("components")));
            }
            return t.getId();
        });
        return templateService.adminGet(id);
    }

    private WhatsappAccount tokenWaba(Long partnerId) {
        return tx.execute(st -> {
            List<WhatsappAccount> candidates = new ArrayList<>();
            if (partnerId != null) {
                candidates.addAll(wabaRepository.findByPartnerId(partnerId));
                templateService.platformWabaIds(partnerId).forEach(id -> wabaRepository.findById(id).ifPresent(candidates::add));
            }
            candidates.addAll(wabaRepository.findByStatusNot(WabaStatus.DISCONNECTED).stream()
                    .filter(w -> w.getOwnerType() == WabaOwnerType.PLATFORM).toList());
            return candidates.stream().filter(w -> w.getStatus() != WabaStatus.DISCONNECTED).findFirst()
                    .orElseThrow(() -> AppException.conflict("Connect a WhatsApp number first to browse the template library"));
        });
    }

    private static void put(Map<String, Object> q, String k, String v) {
        if (v != null && !v.isBlank()) {
            q.put(k, v.trim());
        }
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
