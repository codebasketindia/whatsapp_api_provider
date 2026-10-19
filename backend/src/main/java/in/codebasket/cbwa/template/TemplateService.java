package in.codebasket.cbwa.template;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaGraphClient;
import in.codebasket.cbwa.meta.MetaTemplateApi;
import in.codebasket.cbwa.partner.PartnerEvent;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.PartnerPhoneNumber;
import in.codebasket.cbwa.sender.PartnerPhoneNumberRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.template.dto.TemplateDtos.ComponentDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.HeaderUploadDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.TemplateDto;
import in.codebasket.cbwa.template.dto.TemplateDtos.TemplateRequest;
import in.codebasket.cbwa.waba.WabaOwnerType;
import in.codebasket.cbwa.waba.WabaStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Templates (F6, TP1-TP6, TP11-TP16, TP18). Templates on a partner's own WABA go straight to Meta;
 * templates a partner creates on a platform WABA wait in the admin review queue (PENDING_REVIEW).
 * Meta calls are made outside DB transactions.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private static final Set<TemplateStatus> LOCAL_EDITABLE = Set.of(TemplateStatus.DRAFT, TemplateStatus.PENDING_REVIEW,
            TemplateStatus.INTERNAL_REJECTED);
    private static final Set<TemplateStatus> META_EDITABLE = Set.of(TemplateStatus.APPROVED, TemplateStatus.REJECTED,
            TemplateStatus.PAUSED);

    private final TemplateRepository templateRepository;
    private final TemplateStore store;
    private final WhatsappAccountRepository wabaRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PartnerRepository partnerRepository;
    private final SenderAccessService access;
    private final MetaTemplateApi templateApi;
    private final MetaGraphClient graphClient;
    private final CryptoService crypto;
    private final FileStorageService storage;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    // ------------------------------------------------------------------ queries

    public PageResponse<TemplateDto> partnerList(Long partnerId, Long wabaDbId, Long senderId, String status, String category,
                                                 String language, String search, Integer page, Integer size) {
        return tx.execute(st -> {
            Set<Long> platformWabas = platformWabaIds(partnerId);
            Long wabaFilter = senderId != null ? access.requirePartnerSender(partnerId, senderId).getWhatsappAccountId() : wabaDbId;
            Specification<Template> spec = (root, q, cb) -> {
                List<Predicate> ps = new ArrayList<>();
                Predicate mine = cb.equal(root.get("partnerId"), partnerId);
                ps.add(platformWabas.isEmpty() ? mine
                        : cb.or(mine, cb.and(cb.isNull(root.get("partnerId")), root.get("whatsappAccountId").in(platformWabas))));
                filters(ps, root, cb, wabaFilter, status, category, language, search);
                return cb.and(ps.toArray(Predicate[]::new));
            };
            return PageResponse.of(templateRepository.findAll(spec, Paging.of(page, size, null,
                    Set.of("name", "createdAt", "updatedAt", "status"), Sort.by(Sort.Direction.DESC, "id"))), store::toDto);
        });
    }

    public PageResponse<TemplateDto> adminList(Long partnerId, Long wabaDbId, String status, String category, String search,
                                               Integer page, Integer size) {
        return tx.execute(st -> {
            Specification<Template> spec = (root, q, cb) -> {
                List<Predicate> ps = new ArrayList<>();
                if (partnerId != null) {
                    ps.add(cb.equal(root.get("partnerId"), partnerId));
                }
                filters(ps, root, cb, wabaDbId, status, category, null, search);
                return cb.and(ps.toArray(Predicate[]::new));
            };
            return PageResponse.of(templateRepository.findAll(spec, Paging.of(page, size, null,
                    Set.of("name", "createdAt", "updatedAt", "status"), Sort.by(Sort.Direction.DESC, "id"))), store::toDto);
        });
    }

    public PageResponse<TemplateDto> reviewQueue(Integer page, Integer size) {
        return adminList(null, null, TemplateStatus.PENDING_REVIEW.name(), null, null, page, size);
    }

    public TemplateDto partnerGet(Long partnerId, Long id) {
        return tx.execute(st -> store.toDto(partnerTemplate(partnerId, id, false)));
    }

    public Template loadForPartner(Long partnerId, Long id) {
        return tx.execute(st -> partnerTemplate(partnerId, id, false));
    }

    public Template load(Long id) {
        return tx.execute(st -> find(id));
    }

    public WhatsappAccount wabaForAdmin(Long wabaDbId, Long senderId) {
        return adminWaba(wabaDbId, senderId);
    }

    public TemplateDto adminGet(Long id) {
        return tx.execute(st -> store.toDto(find(id)));
    }

    // ------------------------------------------------------------------ create / edit

    /** TP2 (partner, partnerId != null) and TP16 (admin, partnerId == null; req.partnerId optional owner). */
    public TemplateDto create(Long partnerId, TemplateRequest req) {
        boolean admin = partnerId == null;
        WhatsappAccount waba = admin ? adminWaba(req.wabaDbId(), req.senderId()) : partnerWaba(partnerId, req.wabaDbId(), req.senderId());
        Long owner = admin ? ownerForAdmin(waba, req.partnerId()) : (waba.getOwnerType() == WabaOwnerType.PARTNER ? waba.getPartnerId() : partnerId);
        ParameterFormat format = format(req.parameterFormat());
        String name = req.name().trim();
        String category = req.category().toUpperCase();
        TemplateValidator.validate(name, req.language(), category, format, req.components());
        List<ComponentDto> components = req.components().stream().map(c -> TemplatePayloads.normalise(c, format)).toList();
        tx.executeWithoutResult(st -> {
            if (templateRepository.findByWhatsappAccountIdAndNameAndLanguage(waba.getId(), name, req.language()).isPresent()) {
                throw AppException.field("name", "A template with this name and language already exists");
            }
        });
        boolean needsReview = !admin && waba.getOwnerType() == WabaOwnerType.PLATFORM;
        boolean submitNow = req.submit() && !needsReview;
        JsonNode metaResponse = null;
        if (submitNow) {
            components = withHandles(waba, components, owner);
            metaResponse = templateApi.create(waba.getWabaId(), createPayload(name, req.language(), category, format, components),
                    token(waba));
        }
        final List<ComponentDto> finalComponents = components;
        final JsonNode meta = metaResponse;
        Long id = tx.execute(st -> {
            Template t = new Template();
            t.setUid(Ulids.next());
            t.setPartnerId(owner);
            t.setWhatsappAccountId(waba.getId());
            t.setName(name);
            t.setLanguage(req.language());
            t.setCategory(MessageCategory.valueOf(category));
            t.setParameterFormat(format);
            if (meta != null) {
                applyMetaResponse(t, meta);
            } else {
                t.setStatus(req.submit() && needsReview ? TemplateStatus.PENDING_REVIEW : TemplateStatus.DRAFT);
            }
            templateRepository.save(t);
            store.replaceComponents(t, finalComponents);
            auditService.log("TEMPLATE_CREATE", "TEMPLATE", t.getUid(), null,
                    Map.of("name", name, "language", req.language(), "status", t.getStatus().name()), owner);
            if (t.getStatus() == TemplateStatus.PENDING_REVIEW) {
                notifications.notifyAdmins("TEMPLATE_REVIEW", NotificationService.INFO, "Template waiting for review",
                        "Template " + name + " (" + req.language() + ") needs review.", "/admin/templates/review");
            }
            return t.getId();
        });
        return adminGet(id);
    }

    /** TP4: local edit for draft/review states; Meta edit for approved/rejected/paused templates. */
    public TemplateDto update(Long partnerId, Long id, TemplateRequest req) {
        boolean admin = partnerId == null;
        Template t = tx.execute(st -> admin ? find(id) : partnerTemplate(partnerId, id, true));
        WhatsappAccount waba = wabaRepository.findById(t.getWhatsappAccountId()).orElseThrow();
        ParameterFormat format = format(req.parameterFormat());
        String category = req.category().toUpperCase();
        boolean submitted = t.getMetaTemplateId() != null;
        String name = submitted ? t.getName() : req.name().trim();
        String language = submitted ? t.getLanguage() : req.language();
        if (submitted && (!t.getName().equals(req.name().trim()) || !t.getLanguage().equals(req.language()))) {
            throw new AppException(ErrorCode.TEMPLATE_INVALID_STATE, "Name and language cannot be changed after submission");
        }
        TemplateValidator.validate(name, language, category, format, req.components());
        List<ComponentDto> components = req.components().stream().map(c -> TemplatePayloads.normalise(c, format)).toList();
        boolean needsReview = !admin && waba.getOwnerType() == WabaOwnerType.PLATFORM;

        if (LOCAL_EDITABLE.contains(t.getStatus()) || (needsReview && META_EDITABLE.contains(t.getStatus()))) {
            if (!submitted && (!t.getName().equals(name) || !t.getLanguage().equals(language))) {
                tx.executeWithoutResult(st -> templateRepository.findByWhatsappAccountIdAndNameAndLanguage(waba.getId(), name, language)
                        .filter(o -> !o.getId().equals(id)).ifPresent(o -> {
                            throw AppException.field("name", "A template with this name and language already exists");
                        }));
            }
            boolean toReview = needsReview && (req.submit() || t.getStatus() != TemplateStatus.DRAFT);
            if (!needsReview && req.submit()) {
                // own WABA or admin: save locally then submit to Meta
                saveLocal(t.getId(), name, language, category, format, components, TemplateStatus.DRAFT);
                return submit(partnerId, id);
            }
            saveLocal(t.getId(), name, language, category, format, components,
                    toReview ? TemplateStatus.PENDING_REVIEW : TemplateStatus.DRAFT);
            return adminGet(id);
        }
        if (META_EDITABLE.contains(t.getStatus()) && submitted) {
            if (t.getStatus() == TemplateStatus.APPROVED && !t.getCategory().name().equals(category)) {
                throw new AppException(ErrorCode.TEMPLATE_INVALID_STATE, "The category of an approved template cannot be changed");
            }
            List<ComponentDto> withHandles = withHandles(waba, components, t.getPartnerId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("components", TemplatePayloads.toMeta(withHandles, category));
            if (t.getStatus() != TemplateStatus.APPROVED) {
                payload.put("category", category);
            }
            templateApi.edit(t.getMetaTemplateId(), payload, token(waba));
            saveLocal(t.getId(), name, language, category, format, withHandles, TemplateStatus.PENDING);
            tx.executeWithoutResult(st -> {
                Template fresh = find(id);
                fresh.setSubmittedAt(Instant.now());
                fresh.setRejectedReason(null);
                templateRepository.save(fresh);
                auditService.log("TEMPLATE_EDIT", "TEMPLATE", fresh.getUid(), null, Map.of("submittedToMeta", true), fresh.getPartnerId());
            });
            return adminGet(id);
        }
        throw new AppException(ErrorCode.TEMPLATE_INVALID_STATE,
                "A template that is " + t.getStatus().name().toLowerCase().replace('_', ' ') + " cannot be edited");
    }

    /** TP6 (partner) - submit a draft. Platform WABA templates of partners go to admin review. */
    public TemplateDto submit(Long partnerId, Long id) {
        boolean admin = partnerId == null;
        Template t = tx.execute(st -> admin ? find(id) : partnerTemplate(partnerId, id, true));
        if (!Set.of(TemplateStatus.DRAFT, TemplateStatus.INTERNAL_REJECTED, TemplateStatus.REJECTED).contains(t.getStatus())) {
            throw new AppException(ErrorCode.TEMPLATE_INVALID_STATE, "Only drafts and rejected templates can be submitted");
        }
        WhatsappAccount waba = wabaRepository.findById(t.getWhatsappAccountId()).orElseThrow();
        if (!admin && waba.getOwnerType() == WabaOwnerType.PLATFORM) {
            tx.executeWithoutResult(st -> {
                Template fresh = find(id);
                fresh.setStatus(TemplateStatus.PENDING_REVIEW);
                templateRepository.save(fresh);
                notifications.notifyAdmins("TEMPLATE_REVIEW", NotificationService.INFO, "Template waiting for review",
                        "Template " + fresh.getName() + " (" + fresh.getLanguage() + ") needs review.", "/admin/templates/review");
            });
            return adminGet(id);
        }
        return sendToMeta(id, null);
    }

    /** TP14: admin approves a reviewed template and submits it to Meta. */
    public TemplateDto approveAndSubmit(Long id, String note) {
        Template t = tx.execute(st -> find(id));
        if (t.getStatus() != TemplateStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.TEMPLATE_INVALID_STATE, "Only templates waiting for review can be approved");
        }
        TemplateDto dto = sendToMeta(id, note);
        tx.executeWithoutResult(st -> {
            Template fresh = find(id);
            fresh.setReviewedBy(in.codebasket.cbwa.auth.SecurityUtils.currentUserIdOrNull());
            fresh.setReviewedAt(Instant.now());
            fresh.setAdminReviewNote(note);
            templateRepository.save(fresh);
            auditService.log("TEMPLATE_REVIEW_APPROVE", "TEMPLATE", fresh.getUid(), null, Map.of("note", note == null ? "" : note),
                    fresh.getPartnerId());
            notifyPartner(fresh, "Template sent to WhatsApp", "Template " + fresh.getName()
                    + " passed our review and was sent to WhatsApp for approval.", NotificationService.INFO);
        });
        return adminGet(dto.id());
    }

    /** TP15 */
    public TemplateDto reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw AppException.field("reason", "Please give a reason");
        }
        return tx.execute(st -> {
            Template t = find(id);
            if (t.getStatus() != TemplateStatus.PENDING_REVIEW) {
                throw new AppException(ErrorCode.TEMPLATE_INVALID_STATE, "Only templates waiting for review can be rejected");
            }
            t.setStatus(TemplateStatus.INTERNAL_REJECTED);
            t.setRejectedReason(reason.trim());
            t.setAdminReviewNote(reason.trim());
            t.setReviewedBy(in.codebasket.cbwa.auth.SecurityUtils.currentUserIdOrNull());
            t.setReviewedAt(Instant.now());
            templateRepository.save(t);
            auditService.log("TEMPLATE_REVIEW_REJECT", "TEMPLATE", t.getUid(), null, Map.of("reason", reason), t.getPartnerId());
            notifyPartner(t, "Template needs changes", "Template " + t.getName() + " was not approved: " + reason.trim(),
                    NotificationService.WARNING);
            return store.toDto(t);
        });
    }

    /** TP5 / TP18: deleting at Meta removes all languages of the name. */
    public void delete(Long partnerId, Long id) {
        boolean admin = partnerId == null;
        Template t = tx.execute(st -> admin ? find(id) : partnerTemplate(partnerId, id, true));
        WhatsappAccount waba = wabaRepository.findById(t.getWhatsappAccountId()).orElseThrow();
        boolean atMeta = t.getMetaTemplateId() != null;
        if (atMeta) {
            try {
                templateApi.deleteByName(waba.getWabaId(), t.getName(), token(waba));
            } catch (MetaApiException e) {
                if (e.getHttpStatus() != 404) {
                    throw e;
                }
            }
        }
        tx.executeWithoutResult(st -> {
            List<Template> targets = atMeta ? templateRepository.findByWhatsappAccountIdAndName(waba.getId(), t.getName())
                    : List.of(find(id));
            for (Template x : targets) {
                if (x.getMetaTemplateId() == null && x.getId().equals(id)) {
                    templateRepository.delete(x);
                } else {
                    x.setStatus(TemplateStatus.DELETED);
                    templateRepository.save(x);
                }
            }
            auditService.log("TEMPLATE_DELETE", "TEMPLATE", t.getUid(), Map.of("name", t.getName()), null, t.getPartnerId());
        });
    }

    /** TP11: header sample upload -> Resumable Upload API handle. */
    public HeaderUploadDto uploadHeader(Long partnerId, Long wabaDbId, Long senderId, MultipartFile file) {
        WhatsappAccount waba = partnerId == null ? adminWaba(wabaDbId, senderId) : partnerWaba(partnerId, wabaDbId, senderId);
        Set<String> allowed = Set.of("image/jpeg", "image/png", "video/mp4", "application/pdf");
        StoredFile f = storage.store(file, FileCategory.TEMPLATE_HEADER, partnerId, allowed, 16L * 1024 * 1024);
        String handle = graphClient.resumableUpload(storage.readBytes(f), f.getMimeType(), f.getOriginalName(), token(waba));
        return new HeaderUploadDto(f.getUid(), handle, f.getMimeType());
    }

    // ------------------------------------------------------------------ helpers

    private TemplateDto sendToMeta(Long id, String note) {
        Template t = tx.execute(st -> find(id));
        WhatsappAccount waba = wabaRepository.findById(t.getWhatsappAccountId()).orElseThrow();
        List<ComponentDto> components = tx.execute(st -> store.components(t));
        components = withHandles(waba, components, t.getPartnerId());
        String token = token(waba);
        JsonNode res;
        if (t.getMetaTemplateId() != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("components", TemplatePayloads.toMeta(components, t.getCategory().name()));
            if (t.getStatus() != TemplateStatus.APPROVED) {
                payload.put("category", t.getCategory().name());
            }
            templateApi.edit(t.getMetaTemplateId(), payload, token);
            res = JsonUtil.MAPPER.createObjectNode().put("id", t.getMetaTemplateId()).put("status", "PENDING");
        } else {
            res = templateApi.create(waba.getWabaId(), createPayload(t.getName(), t.getLanguage(), t.getCategory().name(),
                    t.getParameterFormat(), components), token);
        }
        final List<ComponentDto> finalComponents = components;
        tx.executeWithoutResult(st -> {
            Template fresh = find(id);
            applyMetaResponse(fresh, res);
            fresh.setRejectedReason(null);
            templateRepository.save(fresh);
            store.replaceComponents(fresh, finalComponents);
            auditService.log("TEMPLATE_SUBMIT", "TEMPLATE", fresh.getUid(), null, Map.of("status", fresh.getStatus().name()),
                    fresh.getPartnerId());
        });
        return adminGet(id);
    }

    private void applyMetaResponse(Template t, JsonNode res) {
        if (res.hasNonNull("id")) {
            t.setMetaTemplateId(res.get("id").asText());
        }
        String status = res.path("status").asText("PENDING");
        t.setStatus(mapStatus(status, TemplateStatus.PENDING));
        if (res.hasNonNull("category")) {
            String c = res.get("category").asText().toUpperCase();
            if (!c.equals(t.getCategory() == null ? null : t.getCategory().name())) {
                try {
                    MessageCategory mc = MessageCategory.valueOf(c);
                    if (t.getCategory() != null) {
                        t.setPreviousCategory(t.getCategory().name());
                    }
                    t.setCategory(mc);
                } catch (IllegalArgumentException ignored) {
                    // unknown category from Meta - keep ours
                }
            }
        }
        t.setSubmittedAt(Instant.now());
        t.setMetaPayload(res.toString());
    }

    public static TemplateStatus mapStatus(String metaStatus, TemplateStatus fallback) {
        if (metaStatus == null) {
            return fallback;
        }
        try {
            return TemplateStatus.valueOf(metaStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private Map<String, Object> createPayload(String name, String language, String category, ParameterFormat format,
                                              List<ComponentDto> components) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("language", language);
        p.put("category", category);
        p.put("parameter_format", format.name());
        p.put("components", TemplatePayloads.toMeta(components, category));
        return p;
    }

    /** Uploads stored header samples that have no Meta handle yet. */
    private List<ComponentDto> withHandles(WhatsappAccount waba, List<ComponentDto> components, Long partnerId) {
        List<ComponentDto> out = new ArrayList<>();
        for (ComponentDto c : components) {
            boolean media = "HEADER".equals(c.type()) && c.format() != null && Set.of("IMAGE", "VIDEO", "DOCUMENT").contains(c.format());
            if (media && (c.headerHandle() == null || c.headerHandle().isBlank()) && c.headerMediaFileUid() != null) {
                StoredFile f = storage.get(c.headerMediaFileUid());
                if (f.getPartnerId() != null && partnerId != null && !f.getPartnerId().equals(partnerId)) {
                    throw AppException.field("header", "Header sample file not found");
                }
                String handle = graphClient.resumableUpload(storage.readBytes(f), f.getMimeType(), f.getOriginalName(), token(waba));
                out.add(new ComponentDto(c.type(), c.format(), null, null, null, c.headerMediaFileUid(), handle, null, null));
            } else {
                out.add(c);
            }
        }
        return out;
    }

    private void saveLocal(Long id, String name, String language, String category, ParameterFormat format,
                           List<ComponentDto> components, TemplateStatus status) {
        tx.executeWithoutResult(st -> {
            Template t = find(id);
            Map<String, Object> old = Map.of("status", t.getStatus().name(), "category", t.getCategory().name());
            t.setName(name);
            t.setLanguage(language);
            t.setCategory(MessageCategory.valueOf(category));
            t.setParameterFormat(format);
            t.setStatus(status);
            if (status == TemplateStatus.DRAFT || status == TemplateStatus.PENDING_REVIEW) {
                t.setRejectedReason(null);
            }
            templateRepository.save(t);
            store.replaceComponents(t, components);
            auditService.log("TEMPLATE_UPDATE", "TEMPLATE", t.getUid(), old, Map.of("status", status.name(), "category", category),
                    t.getPartnerId());
            if (status == TemplateStatus.PENDING_REVIEW) {
                notifications.notifyAdmins("TEMPLATE_REVIEW", NotificationService.INFO, "Template waiting for review",
                        "Template " + name + " (" + language + ") needs review.", "/admin/templates/review");
            }
        });
    }

    private void notifyPartner(Template t, String title, String body, String severity) {
        if (t.getPartnerId() == null) {
            return;
        }
        String email = partnerRepository.findById(t.getPartnerId()).map(p -> p.getEmail()).orElse(null);
        notifications.notifyPartner(t.getPartnerId(), "TEMPLATE_STATUS", severity, title, body, "/partner/templates/" + t.getId(), email);
        events.publishEvent(new PartnerEvent(t.getPartnerId(), PartnerEvent.TEMPLATE_STATUS,
                Map.of("templateId", t.getId(), "name", t.getName(), "language", t.getLanguage(), "status", t.getStatus().name())));
    }

    private void filters(List<Predicate> ps, jakarta.persistence.criteria.Root<Template> root,
                         jakarta.persistence.criteria.CriteriaBuilder cb, Long wabaDbId, String status, String category,
                         String language, String search) {
        if (wabaDbId != null) {
            ps.add(cb.equal(root.get("whatsappAccountId"), wabaDbId));
        }
        if (status != null && !status.isBlank()) {
            List<TemplateStatus> statuses = new ArrayList<>();
            for (String s : status.split(",")) {
                statuses.add(TemplateStatus.valueOf(s.trim()));
            }
            ps.add(root.get("status").in(statuses));
        } else {
            ps.add(cb.notEqual(root.get("status"), TemplateStatus.DELETED));
        }
        if (category != null && !category.isBlank()) {
            ps.add(cb.equal(root.get("category"), MessageCategory.valueOf(category)));
        }
        if (language != null && !language.isBlank()) {
            ps.add(cb.equal(root.get("language"), language));
        }
        if (search != null && !search.isBlank()) {
            ps.add(cb.like(cb.lower(root.get("name")), "%" + search.trim().toLowerCase() + "%"));
        }
    }

    /** Platform WABAs where the partner has an active assigned number. */
    Set<Long> platformWabaIds(Long partnerId) {
        Set<Long> ids = new HashSet<>();
        for (PartnerPhoneNumber a : assignmentRepository.findByPartnerIdAndActiveTrue(partnerId)) {
            phoneNumberRepository.findById(a.getPhoneNumberId()).map(PhoneNumber::getWhatsappAccountId)
                    .flatMap(wabaRepository::findById)
                    .filter(w -> w.getOwnerType() == WabaOwnerType.PLATFORM)
                    .ifPresent(w -> ids.add(w.getId()));
        }
        return ids;
    }

    WhatsappAccount partnerWaba(Long partnerId, Long wabaDbId, Long senderId) {
        return tx.execute(st -> {
            WhatsappAccount w;
            if (senderId != null) {
                PhoneNumber pn = access.requirePartnerSender(partnerId, senderId);
                w = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
            } else if (wabaDbId != null) {
                w = wabaRepository.findById(wabaDbId).orElseThrow(() -> AppException.field("wabaDbId", "WhatsApp account not found"));
                boolean own = w.getOwnerType() == WabaOwnerType.PARTNER && partnerId.equals(w.getPartnerId());
                if (!own && !platformWabaIds(partnerId).contains(w.getId())) {
                    throw AppException.field("wabaDbId", "WhatsApp account not found");
                }
            } else {
                PhoneNumber pn = access.defaultSender(partnerId)
                        .orElseThrow(() -> AppException.field("wabaDbId", "Choose the WhatsApp number for this template"));
                w = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
            }
            if (w.getStatus() == WabaStatus.DISCONNECTED) {
                throw AppException.field("wabaDbId", "This WhatsApp account is disconnected");
            }
            return w;
        });
    }

    private WhatsappAccount adminWaba(Long wabaDbId, Long senderId) {
        return tx.execute(st -> {
            if (senderId != null) {
                PhoneNumber pn = phoneNumberRepository.findById(senderId).orElseThrow(() -> AppException.field("senderId", "Sender not found"));
                return wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
            }
            if (wabaDbId == null) {
                throw AppException.field("wabaDbId", "Choose the WhatsApp account");
            }
            return wabaRepository.findById(wabaDbId).orElseThrow(() -> AppException.field("wabaDbId", "WhatsApp account not found"));
        });
    }

    private Long ownerForAdmin(WhatsappAccount waba, Long requested) {
        if (waba.getOwnerType() == WabaOwnerType.PARTNER) {
            return waba.getPartnerId();
        }
        if (requested != null) {
            if (!platformWabaIds(requested).contains(waba.getId())) {
                throw AppException.field("partnerId", "This partner has no number on the selected WhatsApp account");
            }
        }
        return requested;
    }

    private Template partnerTemplate(Long partnerId, Long id, boolean write) {
        Template t = find(id);
        if (partnerId.equals(t.getPartnerId())) {
            return t;
        }
        if (!write && t.getPartnerId() == null && platformWabaIds(partnerId).contains(t.getWhatsappAccountId())) {
            return t;
        }
        throw AppException.notFound("Template");
    }

    private Template find(Long id) {
        return templateRepository.findById(id).orElseThrow(() -> AppException.notFound("Template"));
    }

    private String token(WhatsappAccount waba) {
        return crypto.decrypt(waba.getAccessTokenEnc());
    }

    private static ParameterFormat format(String f) {
        if (f == null || f.isBlank()) {
            return ParameterFormat.POSITIONAL;
        }
        try {
            return ParameterFormat.valueOf(f.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw AppException.field("parameterFormat", "Use POSITIONAL or NAMED");
        }
    }
}
