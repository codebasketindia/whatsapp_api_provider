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
@Table(name = "partner_webhooks")
public class PartnerWebhook extends AuditableEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String secretEnc;

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private List<String> events;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int consecutiveFailures;

    private Instant lastSuccessAt;
}
