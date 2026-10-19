package in.codebasket.cbwa.convtools;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "qr_codes")
public class QrCode extends AuditableEntity {

    private Long partnerId;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Column(nullable = false)
    private String metaCode;

    @Column(nullable = false)
    private String prefilledMessage;

    private String deepLinkUrl;

    private String qrImagePngUrl;

    private String qrImageSvgUrl;

    private String shortLink;
}
