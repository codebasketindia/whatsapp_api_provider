package in.codebasket.cbwa.partnerhook;

import com.fasterxml.jackson.databind.node.ObjectNode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.crypto.TokenGenerator;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.partner.PartnerEvent;
import in.codebasket.cbwa.partnerhook.dto.PartnerWebhookDtos.DeliveryLogDto;
import in.codebasket.cbwa.partnerhook.dto.PartnerWebhookDtos.SecretDto;
import in.codebasket.cbwa.partnerhook.dto.PartnerWebhookDtos.TestResultDto;
import in.codebasket.cbwa.partnerhook.dto.PartnerWebhookDtos.WebhookConfigDto;
import in.codebasket.cbwa.partnerhook.dto.PartnerWebhookDtos.WebhookConfigRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Partner webhook configuration (D6–D11) and creation/processing of delivery log rows. */
@Service
@RequiredArgsConstructor
public class PartnerWebhookService {

    public static final List<String> SUBSCRIBABLE = List.of(PartnerEvent.MESSAGE_STATUS, PartnerEvent.MESSAGE_INBOUND,
            PartnerEvent.TEMPLATE_STATUS, PartnerEvent.SENDER_STATUS, PartnerEvent.WALLET_LOW_BALANCE);

    /** Delay before retry n (n = number of failed attempts so far). 5 retries, then DEAD. */
    static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(6));

    private final PartnerWebhookRepository webhookRepository;
    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookUrlValidator urlValidator;
    private final CryptoService crypto;
    private final AuditService auditService;
    private final WebhookSender sender;

    @Transactional(readOnly = true)
    public WebhookConfigDto get(Long partnerId) {
        return webhookRepository.findByPartnerId(partnerId).map(w -> toDto(w, null))
                .orElse(new WebhookConfigDto(null, List.of(), false, false, null, 0, null));
    }

    @Transactional
    public WebhookConfigDto save(Long partnerId, WebhookConfigRequest req) {
        String url = urlValidator.validate(req.url());
        List<String> events = validateEvents(req.events());
        PartnerWebhook w = webhookRepository.findByPartnerId(partnerId).orElse(null);
        String newSecret = null;
        Map<String, Object> old = null;
        if (w == null) {
            w = new PartnerWebhook();
            w.setPartnerId(partnerId);
            newSecret = newSecret();
            w.setSecretEnc(crypto.encrypt(newSecret));
        } else {
            old = summary(w);
        }
        w.setUrl(url);
        w.setEvents(events);
        w.setActive(req.isActive() == null || req.isActive());
        if (w.isActive()) {
            w.setConsecutiveFailures(0);
        }
        webhookRepository.save(w);
        auditService.log("WEBHOOK_UPDATE", "PARTNER_WEBHOOK", w.getId(), old, summary(w), partnerId);
        return toDto(w, newSecret);
    }

    @Transactional
    public SecretDto rotateSecret(Long partnerId) {
        PartnerWebhook w = require(partnerId);
        String secret = newSecret();
        w.setSecretEnc(crypto.encrypt(secret));
        webhookRepository.save(w);
        auditService.log("WEBHOOK_SECRET_ROTATE", "PARTNER_WEBHOOK", w.getId(), null, null, partnerId);
        return new SecretDto(secret);
    }

    /** D9: delivers a test event right away (no retries) and returns the outcome. */
    @Transactional
    public TestResultDto test(Long partnerId, String eventType) {
        PartnerWebhook w = require(partnerId);
        urlValidator.validate(w.getUrl());
        String type = eventType == null || eventType.isBlank() ? PartnerEvent.TEST_PING : eventType;
        if (!PartnerEvent.TEST_PING.equals(type) && !SUBSCRIBABLE.contains(type)) {
            throw AppException.field("eventType", "Unknown event type");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "This is a test event from CB WhatsApp Gateway");
        data.put("test", true);
        WebhookDeliveryLog log = newLog(w, type, data);
        log.setAttempt(1);
        WebhookSender.Result r = sender.send(w.getUrl(), crypto.decrypt(w.getSecretEnc()), type, log.getEventId(), log.getPayload());
        log.setResponseCode(r.responseCode());
        log.setResponseBody(r.responseBody());
        log.setDurationMs(r.durationMs());
        log.setStatus(r.success() ? DeliveryStatus.SUCCESS : DeliveryStatus.DEAD);
        log.setNextRetryAt(null);
        logRepository.save(log);
        if (r.success()) {
            w.setLastSuccessAt(Instant.now());
            w.setConsecutiveFailures(0);
            webhookRepository.save(w);
        }
        return new TestResultDto(log.getStatus().name(), r.responseCode(), r.durationMs(), r.responseBody());
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryLogDto> logs(Long partnerId, String status, String eventType, LocalDate from, LocalDate to,
                                             Integer page, Integer size) {
        Specification<WebhookDeliveryLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("partnerId"), partnerId));
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), parseStatus(status)));
            }
            if (eventType != null && !eventType.isBlank()) {
                ps.add(cb.equal(root.get("eventType"), eventType));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeUtil.startOfDayIst(from)));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), TimeUtil.endOfDayIstExclusive(to)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var pageable = Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(logRepository.findAll(spec, pageable), PartnerWebhookService::toLogDto);
    }

    /** D11: schedules a delivery to be tried again now. */
    @Transactional
    public DeliveryLogDto retry(Long partnerId, Long logId) {
        WebhookDeliveryLog log = logRepository.findByIdAndPartnerId(logId, partnerId)
                .orElseThrow(() -> AppException.notFound("Delivery"));
        if (log.getStatus() == DeliveryStatus.SUCCESS) {
            throw AppException.conflict("This event was already delivered");
        }
        log.setStatus(DeliveryStatus.PENDING);
        log.setNextRetryAt(Instant.now());
        logRepository.save(log);
        return toLogDto(log);
    }

    /** Called (after commit) for every PartnerEvent: queues a delivery if the partner subscribed. */
    @Transactional
    public void enqueue(PartnerEvent event) {
        if (event.partnerId() == null) {
            return;
        }
        PartnerWebhook w = webhookRepository.findByPartnerId(event.partnerId()).orElse(null);
        if (w == null || !w.isActive()) {
            return;
        }
        boolean subscribed = PartnerEvent.TEST_PING.equals(event.type())
                || (w.getEvents() != null && w.getEvents().contains(event.type()));
        if (!subscribed) {
            return;
        }
        WebhookDeliveryLog log = newLog(w, event.type(), event.data());
        log.setStatus(DeliveryStatus.PENDING);
        log.setNextRetryAt(Instant.now());
        logRepository.save(log);
    }

    /** Delivers one claimed row and records the outcome with the retry schedule. */
    @Transactional
    public void deliver(Long logId) {
        WebhookDeliveryLog log = logRepository.findById(logId).orElse(null);
        if (log == null || log.getStatus() == DeliveryStatus.SUCCESS || log.getStatus() == DeliveryStatus.DEAD) {
            return;
        }
        PartnerWebhook w = webhookRepository.findById(log.getPartnerWebhookId()).orElse(null);
        if (w == null || !w.isActive()) {
            log.setStatus(DeliveryStatus.DEAD);
            log.setResponseBody("Webhook is disabled");
            log.setNextRetryAt(null);
            logRepository.save(log);
            return;
        }
        log.setAttempt(log.getAttempt() + 1);
        WebhookSender.Result r;
        if (!urlValidator.isDeliverable(w.getUrl())) {
            r = new WebhookSender.Result(false, null, "Webhook URL is not allowed (must be a public https URL)", 0);
        } else {
            r = sender.send(w.getUrl(), crypto.decrypt(w.getSecretEnc()), log.getEventType(), log.getEventId(), log.getPayload());
        }
        log.setResponseCode(r.responseCode());
        log.setResponseBody(r.responseBody());
        log.setDurationMs(r.durationMs());
        if (r.success()) {
            log.setStatus(DeliveryStatus.SUCCESS);
            log.setNextRetryAt(null);
            w.setConsecutiveFailures(0);
            w.setLastSuccessAt(Instant.now());
        } else {
            int failures = log.getAttempt();
            if (failures > RETRY_DELAYS.size()) {
                log.setStatus(DeliveryStatus.DEAD);
                log.setNextRetryAt(null);
            } else {
                log.setStatus(DeliveryStatus.FAILED);
                log.setNextRetryAt(Instant.now().plus(RETRY_DELAYS.get(failures - 1)));
            }
            w.setConsecutiveFailures(w.getConsecutiveFailures() + 1);
        }
        logRepository.save(log);
        webhookRepository.save(w);
    }

    private WebhookDeliveryLog newLog(PartnerWebhook w, String type, Map<String, Object> data) {
        String eventId = Ulids.next();
        ObjectNode payload = JsonUtil.MAPPER.createObjectNode();
        payload.put("event", type);
        payload.put("eventId", eventId);
        payload.put("createdAt", Instant.now().toString());
        payload.set("data", JsonUtil.MAPPER.valueToTree(data == null ? Map.of() : data));
        WebhookDeliveryLog log = new WebhookDeliveryLog();
        log.setPartnerWebhookId(w.getId());
        log.setPartnerId(w.getPartnerId());
        log.setEventId(eventId);
        log.setEventType(type);
        log.setPayload(payload.toString());
        log.setAttempt(0);
        log.setStatus(DeliveryStatus.PENDING);
        return log;
    }

    private PartnerWebhook require(Long partnerId) {
        return webhookRepository.findByPartnerId(partnerId)
                .orElseThrow(() -> AppException.notFound("Webhook (save a webhook URL first)"));
    }

    private static List<String> validateEvents(List<String> events) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String e : events) {
            if (!SUBSCRIBABLE.contains(e)) {
                throw AppException.field("events", "Unknown event: " + e);
            }
            out.add(e);
        }
        if (out.isEmpty()) {
            throw AppException.field("events", "Choose at least one event");
        }
        return new ArrayList<>(out);
    }

    private static DeliveryStatus parseStatus(String s) {
        try {
            return DeliveryStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw AppException.field("status", "Unknown status");
        }
    }

    private static String newSecret() {
        return "whsec_" + TokenGenerator.alphanumeric(32);
    }

    private static Map<String, Object> summary(PartnerWebhook w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", w.getUrl());
        m.put("events", w.getEvents());
        m.put("active", w.isActive());
        return m;
    }

    private static WebhookConfigDto toDto(PartnerWebhook w, String secret) {
        return new WebhookConfigDto(w.getUrl(), w.getEvents(), w.isActive(), w.getSecretEnc() != null,
                w.getLastSuccessAt(), w.getConsecutiveFailures(), secret);
    }

    static DeliveryLogDto toLogDto(WebhookDeliveryLog l) {
        return new DeliveryLogDto(l.getId(), l.getEventId(), l.getEventType(), JsonUtil.readTree(l.getPayload()), l.getAttempt(),
                l.getStatus().name(), l.getResponseCode(), l.getResponseBody(), l.getDurationMs(), l.getNextRetryAt(),
                l.getCreatedAt(), l.getUpdatedAt());
    }
}
