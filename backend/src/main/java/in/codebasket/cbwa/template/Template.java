package in.codebasket.cbwa.template;

import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "templates")
public class Template extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    private Long partnerId;

    @Column(nullable = false)
    private Long whatsappAccountId;

    private String metaTemplateId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageCategory category;

    private String previousCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParameterFormat parameterFormat;

    private String libraryTemplateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateStatus status;

    private String rejectedReason;

    @Column(nullable = false)
    private String qualityScore = "UNKNOWN";

    private String adminReviewNote;

    private Long reviewedBy;

    private Instant reviewedAt;

    private Instant submittedAt;

    private Instant lastSyncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String metaPayload;
}
