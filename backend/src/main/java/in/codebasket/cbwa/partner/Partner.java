package in.codebasket.cbwa.partner;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "partners")
public class Partner extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false, updatable = false)
    private String partnerCode;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String mobile;

    private String gstin;

    private String pan;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String stateCode;

    private String pincode;

    @Column(nullable = false)
    private String country = "IN";

    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartnerStatus status;

    private String suspendReason;

    @Column(nullable = false)
    private boolean kycVerified;

    @Column(nullable = false)
    private Long ratePlanId;

    @Column(nullable = false)
    private boolean allowOwnNumber;
}
