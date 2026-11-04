package in.codebasket.cbwa.registration;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "registration_forms")
public class RegistrationForm extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormStatus status = FormStatus.ACTIVE;

    private Instant closesAt;
    private Integer maxRegistrations;

    @Column(nullable = false)
    private String consentText;

    private String successMessage;

    @Column(nullable = false)
    private boolean collectPhoto;

    @Column(nullable = false)
    private boolean passEnabled = true;

    /** Built-in design key (e.g. "harmain") when no custom design file is uploaded. */
    private String passDesign;

    private Long passDesignFileId;

    @JdbcTypeCode(SqlTypes.JSON)
    private PassLayout passLayout;

    @Column(nullable = false)
    private String passPrefix = "PASS";

    private Long passTemplateId;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> passTemplateBody = new ArrayList<>();

    private String passCaption;

    private Long contactGroupId;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> contactTags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FollowUp> followUps = new ArrayList<>();

    @Column(nullable = false)
    private int registrationsCount;

    /** Registration fee in rupees, collected in the partner's own Razorpay account. Null = free. */
    private java.math.BigDecimal feeAmount;

    private String feeLabel;
}
