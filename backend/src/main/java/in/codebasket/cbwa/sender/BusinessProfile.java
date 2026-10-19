package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "business_profiles")
public class BusinessProfile extends AuditableEntity {

    @Column(nullable = false)
    private Long phoneNumberId;

    private String about;

    private String description;

    private String address;

    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> websites;

    private String vertical;

    private String profilePictureUrl;

    private Long profilePictureFileId;

    private Instant syncedAt;
}
