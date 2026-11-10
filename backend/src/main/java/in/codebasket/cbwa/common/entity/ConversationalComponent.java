package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.config.MapListConverter;
import in.codebasket.cbwa.config.StringListConverter;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "conversational_components")
public class ConversationalComponent extends AuditableEntity {

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private boolean enableWelcomeMessage;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<String> prompts;

    @Convert(converter = MapListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<Map<String, String>> commands;

    private String syncStatus;

    private Instant syncedAt;
}
