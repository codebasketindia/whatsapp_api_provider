package in.codebasket.cbwa.registration;

import in.codebasket.cbwa.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "registrations")
public class Registration extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long formId;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String passNo;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    private Long photoFileId;
    private Long passFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PassStatus passStatus;

    private Long passMessageId;
    private String passError;

    @Column(nullable = false)
    private String claimCode;

    private Long contactId;

    @Column(nullable = false)
    private Instant consentAt;

    private String ip;
    private String userAgent;
}
