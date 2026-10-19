package in.codebasket.cbwa.convtools;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "conversational_components")
public class ConversationalComponent extends AuditableEntity {

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private boolean enableWelcomeMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> prompts;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, String>> commands;

    private String syncStatus;

    private Instant syncedAt;
}
