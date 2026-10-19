package in.codebasket.cbwa.common.settings;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "settings")
public class Setting extends AuditableEntity {

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(name = "setting_key", nullable = false)
    private String settingKey;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column(nullable = false)
    private String valueType;

    @Column(name = "is_encrypted", nullable = false)
    private boolean encrypted;
}
