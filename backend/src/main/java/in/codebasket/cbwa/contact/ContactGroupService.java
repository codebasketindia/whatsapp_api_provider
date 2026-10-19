package in.codebasket.cbwa.contact;

import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.contact.dto.ContactDtos.GroupDto;
import in.codebasket.cbwa.contact.dto.ContactDtos.GroupRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** G1-G4: contact groups; member_count is kept in sync on every membership change. */
@Service
@RequiredArgsConstructor
public class ContactGroupService {

    private final ContactGroupRepository groupRepository;
    private final ContactGroupMemberRepository memberRepository;
    private final ContactRepository contactRepository;
    private final ContactService contactService;

    @Transactional(readOnly = true)
    public PageResponse<GroupDto> list(Long partnerId, String search, Integer page, Integer size) {
        Specification<ContactGroup> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("partnerId"), partnerId));
            if (search != null && !search.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("name")), "%" + search.trim().toLowerCase() + "%"));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResponse.of(groupRepository.findAll(spec, Paging.of(page, size, null, Set.of(), Sort.by("name"))), ContactGroupService::toDto);
    }

    @Transactional(readOnly = true)
    public GroupDto get(Long partnerId, Long id) {
        return toDto(find(partnerId, id));
    }

    @Transactional
    public GroupDto create(Long partnerId, GroupRequest req) {
        if (groupRepository.existsByPartnerIdAndNameIgnoreCase(partnerId, req.name().trim())) {
            throw AppException.field("name", "A group with this name already exists");
        }
        ContactGroup g = new ContactGroup();
        g.setPartnerId(partnerId);
        g.setName(req.name().trim());
        g.setDescription(req.description());
        g.setMemberCount(0);
        return toDto(groupRepository.save(g));
    }

    @Transactional
    public GroupDto update(Long partnerId, Long id, GroupRequest req) {
        ContactGroup g = find(partnerId, id);
        if (!g.getName().equalsIgnoreCase(req.name().trim()) && groupRepository.existsByPartnerIdAndNameIgnoreCase(partnerId, req.name().trim())) {
            throw AppException.field("name", "A group with this name already exists");
        }
        g.setName(req.name().trim());
        g.setDescription(req.description());
        return toDto(groupRepository.save(g));
    }

    @Transactional
    public void delete(Long partnerId, Long id) {
        groupRepository.delete(find(partnerId, id));
    }

    @Transactional
    public int addMembers(Long partnerId, Long groupId, Collection<Long> contactIds) {
        ContactGroup g = find(partnerId, groupId);
        Set<Long> existing = new HashSet<>();
        memberRepository.findByGroupId(groupId).forEach(m -> existing.add(m.getContactId()));
        int added = 0;
        List<ContactGroupMember> batch = new ArrayList<>();
        for (Contact c : contactRepository.findByPartnerIdAndIdIn(partnerId, contactIds)) {
            if (existing.add(c.getId())) {
                ContactGroupMember m = new ContactGroupMember();
                m.setGroupId(groupId);
                m.setContactId(c.getId());
                m.setAddedAt(Instant.now());
                batch.add(m);
                added++;
            }
        }
        memberRepository.saveAll(batch);
        memberRepository.flush();
        contactService.refreshMemberCount(g.getId());
        return added;
    }

    @Transactional
    public int removeMembers(Long partnerId, Long groupId, Collection<Long> contactIds) {
        ContactGroup g = find(partnerId, groupId);
        Set<Long> ids = new HashSet<>(contactIds);
        List<ContactGroupMember> remove = memberRepository.findByGroupId(groupId).stream()
                .filter(m -> ids.contains(m.getContactId())).toList();
        memberRepository.deleteAll(remove);
        memberRepository.flush();
        contactService.refreshMemberCount(g.getId());
        return remove.size();
    }

    public ContactGroup find(Long partnerId, Long id) {
        return groupRepository.findByIdAndPartnerId(id, partnerId).orElseThrow(() -> AppException.notFound("Group"));
    }

    static GroupDto toDto(ContactGroup g) {
        return new GroupDto(g.getId(), g.getName(), g.getDescription(), g.getMemberCount(), g.getCreatedAt());
    }
}
