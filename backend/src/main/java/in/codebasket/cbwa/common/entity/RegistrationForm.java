package in.codebasket.cbwa.common.entity;

import in.codebasket.cbwa.common.constants.FormStatus;
import in.codebasket.cbwa.common.dto.FollowUp;
import in.codebasket.cbwa.common.dto.PassLayout;
import in.codebasket.cbwa.config.FollowUpListConverter;
import in.codebasket.cbwa.config.PassLayoutConverter;
import in.codebasket.cbwa.config.StringListConverter;

import in.codebasket.cbwa.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "registration_forms")
public class RegistrationForm extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String uid;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private Long phoneNumberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormStatus status = FormStatus.ACTIVE;

    private Instant closesAt;
    private Integer maxRegistrations;

    @Column(nullable = false)
    private String consentText;

    private String successMessage;

    @Column(nullable = false)
    private boolean collectPhoto;

    @Column(nullable = false)
    private boolean passEnabled = true;

    /** Built-in design key (e.g. "harmain") when no custom design file is uploaded. */
    private String passDesign;

    private Long passDesignFileId;

    @Convert(converter = PassLayoutConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private PassLayout passLayout;

    @Column(nullable = false)
    private String passPrefix = "PASS";

    private Long passTemplateId;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<String> passTemplateBody = new ArrayList<>();

    private String passCaption;

    private Long contactGroupId;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<String> contactTags = new ArrayList<>();

    @Convert(converter = FollowUpListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<FollowUp> followUps = new ArrayList<>();

    @Column(nullable = false)
    private int registrationsCount;

    /** Registration fee in rupees, collected in the partner's own Razorpay account. Null = free. */
    private java.math.BigDecimal feeAmount;

    private String feeLabel;

    @Column(columnDefinition = "LONGTEXT")
    private String termsText;

    @Column(columnDefinition = "LONGTEXT")
    private String privacyText;

    // booking page look
    private String heroImageUrl;
    private String pageHeading;
    private String pageNote;
    private String ctaLabel;

    // combo offer: all packages in comboCodes together cost comboPrice (until comboValidUntil)
    private String comboTitle;
    private String comboSubtitle;
    private String comboCodes;
    private java.math.BigDecimal comboMrp;
    private java.math.BigDecimal comboPrice;
    private Instant comboValidUntil;

    /** Seller name on invoices; null = partner's business name. */
    private String invoiceName;

    /** Small line at the bottom of Ziyarat-card passes, e.g. an emergency number. */
    private String passSupportLine;

    // link preview shown by WhatsApp / Facebook / Telegram when the link is shared
    private String shareTitle;
    private String shareDescription;
    private String shareImageUrl;

    /** null = standard invoice; HFHU = the HFHU artwork invoice. */
    private String invoiceStyle;

    /** Contact line on the invoice header, e.g. "Office: +91 98xxxxxxx". */
    private String invoiceContact;
}
