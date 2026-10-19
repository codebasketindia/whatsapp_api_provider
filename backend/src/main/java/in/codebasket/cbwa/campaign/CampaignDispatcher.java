package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.campaign.dto.CampaignDtos.HeaderMedia;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageSource;
import in.codebasket.cbwa.messaging.OutboundCommand;
import in.codebasket.cbwa.messaging.OutboundMessageService;
import in.codebasket.cbwa.template.Template;
import in.codebasket.cbwa.template.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Feeds RUNNING campaigns into the message queue (500 recipients per campaign per tick, one small
 * transaction per recipient) and starts SCHEDULED campaigns when they are due. Business errors that
 * affect the whole campaign pause it with a reason; per-recipient errors fail only that recipient.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignDispatcher {

    static final int BATCH = 500;

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final TemplateRepository templateRepository;
    private final CampaignService campaignService;
    private final ObjectProvider<OutboundMessageService> outbound;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void tick() {
        activateDue();
        for (Campaign c : campaignRepository.findByStatus(CampaignStatus.RUNNING)) {
            try {
                dispatch(c.getId());
            } catch (RuntimeException e) {
                log.warn("Campaign {} dispatch failed: {}", c.getId(), e.getMessage());
            }
        }
    }

    /** SCHEDULED campaigns whose time has come become RUNNING. */
    public int activateDue() {
        int n = 0;
        for (Campaign c : campaignRepository.findByStatusAndScheduledAtLessThanEqual(CampaignStatus.SCHEDULED, Instant.now())) {
            Boolean changed = tx.execute(s -> {
                Campaign locked = campaignRepository.lockById(c.getId()).orElse(null);
                if (locked == null || locked.getStatus() != CampaignStatus.SCHEDULED) {
                    return false;
                }
                locked.setStatus(CampaignStatus.RUNNING);
                locked.setStartedAt(Instant.now());
                campaignRepository.save(locked);
                return true;
            });
            if (Boolean.TRUE.equals(changed)) {
                n++;
            }
        }
        return n;
    }

    /** Queues up to BATCH pending recipients of a RUNNING campaign; returns how many were queued. */
    public int dispatch(Long campaignId) {
        OutboundMessageService sender = outbound.getIfAvailable();
        if (sender == null) {
            log.warn("Messaging engine not available - campaign {} not dispatched", campaignId);
            return 0;
        }
        Campaign c = campaignRepository.findById(campaignId).orElse(null);
        if (c == null || c.getStatus() != CampaignStatus.RUNNING) {
            return 0;
        }
        Template template = templateRepository.findById(c.getTemplateId()).orElseThrow();
        TemplateSpec spec = campaignService.spec(template);
        HeaderMedia headerMedia = CampaignService.headerMedia(c);
        List<CampaignRecipient> batch = recipientRepository.findTop500ByCampaignIdAndStatusOrderByIdAsc(campaignId, RecipientStatus.PENDING);
        int queued = 0;
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0 && i % 100 == 0 && !stillRunning(campaignId)) {
                break;
            }
            CampaignRecipient r = batch.get(i);
            try {
                Boolean done = tx.execute(s -> {
                    List<String> st = jdbc.queryForList("select status from campaign_recipients where id = ? for update", String.class, r.getId());
                    if (st.isEmpty() || !RecipientStatus.PENDING.name().equals(st.get(0))) {
                        return false;
                    }
                    Map<String, String> vars = r.getVariables() == null ? Map.of() : r.getVariables();
                    Message m = sender.enqueue(new OutboundCommand(c.getPartnerId(), c.getPhoneNumberId(), r.getPhone(),
                            spec.request(c.getPhoneNumberId(), r.getPhone(), vars, headerMedia), MessageSource.CAMPAIGN, null,
                            c.getId(), r.getId(), r.getContactId(), 5));
                    jdbc.update("update campaign_recipients set status = 'QUEUED', message_id = ?, updated_at = utc_timestamp(6) where id = ?",
                            m.getId(), r.getId());
                    return true;
                });
                if (Boolean.TRUE.equals(done)) {
                    queued++;
                }
            } catch (AppException e) {
                String pauseReason = switch (e.getCode()) {
                    case WALLET_INSUFFICIENT_BALANCE -> "INSUFFICIENT_BALANCE";
                    case SENDER_PAUSED, SENDER_NOT_AVAILABLE, META_TOKEN_INVALID, SENDER_DAILY_CAP_REACHED -> "SENDER_QUALITY";
                    case TEMPLATE_NOT_APPROVED -> "TEMPLATE_PAUSED";
                    case RATE_NOT_CONFIGURED, INTERNAL_ERROR -> "SYSTEM";
                    default -> null;
                };
                if (pauseReason != null) {
                    pause(campaignId, pauseReason, e.getMessage());
                    break;
                }
                failRecipient(r.getId(), e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Campaign {} recipient {} failed: {}", campaignId, r.getId(), e.getMessage());
                failRecipient(r.getId(), "Could not queue this message");
            }
        }
        return queued;
    }

    private boolean stillRunning(Long campaignId) {
        List<String> st = jdbc.queryForList("select status from campaigns where id = ?", String.class, campaignId);
        return !st.isEmpty() && CampaignStatus.RUNNING.name().equals(st.get(0));
    }

    private void failRecipient(Long recipientId, String message) {
        jdbc.update("update campaign_recipients set status = 'FAILED', error_message = ?, updated_at = utc_timestamp(6) where id = ? and status = 'PENDING'",
                CampaignService.truncate(message, 255), recipientId);
    }

    private void pause(Long campaignId, String reason, String detail) {
        tx.executeWithoutResult(s -> {
            Campaign c = campaignRepository.lockById(campaignId).orElse(null);
            if (c == null || c.getStatus() != CampaignStatus.RUNNING) {
                return;
            }
            c.setStatus(CampaignStatus.PAUSED);
            c.setPauseReason(reason);
            campaignRepository.save(c);
            String body = switch (reason) {
                case "INSUFFICIENT_BALANCE" -> "Your wallet balance ran out. Add money and resume the campaign.";
                case "SENDER_QUALITY" -> "The sender number cannot send right now: " + detail;
                case "TEMPLATE_PAUSED" -> "The template is no longer approved: " + detail;
                default -> detail;
            };
            notifications.notifyPartner(c.getPartnerId(), "CAMPAIGN_PAUSED", NotificationService.WARNING,
                    "Campaign paused: " + c.getName(), body, "/partner/campaigns/" + c.getId(), null);
        });
    }
}
