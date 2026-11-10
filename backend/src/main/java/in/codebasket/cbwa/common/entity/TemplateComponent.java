package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.constants.ComponentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "template_components")
public class TemplateComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentType type;

    private String format;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "LONGTEXT")
    private String example;

    @Column(columnDefinition = "LONGTEXT")
    private String buttons;

    private Long headerMediaFileId;

    private String headerHandle;

    private Boolean addSecurityRecommendation;

    private Integer codeExpirationMinutes;
}
