package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "template_analytics")
public class TemplateAnalytics extends BaseEntity {

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int sent;

    @Column(nullable = false)
    private int delivered;

    @Column(nullable = false)
    private int readCount;

    @Column(columnDefinition = "LONGTEXT")
    private String clicked;

    @Column(nullable = false)
    private BigDecimal cost;
}
