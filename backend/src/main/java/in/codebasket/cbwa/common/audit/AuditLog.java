package in.codebasket.cbwa.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorUserId;

    @Column(nullable = false)
    private String actorType;

    private Long impersonatorUserId;
    private Long partnerId;

    @Column(nullable = false)
    private String action;

    private String entityType;
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    private String newValue;

    private String ip;
    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt;
}
