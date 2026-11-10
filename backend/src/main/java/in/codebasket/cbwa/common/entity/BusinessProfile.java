package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.config.StringListConverter;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

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

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<String> websites;

    private String vertical;

    private String profilePictureUrl;

    private Long profilePictureFileId;

    private Instant syncedAt;
}
