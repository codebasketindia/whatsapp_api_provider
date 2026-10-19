package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.campaign.dto.CampaignDtos.CampaignDto;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.CostEstimateDto;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * CP6 / CP13 state machine:
 *   pause:  RUNNING | SCHEDULED -> PAUSED
 *   resume: PAUSED -> RUNNING (or SCHEDULED if it never started and its time is still ahead);
 *           re-checks sender, template and wallet balance for the remaining recipients
 *   cancel: DRAFT | SCHEDULED | RUNNING | PAUSED -> CANCELLED; PENDING recipients become SKIPPED (CANCELLED),
 *           messages already queued are left to finish.
 */
@Service
@RequiredArgsConstructor
public class CampaignControlService {

    private final CampaignService campaignService;
    private final CampaignRepository campaignRepository;
    private final CampaignProgressJob progressJob;
    private final SenderAccessService senderAccess;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    /** partnerId null = admin action (any partner). */
    @Transactional
    public CampaignDto pause(Long partnerId, Long id, String reason) {
        Campaign c = campaignService.lockOwned(partnerId, id);
        CampaignService.requireStatus(c, CampaignStatus.RUNNING, CampaignStatus.SCHEDULED);
        c.setStatus(CampaignStatus.PAUSED);
        c.setPauseReason(partnerId == null ? "ADMIN" : "USER");
        campaignRepository.save(c);
        if (partnerId == null) {
            auditService.log("CAMPAIGN_PAUSE", "CAMPAIGN", c.getUid(), null, Map.of("reason", reason == null ? "" : reason), c.getPartnerId());
            notifications.notifyPartner(c.getPartnerId(), "CAMPAIGN_PAUSED", NotificationService.WARNING,
                    "Campaign paused by admin: " + c.getName(), reason == null ? "Please contact support." : reason,
                    "/partner/campaigns/" + c.getId(), null);
        }
        return campaignService.toDto(c, partnerId == null);
    }

    @Transactional
    public CampaignDto resume(Long partnerId, Long id) {
        Campaign c = campaignService.lockOwned(partnerId, id);
        CampaignService.requireStatus(c, CampaignStatus.PAUSED);
        if ("ADMIN".equals(c.getPauseReason())) {
            throw new AppException(ErrorCode.CAMPAIGN_INVALID_STATE, "This campaign was paused by the admin. Please contact support");
        }
        PhoneNumber sender = senderAccess.requireUsableSender(c.getPartnerId(), c.getPhoneNumberId());
        Template template = campaignService.templateFor(c.getPartnerId(), sender, c.getTemplateId());
        if (template.getStatus() != TemplateStatus.APPROVED) {
            throw new AppException(ErrorCode.TEMPLATE_NOT_APPROVED, "The template is not approved");
        }
        CostEstimateDto remaining = campaignService.estimatePending(c.getPartnerId(), c, sender, template);
        campaignService.requireBalance(c.getPartnerId(), remaining.total());
        boolean scheduleAhead = c.getStartedAt() == null && c.getScheduledAt() != null && c.getScheduledAt().isAfter(Instant.now());
        if (scheduleAhead) {
            c.setStatus(CampaignStatus.SCHEDULED);
        } else {
            c.setStatus(CampaignStatus.RUNNING);
            if (c.getStartedAt() == null) {
                c.setStartedAt(Instant.now());
            }
        }
        c.setPauseReason(null);
        campaignRepository.save(c);
        return campaignService.toDto(c, false);
    }

    @Transactional
    public CampaignDto cancel(Long partnerId, Long id, String reason) {
        Campaign c = campaignService.lockOwned(partnerId, id);
        CampaignService.requireStatus(c, CampaignStatus.DRAFT, CampaignStatus.SCHEDULED, CampaignStatus.RUNNING, CampaignStatus.PAUSED);
        jdbc.update("""
                update campaign_recipients set status = 'SKIPPED', skip_reason = 'CANCELLED', updated_at = utc_timestamp(6)
                where campaign_id = ? and status = 'PENDING'
                """, c.getId());
        c.setStatus(CampaignStatus.CANCELLED);
        c.setCompletedAt(Instant.now());
        campaignRepository.save(c);
        if (partnerId == null) {
            auditService.log("CAMPAIGN_CANCEL", "CAMPAIGN", c.getUid(), null, Map.of("reason", reason == null ? "" : reason), c.getPartnerId());
            notifications.notifyPartner(c.getPartnerId(), "CAMPAIGN_CANCELLED", NotificationService.WARNING,
                    "Campaign cancelled by admin: " + c.getName(), reason == null ? "Please contact support." : reason,
                    "/partner/campaigns/" + c.getId(), null);
        }
        progressJob.refresh(c.getId());
        return campaignService.toDto(campaignRepository.findById(c.getId()).orElseThrow(), partnerId == null);
    }
}
