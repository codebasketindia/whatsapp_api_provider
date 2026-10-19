package in.codebasket.cbwa.contact;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.contact.dto.ContactDtos.ContactDto;
import in.codebasket.cbwa.contact.dto.ContactDtos.ContactRequest;
import in.codebasket.cbwa.contact.dto.ContactDtos.GroupRef;
import in.codebasket.cbwa.contact.dto.ContactDtos.OptOutDto;
import in.codebasket.cbwa.contact.dto.ContactDtos.TagCount;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** C1-C4, C8-C11: partner contacts, tags, opt-outs. Every query is scoped to the caller's partner. */
@Service
@RequiredArgsConstructor
public class ContactService {

    public static final Pattern ATTRIBUTE_KEY = Pattern.compile("^[a-z0-9_]{1,40}$");

    private final ContactRepository contactRepository;
    private final ContactGroupRepository groupRepository;
    private final ContactGroupMemberRepository memberRepository;
    private final OptOutRepository optOutRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ContactDto> list(Long partnerId, String search, String tag, Long groupId, Boolean optedOut,
                                         Boolean blocked, Integer page, Integer size) {
        Pageable pageable = Paging.of(page, size);
        StringBuilder where = new StringBuilder(" from contacts c where c.partner_id = :pid");
        MapSqlParameterSource params = new MapSqlParameterSource("pid", partnerId);
        if (search != null && !search.isBlank()) {
            where.append(" and (lower(c.name) like :q or c.phone like :q or lower(c.email) like :q)");
            params.addValue("q", "%" + search.trim().toLowerCase().replaceAll("[%_]", "") + "%");
        }
        if (tag != null && !tag.isBlank()) {
            where.append(" and json_contains(c.tags, json_quote(:tag))");
            params.addValue("tag", tag.trim());
        }
        if (groupId != null) {
            where.append(" and exists (select 1 from contact_group_members m where m.contact_id = c.id and m.group_id = :gid)");
            params.addValue("gid", groupId);
        }
        if (optedOut != null) {
            where.append(" and c.opted_out = :oo");
            params.addValue("oo", optedOut);
        }
        if (blocked != null) {
            where.append(" and c.is_blocked = :bl");
            params.addValue("bl", blocked);
        }
        Long total = jdbc.queryForObject("select count(*)" + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<Long> ids = jdbc.queryForList("select c.id" + where + " order by c.id desc limit :limit offset :offset", params, Long.class);
        Map<Long, Contact> byId = contactRepository.findAllById(ids).stream().collect(Collectors.toMap(Contact::getId, Function.identity()));
        List<Contact> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        List<ContactDto> content = toDtos(ordered);
        long t = total == null ? 0 : total;
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), t,
                (int) Math.ceil(t / (double) pageable.getPageSize()));
    }

    @Transactional(readOnly = true)
    public ContactDto get(Long partnerId, Long id) {
        return toDtos(List.of(find(partnerId, id))).get(0);
    }

    @Transactional
    public ContactDto create(Long partnerId, ContactRequest req) {
        PhoneUtil.Parsed parsed = normalise(req.phone());
        if (contactRepository.findByPartnerIdAndPhone(partnerId, parsed.digits()).isPresent()) {
            throw new AppException(ErrorCode.CONFLICT, "A contact with this phone number already exists");
        }
        Contact c = new Contact();
        c.setPartnerId(partnerId);
        c.setPhone(parsed.digits());
        c.setCountryCode(parsed.regionCode());
        c.setSource(ContactSource.MANUAL);
        c.setOptedOut(optOutRepository.existsByPartnerIdAndPhone(partnerId, parsed.digits()));
        apply(c, req);
        contactRepository.save(c);
        setGroups(partnerId, c.getId(), req.groupIds());
        return get(partnerId, c.getId());
    }

    @Transactional
    public ContactDto update(Long partnerId, Long id, ContactRequest req) {
        Contact c = find(partnerId, id);
        PhoneUtil.Parsed parsed = normalise(req.phone());
        if (!parsed.digits().equals(c.getPhone())) {
            if (contactRepository.findByPartnerIdAndPhone(partnerId, parsed.digits()).isPresent()) {
                throw new AppException(ErrorCode.CONFLICT, "A contact with this phone number already exists");
            }
            c.setPhone(parsed.digits());
            c.setCountryCode(parsed.regionCode());
            c.setOptedOut(optOutRepository.existsByPartnerIdAndPhone(partnerId, parsed.digits()));
        }
        apply(c, req);
        contactRepository.save(c);
        if (req.groupIds() != null) {
            setGroups(partnerId, c.getId(), req.groupIds());
        }
        return get(partnerId, id);
    }

    @Transactional
    public void delete(Long partnerId, Long id) {
        bulkDelete(partnerId, List.of(find(partnerId, id).getId()));
    }

    @Transactional
    public int bulkDelete(Long partnerId, Collection<Long> ids) {
        List<Contact> contacts = contactRepository.findByPartnerIdAndIdIn(partnerId, ids);
        if (contacts.isEmpty()) {
            return 0;
        }
        Set<Long> groupIds = new HashSet<>();
        for (Contact c : contacts) {
            memberRepository.findByContactId(c.getId()).forEach(m -> groupIds.add(m.getGroupId()));
        }
        contactRepository.deleteAll(contacts);
        contactRepository.flush();
        groupIds.forEach(this::refreshMemberCount);
        return contacts.size();
    }

    @Transactional(readOnly = true)
    public List<TagCount> tags(Long partnerId) {
        return jdbc.query("""
                        select jt.tag as tag, count(*) as cnt from contacts c,
                          json_table(c.tags, '$[*]' columns (tag varchar(100) path '$')) jt
                        where c.partner_id = :pid and jt.tag is not null and jt.tag <> ''
                        group by jt.tag order by cnt desc, jt.tag limit 500
                        """, new MapSqlParameterSource("pid", partnerId),
                (rs, i) -> new TagCount(rs.getString("tag"), rs.getLong("cnt")));
    }

    @Transactional
    public ContactDto optOut(Long partnerId, Long id, String reason) {
        Contact c = find(partnerId, id);
        if (optOutRepository.findByPartnerIdAndPhone(partnerId, c.getPhone()).isEmpty()) {
            OptOut o = new OptOut();
            o.setPartnerId(partnerId);
            o.setPhone(c.getPhone());
            o.setSource(OptOutSource.MANUAL);
            o.setCreatedAt(Instant.now());
            o.setCreatedBy(in.codebasket.cbwa.auth.SecurityUtils.currentUserIdOrNull());
            optOutRepository.save(o);
        }
        c.setOptedOut(true);
        contactRepository.save(c);
        auditService.log("CONTACT_OPT_OUT", "CONTACT", c.getId(), null, Map.of("phone", c.getPhone(),
                "reason", reason == null ? "" : reason), partnerId);
        return get(partnerId, id);
    }

    @Transactional
    public ContactDto optIn(Long partnerId, Long id, Boolean confirmConsent) {
        if (!Boolean.TRUE.equals(confirmConsent)) {
            throw AppException.field("confirmConsent", "Please confirm that this contact agreed to receive messages again");
        }
        Contact c = find(partnerId, id);
        optOutRepository.findByPartnerIdAndPhone(partnerId, c.getPhone()).ifPresent(optOutRepository::delete);
        c.setOptedOut(false);
        contactRepository.save(c);
        auditService.log("CONTACT_OPT_IN", "CONTACT", c.getId(), Map.of("optedOut", true), Map.of("optedOut", false), partnerId);
        return get(partnerId, id);
    }

    @Transactional(readOnly = true)
    public PageResponse<OptOutDto> optOuts(Long partnerId, String search, String source, Integer page, Integer size) {
        Specification<OptOut> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("partnerId"), partnerId));
            if (search != null && !search.isBlank()) {
                ps.add(cb.like(root.get("phone"), "%" + search.replaceAll("[^0-9]", "") + "%"));
            }
            if (source != null && !source.isBlank()) {
                ps.add(cb.equal(root.get("source"), OptOutSource.valueOf(source)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<OptOut> p = optOutRepository.findAll(spec, Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(p, o -> new OptOutDto(o.getId(), o.getPhone(), o.getSource().name(), o.getKeyword(), o.getCreatedAt()));
    }

    public Contact find(Long partnerId, Long id) {
        return contactRepository.findByIdAndPartnerId(id, partnerId).orElseThrow(() -> AppException.notFound("Contact"));
    }

    public static PhoneUtil.Parsed normalise(String raw) {
        return PhoneUtil.parse(raw, "IN").orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_INVALID,
                "The phone number is not valid", null, List.of(new in.codebasket.cbwa.common.api.FieldErrorDto("phone", "Enter a valid phone number with country code"))));
    }

    /** Cleans a tag list: trimmed, non-empty, max 50 chars, unique, keeps order. */
    public static List<String> cleanTags(Collection<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : tags) {
            if (t != null && !t.isBlank()) {
                String v = t.trim();
                out.add(v.length() > 50 ? v.substring(0, 50) : v);
            }
        }
        return new ArrayList<>(out);
    }

    public static Map<String, String> cleanAttributes(Map<String, String> attrs) {
        Map<String, String> out = new LinkedHashMap<>();
        if (attrs == null) {
            return out;
        }
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim().toLowerCase();
            if (!ATTRIBUTE_KEY.matcher(key).matches()) {
                throw AppException.field("attributes", "Attribute names may use only a-z, 0-9 and _ (max 40): " + e.getKey());
            }
            if (e.getValue() != null) {
                String v = e.getValue().trim();
                out.put(key, v.length() > 500 ? v.substring(0, 500) : v);
            }
        }
        return out;
    }

    public List<ContactDto> toDtos(List<Contact> contacts) {
        if (contacts.isEmpty()) {
            return List.of();
        }
        Map<Long, List<GroupRef>> groupsByContact = new HashMap<>();
        List<Long> ids = contacts.stream().map(Contact::getId).toList();
        jdbc.query("""
                        select m.contact_id, g.id, g.name from contact_group_members m join contact_groups g on g.id = m.group_id
                        where m.contact_id in (:ids) order by g.name
                        """, new MapSqlParameterSource("ids", ids),
                rs -> {
                    groupsByContact.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new GroupRef(rs.getLong(2), rs.getString(3)));
                });
        return contacts.stream().map(c -> new ContactDto(c.getId(), c.getPhone(), c.getName(), c.getEmail(), c.getCountryCode(),
                c.getTags() == null ? List.of() : c.getTags(), c.getAttributes() == null ? Map.of() : c.getAttributes(),
                c.isOptedOut(), c.isBlocked(), c.getLastInboundAt(), c.getSource().name(),
                groupsByContact.getOrDefault(c.getId(), List.of()), c.getCreatedAt())).toList();
    }

    void refreshMemberCount(Long groupId) {
        groupRepository.findById(groupId).ifPresent(g -> {
            g.setMemberCount((int) memberRepository.countByGroupId(groupId));
            groupRepository.save(g);
        });
    }

    private void apply(Contact c, ContactRequest req) {
        c.setName(blankToNull(req.name()));
        c.setEmail(blankToNull(req.email()));
        c.setTags(cleanTags(req.tags()));
        c.setAttributes(cleanAttributes(req.attributes()));
    }

    /** Replaces the contact's group memberships with the given groups (all must belong to the partner). */
    private void setGroups(Long partnerId, Long contactId, List<Long> groupIds) {
        Set<Long> wanted = new HashSet<>(groupIds == null ? List.of() : groupIds);
        for (Long gid : wanted) {
            groupRepository.findByIdAndPartnerId(gid, partnerId).orElseThrow(() -> AppException.field("groupIds", "Group not found"));
        }
        Set<Long> touched = new HashSet<>();
        for (ContactGroupMember m : memberRepository.findByContactId(contactId)) {
            if (!wanted.contains(m.getGroupId())) {
                memberRepository.delete(m);
                touched.add(m.getGroupId());
            } else {
                wanted.remove(m.getGroupId());
            }
        }
        for (Long gid : wanted) {
            ContactGroupMember m = new ContactGroupMember();
            m.setGroupId(gid);
            m.setContactId(contactId);
            m.setAddedAt(Instant.now());
            memberRepository.save(m);
            touched.add(gid);
        }
        memberRepository.flush();
        touched.forEach(this::refreshMemberCount);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
