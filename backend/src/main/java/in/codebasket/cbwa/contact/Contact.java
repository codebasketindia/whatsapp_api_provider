package in.codebasket.cbwa.contact;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "contacts")
public class Contact extends AuditableEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String phone;

    private String name;

    private String email;

    private String countryCode;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> attributes;

    @Column(nullable = false)
    private boolean optedOut;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    private Instant lastInboundAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactSource source;
}
