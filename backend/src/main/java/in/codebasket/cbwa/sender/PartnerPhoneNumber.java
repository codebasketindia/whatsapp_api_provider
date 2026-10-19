package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "partner_phone_numbers")
public class PartnerPhoneNumber extends AuditableEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentType assignmentType;

    @Column(name = "is_default", nullable = false)
    private boolean defaultSender;

    @Column(nullable = false)
    private boolean active;

    @Column(insertable = false, updatable = false)
    private Long activeKey;

    private Long assignedBy;

    @Column(nullable = false)
    private Instant assignedAt;

    private Instant unassignedAt;
}
