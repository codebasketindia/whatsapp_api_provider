package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.constants.ContactSource;
import in.codebasket.cbwa.config.StringListConverter;
import in.codebasket.cbwa.config.StringMapConverter;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

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

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<String> tags;

    @Convert(converter = StringMapConverter.class)
    @Column(columnDefinition = "LONGTEXT")
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
