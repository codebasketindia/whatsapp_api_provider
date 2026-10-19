package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "api_keys")
public class ApiKey extends AuditableEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String secretHash;

    @Column(nullable = false)
    private String secretLast4;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> ipWhitelist;

    private Integer rateLimitPerMinute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyStatus status;

    private Instant lastUsedAt;

    private String lastUsedIp;
}
