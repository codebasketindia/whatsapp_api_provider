package in.codebasket.cbwa.common.repository;

import in.codebasket.cbwa.common.entity.ContactGroupMember;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactGroupMemberRepository extends JpaRepository<ContactGroupMember, ContactGroupMember.Key> {
    List<ContactGroupMember> findByGroupId(Long groupId);

    List<ContactGroupMember> findByContactId(Long contactId);

    long countByGroupId(Long groupId);

    @Query("select m.contactId from ContactGroupMember m where m.groupId in :groupIds")
    List<Long> findContactIdsByGroupIds(@Param("groupIds") Collection<Long> groupIds);
}
