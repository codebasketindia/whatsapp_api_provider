package in.codebasket.cbwa.wallet;

import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerEvent;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.partner.PartnerSettings;
import in.codebasket.cbwa.partner.PartnerSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * D1 low-balance alert: when the balance drops below the partner threshold (or the admin default)
 * the partner gets an in-app notification + email at most once per 24 hours. The alert re-arms
 * when the balance goes back above the threshold.
 */
@Component
@RequiredArgsConstructor
public class LowBalanceMonitor {

    private final PartnerSettingsRepository settingsRepository;
    private final PartnerRepository partnerRepository;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    public BigDecimal thresholdFor(Long partnerId) {
        BigDecimal def = settings.getDecimal(SettingKeys.WALLET, SettingKeys.DEFAULT_LOW_BALANCE, new BigDecimal("500"));
        BigDecimal own = settingsRepository.findById(partnerId).map(PartnerSettings::getLowBalanceThreshold).orElse(null);
        return own != null ? own : def;
    }

    void onBalanceChanged(Long partnerId, BigDecimal before, BigDecimal after) {
        PartnerSettings ps = settingsRepository.findById(partnerId).orElse(null);
        if (ps == null) {
            return;
        }
        BigDecimal threshold = ps.getLowBalanceThreshold() != null ? ps.getLowBalanceThreshold()
                : settings.getDecimal(SettingKeys.WALLET, SettingKeys.DEFAULT_LOW_BALANCE, new BigDecimal("500"));
        if (threshold.signum() <= 0) {
            return;
        }
        boolean low = after.compareTo(threshold) < 0;
        if (!low) {
            if (ps.getLowBalanceAlertedAt() != null) {
                ps.setLowBalanceAlertedAt(null);
                settingsRepository.save(ps);
            }
            return;
        }
        Instant last = ps.getLowBalanceAlertedAt();
        if (last != null && last.isAfter(Instant.now().minus(Duration.ofHours(24)))) {
            return;
        }
        ps.setLowBalanceAlertedAt(Instant.now());
        settingsRepository.save(ps);
        Partner partner = partnerRepository.findById(partnerId).orElse(null);
        String body = "Your wallet balance is ₹" + MoneyUtil.rupees(after).toPlainString() + ", below your alert level of ₹"
                + MoneyUtil.rupees(threshold).toPlainString() + ". Add money to keep your messages flowing.";
        notifications.notifyPartner(partnerId, "LOW_BALANCE", NotificationService.WARNING, "Low wallet balance", body,
                "/partner/wallet/add", partner != null && ps.isNotifyEmail() ? partner.getEmail() : null);
        events.publishEvent(new PartnerEvent(partnerId, PartnerEvent.WALLET_LOW_BALANCE,
                Map.of("balance", MoneyUtil.str(after), "threshold", MoneyUtil.str(threshold))));
    }
}
