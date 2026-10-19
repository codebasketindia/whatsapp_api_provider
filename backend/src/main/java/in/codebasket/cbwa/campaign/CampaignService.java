package in.codebasket.cbwa.campaign;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.campaign.CampaignPricing.CountryBucket;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.AudienceRequest;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.CampaignDto;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.CampaignRequest;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.CostEstimateDto;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.Counts;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.HeaderMedia;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.Mapping;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.PartnerRef;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.PrepareCounts;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.PrepareResult;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.PreviewLine;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.SenderRef;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.TemplateRef;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.UploadDto;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.storage.FileCategory;
import in.codebasket.cbwa.common.storage.FileStorageService;
import in.codebasket.cbwa.common.storage.StoredFile;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.contact.Contact;
import in.codebasket.cbwa.contact.ContactGroupMemberRepository;
import in.codebasket.cbwa.contact.ContactGroupRepository;
import in.codebasket.cbwa.contact.ContactImportService;
import in.codebasket.cbwa.contact.ContactRepository;
import in.codebasket.cbwa.contact.ContactSource;
import in.codebasket.cbwa.contact.OptOutRepository;
import in.codebasket.cbwa.contact.TabularFile;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateComponentRepository;
import in.codebasket.cbwa.template.TemplateRepository;
import in.codebasket.cbwa.template.TemplateStatus;
import in.codebasket.cbwa.wallet.Wallet;
import in.codebasket.cbwa.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CP1-CP5, CP11: campaign wizard (upload -> draft -> prepare -> launch). Dispatching and progress
 * tracking live in CampaignDispatcher / CampaignProgressJob; pause/resume/cancel in CampaignControlService.
 */
@Service
@RequiredArgsConstructor
public class CampaignService {

    public static final int MAX_ROWS = 100_000;
    static final String SKIP_INVALID = "INVALID_NUMBER";
    static final String SKIP_DUPLICATE = "DUPLICATE";
    static final String SKIP_OPTED_OUT = "OPTED_OUT";
    static final String SKIP_BLOCKED = "BLOCKED";
    static final String SKIP_MISSING = "MISSING_VARIABLE";
    static final String SKIP_CANCELLED = "CANCELLED";

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final TemplateRepository templateRepository;
    private final TemplateComponentRepository componentRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerRepository partnerRepository;
    private final ContactRepository contactRepository;
    private final ContactGroupRepository groupRepository;
    private final ContactGroupMemberRepository memberRepository;
    private final OptOutRepository optOutRepository;
    private final WalletRepository walletRepository;
    private final SenderAccessService senderAccess;
    private final FileStorageService storage;
    private final CampaignPricing pricing;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    // ------------------------------------------------------------------ CP1
    @Transactional
    public UploadDto upload(Long partnerId, MultipartFile file) {
        TabularFile table = readTable(file);
        StoredFile stored = storage.store(file, FileCategory.IMPORT, partnerId, FileStorageService.SPREADSHEETS,
                ContactImportService.MAX_BYTES);
        return new UploadDto(stored.getUid(), table.columns(), ContactImportService.sample(table), table.rows().size());
    }

    private static TabularFile readTable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.field("file", "Please choose a CSV or Excel file");
        }
        if (file.getSize() > ContactImportService.MAX_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE, "File is larger than 10 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            return TabularFile.read(bytes, mimeOf(bytes), MAX_ROWS);
        } catch (java.io.IOException e) {
            throw new AppException(ErrorCode.IMPORT_INVALID_FILE);
        }
    }

    private static String mimeOf(byte[] bytes) {
        boolean zip = bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K';
        return zip ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv";
    }

    // ------------------------------------------------------------------ CP2 / CP3
    @Transactional
    public CampaignDto create(Long partnerId, CampaignRequest req) {
        Campaign c = new Campaign();
        c.setUid(Ulids.next());
        c.setPartnerId(partnerId);
        c.setStatus(CampaignStatus.DRAFT);
        c.setUnitPrice(MoneyUtil.ZERO);
        c.setEstimatedCost(MoneyUtil.ZERO);
        c.setChargedAmount(MoneyUtil.ZERO);
        c.setRefundedAmount(MoneyUtil.ZERO);
        apply(partnerId, c, req);
        campaignRepository.save(c);
        return toDto(c, false);
    }

    @Transactional
    public CampaignDto update(Long partnerId, Long id, CampaignRequest req) {
        Campaign c = lockOwned(partnerId, id);
        requireStatus(c, CampaignStatus.DRAFT);
        apply(partnerId, c, req);
        c.setPreparedAt(null);
        resetCounts(c);
        jdbc.update("delete from campaign_recipients where campaign_id = ?", c.getId());
        campaignRepository.save(c);
        return toDto(c, false);
    }

    @Transactional
    public void delete(Long partnerId, Long id) {
        Campaign c = lockOwned(partnerId, id);
        requireStatus(c, CampaignStatus.DRAFT);
        jdbc.update("delete from campaign_recipients where campaign_id = ?", c.getId());
        campaignRepository.delete(c);
    }

    private void apply(Long partnerId, Campaign c, CampaignRequest req) {
        PhoneNumber sender = senderAccess.requirePartnerSender(partnerId, req.senderId());
        Template template = templateFor(partnerId, sender, req.templateId());
        AudienceRequest a = req.audience();
        AudienceType type = AudienceType.valueOf(a.type());
        Map<String, Object> audience = new LinkedHashMap<>();
        audience.put("type", type.name());
        c.setSourceFileId(null);
        switch (type) {
            case FILE -> {
                if (a.fileUid() == null || a.fileUid().isBlank()) {
                    throw AppException.field("audience.fileUid", "Upload a file first");
                }
                StoredFile f = storage.get(a.fileUid());
                if (!partnerId.equals(f.getPartnerId()) || f.getCategory() != FileCategory.IMPORT) {
                    throw AppException.field("audience.fileUid", "File not found");
                }
                if (req.mapping() == null || req.mapping().phone() == null || req.mapping().phone().isBlank()) {
                    throw AppException.field("mapping.phone", "Choose the column that contains phone numbers");
                }
                c.setSourceFileId(f.getId());
                audience.put("fileUid", f.getUid());
            }
            case GROUPS -> {
                if (a.groupIds() == null || a.groupIds().isEmpty()) {
                    throw AppException.field("audience.groupIds", "Choose at least one group");
                }
                for (Long gid : a.groupIds()) {
                    groupRepository.findByIdAndPartnerId(gid, partnerId)
                            .orElseThrow(() -> AppException.field("audience.groupIds", "Group not found"));
                }
                audience.put("groupIds", a.groupIds());
            }
            case CONTACTS -> {
                if (a.contactIds() == null || a.contactIds().isEmpty()) {
                    throw AppException.field("audience.contactIds", "Choose at least one contact");
                }
                if (a.contactIds().size() > MAX_ROWS) {
                    throw AppException.field("audience.contactIds", "Too many contacts");
                }
                audience.put("contactIds", a.contactIds());
            }
        }
        Mapping m = req.mapping();
        Map<String, Object> mapping = new LinkedHashMap<>();
        if (m != null) {
            mapping.put("phone", m.phone());
            mapping.put("name", m.name());
            mapping.put("variables", normaliseVariableKeys(m.variables()));
        } else {
            mapping.put("variables", Map.of());
        }
        if (m != null && m.headerMedia() != null && m.headerMedia().fileUid() != null) {
            StoredFile f = storage.get(m.headerMedia().fileUid());
            if (!partnerId.equals(f.getPartnerId())) {
                throw AppException.field("mapping.headerMedia", "File not found");
            }
        }
        c.setName(req.name().trim());
        c.setPhoneNumberId(sender.getId());
        c.setTemplateId(template.getId());
        c.setAudienceType(type);
        c.setAudience(JsonUtil.toJson(audience));
        c.setColumnMapping(JsonUtil.toJson(mapping));
        c.setHeaderMedia(m == null || m.headerMedia() == null ? null : JsonUtil.toJson(m.headerMedia()));
        c.setDefaultCountry(req.defaultCountry() == null || req.defaultCountry().isBlank() ? "IN" : req.defaultCountry().toUpperCase());
        c.setSaveContacts(Boolean.TRUE.equals(req.saveContacts()));
    }

    /** Template must be on the sender's WABA and be either the partner's own or a platform template. */
    Template templateFor(Long partnerId, PhoneNumber sender, Long templateId) {
        Template t = templateRepository.findById(templateId).orElseThrow(() -> AppException.field("templateId", "Template not found"));
        if (!t.getWhatsappAccountId().equals(sender.getWhatsappAccountId())
                || (t.getPartnerId() != null && !t.getPartnerId().equals(partnerId))) {
            throw AppException.field("templateId", "This template does not belong to the selected sender number");
        }
        return t;
    }

    TemplateSpec spec(Template t) {
        return TemplateSpec.of(t, componentRepository.findByTemplateIdOrderByPositionAsc(t.getId()));
    }

    // ------------------------------------------------------------------ CP4
    private record Candidate(int rowNo, String phone, String region, Map<String, String> row, Contact contact) {
    }

    @Transactional
    public PrepareResult prepare(Long partnerId, Long id) {
        Campaign c = lockOwned(partnerId, id);
        requireStatus(c, CampaignStatus.DRAFT);
        PhoneNumber sender = senderAccess.requirePartnerSender(partnerId, c.getPhoneNumberId());
        Template template = templateFor(partnerId, sender, c.getTemplateId());
        TemplateSpec spec = spec(template);
        JsonNode mapping = JsonUtil.readTree(c.getColumnMapping());
        Map<String, String> variables = new LinkedHashMap<>();
        mapping.path("variables").fields().forEachRemaining(e -> variables.put(e.getKey(), e.getValue().asText("")));
        Set<String> required = spec.requiredKeys();
        List<String> unmapped = required.stream().filter(k -> variables.get(k) == null || variables.get(k).isBlank()).toList();
        if (!unmapped.isEmpty()) {
            throw AppException.field("mapping.variables", "Choose a value for: " + String.join(", ", unmapped.stream().map(k -> "{{" + k + "}}").toList()));
        }
        HeaderMedia headerMedia = headerMedia(c);
        if (spec.needsHeaderMedia()) {
            boolean ok = headerMedia != null && ("LOCATION".equals(spec.headerFormat())
                    ? headerMedia.latitude() != null && headerMedia.longitude() != null
                    : notBlank(headerMedia.fileUid()) || notBlank(headerMedia.link()) || notBlank(headerMedia.metaMediaId()));
            if (!ok) {
                throw AppException.field("mapping.headerMedia", "This template needs a header " + spec.headerFormat().toLowerCase());
            }
        }

        // 1. Source rows
        List<Map<String, String>> rows = new ArrayList<>();
        List<Contact> sourceContacts = null;
        JsonNode audience = JsonUtil.readTree(c.getAudience());
        String phoneColumn = mapping.path("phone").asText(null);
        switch (c.getAudienceType()) {
            case FILE -> {
                StoredFile f = storage.get(c.getSourceFileId());
                byte[] bytes = storage.readBytes(f);
                TabularFile table = TabularFile.read(bytes, mimeOf(bytes), MAX_ROWS);
                if (!table.columns().contains(phoneColumn)) {
                    throw AppException.field("mapping.phone", "Column \"" + phoneColumn + "\" is not in the file");
                }
                for (int i = 0; i < table.rows().size(); i++) {
                    rows.add(table.rowMap(i));
                }
            }
            case GROUPS -> {
                List<Long> groupIds = new ArrayList<>();
                audience.path("groupIds").forEach(n -> groupIds.add(n.asLong()));
                List<Long> contactIds = new ArrayList<>(new java.util.LinkedHashSet<>(memberRepository.findContactIdsByGroupIds(groupIds)));
                sourceContacts = loadContacts(partnerId, contactIds);
            }
            case CONTACTS -> {
                List<Long> contactIds = new ArrayList<>();
                audience.path("contactIds").forEach(n -> contactIds.add(n.asLong()));
                sourceContacts = loadContacts(partnerId, contactIds);
            }
        }
        if (sourceContacts != null) {
            sourceContacts.sort(java.util.Comparator.comparing(Contact::getId));
            for (Contact ct : sourceContacts) {
                rows.add(contactRow(ct));
            }
            phoneColumn = "phone";
        }
        if (rows.isEmpty()) {
            throw AppException.field("audience", "The audience has no recipients");
        }

        // 2. Normalise + dedupe
        List<Object[]> inserts = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int invalid = 0;
        int duplicates = 0;
        Instant now = Instant.now();
        Map<String, Contact> contactsByPhone = new HashMap<>();
        if (sourceContacts != null) {
            sourceContacts.forEach(ct -> contactsByPhone.put(ct.getPhone(), ct));
        }
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNo = sourceContacts == null ? i + 2 : i + 1;
            String raw = row.get(phoneColumn);
            var parsed = PhoneUtil.parse(raw, c.getDefaultCountry());
            if (parsed.isEmpty()) {
                invalid++;
                inserts.add(row(c.getId(), null, rowNo, placeholderPhone(raw, rowNo), null, RecipientStatus.SKIPPED,
                        SKIP_INVALID, "Not a valid phone number: " + truncate(raw, 200), now));
                continue;
            }
            String phone = parsed.get().digits();
            if (!seen.add(phone)) {
                duplicates++;
                inserts.add(row(c.getId(), null, rowNo, placeholderPhone(phone, rowNo), null, RecipientStatus.SKIPPED,
                        SKIP_DUPLICATE, "Duplicate of an earlier row (" + phone + ")", now));
                continue;
            }
            candidates.add(new Candidate(rowNo, phone, parsed.get().regionCode(), row, contactsByPhone.get(phone)));
        }

        // 3. Contacts, opt-outs, block list (bulk lookups)
        List<String> phones = candidates.stream().map(Candidate::phone).toList();
        Set<String> optedOut = new HashSet<>();
        for (int i = 0; i < phones.size(); i += 1000) {
            List<String> chunk = phones.subList(i, Math.min(phones.size(), i + 1000));
            optOutRepository.findByPartnerIdAndPhoneIn(partnerId, chunk).forEach(o -> optedOut.add(o.getPhone()));
            if (sourceContacts == null) {
                contactRepository.findByPartnerIdAndPhoneIn(partnerId, chunk).forEach(ct -> contactsByPhone.put(ct.getPhone(), ct));
            }
        }
        Set<String> blocked = new HashSet<>(jdbc.queryForList(
                "select wa_id from blocked_users where phone_number_id = ? and status = 'BLOCKED'", String.class, sender.getId()));
        boolean marketing = template.getCategory() == MessageCategory.MARKETING;
        String nameColumn = mapping.path("name").asText(null);

        int optOutCount = 0;
        int blockedCount = 0;
        int missing = 0;
        Map<String, CountryBucket> buckets = new HashMap<>();
        List<PreviewLine> preview = new ArrayList<>();
        List<Contact> newContacts = new ArrayList<>();
        List<Candidate> pending = new ArrayList<>();
        List<Map<String, String>> pendingVars = new ArrayList<>();
        for (Candidate cand : candidates) {
            Contact contact = contactsByPhone.get(cand.phone());
            Map<String, String> vars = resolve(variables, cand.row(), contact);
            List<String> missingKeys = required.stream().filter(k -> !notBlank(vars.get(k))).toList();
            if (!missingKeys.isEmpty()) {
                missing++;
                inserts.add(row(c.getId(), contact == null ? null : contact.getId(), cand.rowNo(), cand.phone(), vars,
                        RecipientStatus.SKIPPED, SKIP_MISSING, "Missing value for " + String.join(", ", missingKeys), now));
                continue;
            }
            if (marketing && (optedOut.contains(cand.phone()) || (contact != null && contact.isOptedOut()))) {
                optOutCount++;
                inserts.add(row(c.getId(), contact == null ? null : contact.getId(), cand.rowNo(), cand.phone(), vars,
                        RecipientStatus.SKIPPED, SKIP_OPTED_OUT, "Opted out of marketing messages", now));
                continue;
            }
            if (blocked.contains(cand.phone())) {
                blockedCount++;
                inserts.add(row(c.getId(), contact == null ? null : contact.getId(), cand.rowNo(), cand.phone(), vars,
                        RecipientStatus.SKIPPED, SKIP_BLOCKED, "Blocked on this sender number", now));
                continue;
            }
            if (contact == null && c.isSaveContacts()) {
                Contact nc = new Contact();
                nc.setPartnerId(partnerId);
                nc.setPhone(cand.phone());
                nc.setCountryCode(cand.region());
                nc.setSource(ContactSource.CAMPAIGN);
                String name = nameColumn == null ? null : cand.row().get(nameColumn);
                nc.setName(notBlank(name) ? truncate(name.trim(), 120) : null);
                nc.setTags(new ArrayList<>());
                nc.setAttributes(new LinkedHashMap<>());
                newContacts.add(nc);
                contactsByPhone.put(cand.phone(), nc);
            }
            pending.add(cand);
            pendingVars.add(vars);
            buckets.computeIfAbsent(cand.region() == null ? "ZZ" : cand.region(), k -> new CountryBucket(cand.phone())).add();
            if (preview.size() < 5) {
                preview.add(new PreviewLine(cand.phone(), spec.render(vars)));
            }
        }
        for (int i = 0; i < newContacts.size(); i += 500) {
            contactRepository.saveAll(newContacts.subList(i, Math.min(newContacts.size(), i + 500)));
        }
        contactRepository.flush();
        for (int i = 0; i < pending.size(); i++) {
            Candidate cand = pending.get(i);
            Contact contact = contactsByPhone.get(cand.phone());
            inserts.add(row(c.getId(), contact == null ? null : contact.getId(), cand.rowNo(), cand.phone(), pendingVars.get(i),
                    RecipientStatus.PENDING, null, null, now));
        }

        // 4. Persist recipients (replace previous preparation)
        jdbc.update("delete from campaign_recipients where campaign_id = ?", c.getId());
        inserts.sort(java.util.Comparator.comparingInt(r -> (Integer) r[2]));
        for (int i = 0; i < inserts.size(); i += 1000) {
            jdbc.batchUpdate("""
                    insert into campaign_recipients (campaign_id, contact_id, row_no, phone, variables, status, skip_reason, error_message, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, inserts.subList(i, Math.min(inserts.size(), i + 1000)));
        }

        // 5. Estimate + counters
        CostEstimateDto estimate = pricing.estimate(partnerId, sender, template.getCategory(), buckets);
        c.setTotalRows(rows.size());
        c.setValidCount(pending.size());
        c.setInvalidCount(invalid + missing);
        c.setSkippedCount(duplicates + optOutCount + blockedCount);
        c.setEstimatedCost(estimate.total());
        c.setUnitPrice(estimate.countryBreakdown().stream().max(java.util.Comparator.comparingLong(l -> l.count()))
                .map(l -> l.unitPrice()).orElse(MoneyUtil.ZERO));
        c.setPreparedAt(now);
        campaignRepository.save(c);
        return new PrepareResult(new PrepareCounts(rows.size(), pending.size(), invalid, optOutCount, blockedCount, duplicates, missing),
                preview, estimate);
    }

    // ------------------------------------------------------------------ CP5
    @Transactional
    public CampaignDto launch(Long partnerId, Long id, Instant scheduleAt, Boolean confirmCost) {
        Campaign c = lockOwned(partnerId, id);
        requireStatus(c, CampaignStatus.DRAFT);
        if (c.getPreparedAt() == null) {
            throw new AppException(ErrorCode.CAMPAIGN_INVALID_STATE, "Please check the audience and cost before launching");
        }
        if (!Boolean.TRUE.equals(confirmCost)) {
            throw AppException.field("confirmCost", "Please confirm the estimated cost");
        }
        if (c.getValidCount() <= 0) {
            throw new AppException(ErrorCode.CAMPAIGN_INVALID_STATE, "There are no valid recipients to send to");
        }
        PhoneNumber sender = senderAccess.requireUsableSender(partnerId, c.getPhoneNumberId());
        Template template = templateFor(partnerId, sender, c.getTemplateId());
        if (template.getStatus() != TemplateStatus.APPROVED) {
            throw new AppException(ErrorCode.TEMPLATE_NOT_APPROVED, "The template must be approved before launching");
        }
        CostEstimateDto estimate = estimatePending(partnerId, c, sender, template);
        requireBalance(partnerId, estimate.total());
        c.setEstimatedCost(estimate.total());
        if (scheduleAt != null && scheduleAt.isAfter(Instant.now().plusSeconds(30))) {
            c.setStatus(CampaignStatus.SCHEDULED);
            c.setScheduledAt(scheduleAt);
        } else {
            c.setStatus(CampaignStatus.RUNNING);
            c.setScheduledAt(null);
            c.setStartedAt(Instant.now());
        }
        c.setPauseReason(null);
        campaignRepository.save(c);
        auditService.log("CAMPAIGN_LAUNCH", "CAMPAIGN", c.getUid(), null,
                Map.of("status", c.getStatus().name(), "estimatedCost", MoneyUtil.str(c.getEstimatedCost())), partnerId);
        return toDto(c, false);
    }

    /** Estimate for the recipients that are still PENDING (used at launch and resume). */
    CostEstimateDto estimatePending(Long partnerId, Campaign c, PhoneNumber sender, Template template) {
        Map<String, CountryBucket> buckets = new HashMap<>();
        jdbc.query("select phone from campaign_recipients where campaign_id = ? and status = 'PENDING'", rs -> {
            String phone = rs.getString(1);
            String region = PhoneUtil.regionOfDigits(phone);
            buckets.computeIfAbsent(region, k -> new CountryBucket(phone)).add();
        }, c.getId());
        return pricing.estimate(partnerId, sender, template.getCategory(), buckets);
    }

    void requireBalance(Long partnerId, BigDecimal required) {
        BigDecimal available = walletRepository.findByPartnerId(partnerId).map(Wallet::getBalance).orElse(MoneyUtil.ZERO);
        if (available.compareTo(required) < 0) {
            throw new AppException(ErrorCode.WALLET_INSUFFICIENT_BALANCE, "Your wallet balance is not enough for this campaign. Please add money",
                    Map.of("required", MoneyUtil.str(required), "available", MoneyUtil.str(available)));
        }
    }

    // ------------------------------------------------------------------ helpers
    Campaign lockOwned(Long partnerId, Long id) {
        Campaign c = campaignRepository.lockById(id).orElseThrow(() -> AppException.notFound("Campaign"));
        if (partnerId != null && !c.getPartnerId().equals(partnerId)) {
            throw AppException.notFound("Campaign");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public Campaign owned(Long partnerId, Long id) {
        return campaignRepository.findByIdAndPartnerId(id, partnerId).orElseThrow(() -> AppException.notFound("Campaign"));
    }

    static void requireStatus(Campaign c, CampaignStatus... allowed) {
        for (CampaignStatus s : allowed) {
            if (c.getStatus() == s) {
                return;
            }
        }
        throw new AppException(ErrorCode.CAMPAIGN_INVALID_STATE,
                "This action is not allowed while the campaign is " + c.getStatus().name().toLowerCase());
    }

    static HeaderMedia headerMedia(Campaign c) {
        return c.getHeaderMedia() == null ? null : JsonUtil.fromJson(c.getHeaderMedia(), HeaderMedia.class);
    }

    private List<Contact> loadContacts(Long partnerId, List<Long> ids) {
        List<Contact> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 1000) {
            out.addAll(contactRepository.findByPartnerIdAndIdIn(partnerId, ids.subList(i, Math.min(ids.size(), i + 1000))));
        }
        return out;
    }

    private static Map<String, String> contactRow(Contact ct) {
        Map<String, String> row = new LinkedHashMap<>();
        if (ct.getAttributes() != null) {
            row.putAll(ct.getAttributes());
        }
        row.put("phone", ct.getPhone());
        row.put("name", ct.getName() == null ? "" : ct.getName());
        row.put("email", ct.getEmail() == null ? "" : ct.getEmail());
        return row;
    }

    /** Resolves "=static", "{{field}}" (contact field/attribute) or a plain column / contact field. */
    static Map<String, String> resolve(Map<String, String> variables, Map<String, String> row, Contact contact) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> contactFields = contact == null ? Map.of() : contactRow(contact);
        for (Map.Entry<String, String> e : variables.entrySet()) {
            String src = e.getValue() == null ? "" : e.getValue();
            String value;
            if (src.startsWith("=")) {
                value = src.substring(1);
            } else if (src.startsWith("{{") && src.endsWith("}}")) {
                String key = src.substring(2, src.length() - 2).trim();
                value = contactFields.getOrDefault(key, row.get(key));
            } else {
                value = row.containsKey(src) ? row.get(src) : contactFields.get(src);
            }
            out.put(e.getKey(), value == null ? "" : value.trim());
        }
        return out;
    }

    private static Object[] row(Long campaignId, Long contactId, int rowNo, String phone, Map<String, String> vars,
                                RecipientStatus status, String skipReason, String error, Instant now) {
        return new Object[]{campaignId, contactId, rowNo, phone, vars == null ? null : JsonUtil.toJson(vars), status.name(),
                skipReason, error == null ? null : truncate(error, 255), Timestamp.from(now)};
    }

    /** Skipped rows can repeat a value; make the stored phone unique per campaign: "<value>#<row>" (max 20 chars). */
    private static String placeholderPhone(String raw, int rowNo) {
        String suffix = "#" + rowNo;
        String base = raw == null ? "" : raw.replaceAll("[^0-9+]", "");
        int max = 20 - suffix.length();
        if (base.length() > max) {
            base = base.substring(0, Math.max(0, max));
        }
        return base + suffix;
    }

    private void resetCounts(Campaign c) {
        c.setTotalRows(0);
        c.setValidCount(0);
        c.setInvalidCount(0);
        c.setSkippedCount(0);
        c.setEstimatedCost(MoneyUtil.ZERO);
        c.setUnitPrice(MoneyUtil.ZERO);
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ------------------------------------------------------------------ DTO
    @Transactional(readOnly = true)
    public CampaignDto toDto(Campaign c, boolean withPartner) {
        PhoneNumber pn = phoneNumberRepository.findById(c.getPhoneNumberId()).orElse(null);
        Template t = templateRepository.findById(c.getTemplateId()).orElse(null);
        Partner partner = withPartner ? partnerRepository.findById(c.getPartnerId()).orElse(null) : null;
        Counts counts = new Counts(c.getTotalRows(), c.getValidCount(), c.getInvalidCount(), c.getSkippedCount(), c.getQueuedCount(),
                c.getSentCount(), c.getDeliveredCount(), c.getReadCount(), c.getFailedCount());
        return new CampaignDto(c.getId(), c.getUid(), c.getName(),
                pn == null ? null : new SenderRef(pn.getId(), pn.getDisplayPhoneNumber(), pn.getVerifiedName()),
                t == null ? null : new TemplateRef(t.getId(), t.getName(), t.getLanguage(), t.getCategory().name(), t.getStatus().name()),
                c.getAudienceType().name(), json(c.getAudience()), json(c.getColumnMapping()), json(c.getHeaderMedia()),
                c.getDefaultCountry(), c.isSaveContacts(), c.getStatus().name(), c.getPauseReason(), c.getScheduledAt(),
                c.getStartedAt(), c.getCompletedAt(), c.getPreparedAt(), counts, c.getUnitPrice(), c.getEstimatedCost(),
                c.getChargedAmount(), c.getRefundedAmount(),
                partner == null ? null : new PartnerRef(partner.getId(), partner.getBusinessName()), c.getCreatedAt());
    }

    private static JsonNode json(String s) {
        return s == null ? null : JsonUtil.readTree(s);
    }

    /**
     * Accepts both key styles for header/button variables: "header" or "header.<var>" and
     * "button_<index>" or "button.<index>" (the dashboard sends the dotted form).
     */
    static Map<String, String> normaliseVariableKeys(Map<String, String> vars) {
        Map<String, String> out = new LinkedHashMap<>();
        if (vars == null) {
            return out;
        }
        vars.forEach((k, v) -> {
            String key = k;
            if (k.startsWith("header.")) {
                key = "header";
            } else if (k.startsWith("button.")) {
                key = "button_" + k.substring("button.".length());
            }
            out.put(key, v);
        });
        return out;
    }
}
