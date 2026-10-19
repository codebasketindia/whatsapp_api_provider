package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inbound_media")
public class InboundMedia extends BaseEntity {

    @Column(nullable = false)
    private Long messageId;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String mediaId;

    private String mimeType;

    private String sha256;

    private Long fileSize;

    private String caption;

    private String filename;

    private Long fileId;

    @Column(nullable = false)
    private String downloadStatus;

    @Column(nullable = false)
    private int attempts;
}
