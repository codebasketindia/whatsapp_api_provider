package in.codebasket.cbwa.ai;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_knowledge_items")
public class AiKnowledgeItem extends AuditableEntity {

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Long fileId;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int sortOrder;
}
