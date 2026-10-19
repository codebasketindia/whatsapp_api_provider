package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.partner.PartnerStatus;
import in.codebasket.cbwa.pricing.RatePlan;
import in.codebasket.cbwa.pricing.RatePlanRepository;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletService;
import in.codebasket.cbwa.wallet.WalletTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * D6 TECH_PROVIDER mode: monthly platform fee per partner-own number (rate plan
 * platform_fee_monthly_per_number). Runs daily at 00:30 IST: the current month is charged once per
 * number (platform_fee_charges is UNIQUE per number + month). With insufficient balance the charge is
 * retried daily; after CHARGING.platform_fee_grace_days failed days the number is paused until paid.
 * The wallet never goes negative.
 */
@Slf4j
@Component
public class PlatformFeeJob {

    public static final String STATUS_CHARGED = "CHARGED";
    public static final String STATUS_INSUFFICIENT = "SKIPPED_INSUFFICIENT";
    public static final String STATUS_PAUSED = "PAUSED_UNPAID";
    public static final String PAUSE_REASON = "Monthly platform fee unpaid - add money to resume";

    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final PartnerRepository partnerRepository;
    private final RatePlanRepository ratePlanRepository;
    private final PlatformFeeChargeRepository chargeRepository;
    private final WalletService walletService;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public PlatformFeeJob(PartnerPhoneNumberRepository assignmentRepository, PhoneNumberRepository phoneNumberRepository,
                          WhatsappAccountRepository wabaRepository, PartnerRepository partnerRepository,
                          RatePlanRepository ratePlanRepository, PlatformFeeChargeRepository chargeRepository,
                          WalletService walletService, SettingsService settings, NotificationService notifications,
                          PlatformTransactionManager txManager) {
        this.assignmentRepository = assignmentRepository;
        this.phoneNumberRepository = phoneNumberRepository;
        this.wabaRepository = wabaRepository;
        this.partnerRepository = partnerRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.chargeRepository = chargeRepository;
        this.walletService = walletService;
        this.settings = settings;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
    }

    public record Summary(int charged, int insufficient, int paused) {
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Kolkata")
    public void run() {
        try {
            Summary s = runFor(TimeUtil.todayIst());
            if (s.charged() + s.insufficient() + s.paused() > 0) {
                log.info("Platform fees: charged={} insufficient={} paused={}", s.charged(), s.insufficient(), s.paused());
            }
        } catch (RuntimeException e) {
            log.error("Platform fee job failed", e);
        }
    }

    public Summary runFor(LocalDate today) {
        String period = YearMonth.from(today).toString();
        int graceDays = settings.getInt(SettingKeys.CHARGING, SettingKeys.PLATFORM_FEE_GRACE_DAYS, 7);
        int charged = 0;
        int insufficient = 0;
        int paused = 0;
        for (PartnerPhoneNumber a : assignmentRepository.findByActiveTrue()) {
            if (a.getAssignmentType() != AssignmentType.OWN) {
                continue;
            }
            PhoneNumber pn = phoneNumberRepository.findById(a.getPhoneNumberId()).orElse(null);
            if (pn == null || pn.getSenderType() != SenderType.PARTNER_OWN_SENDER || pn.getStatus() != SenderStatus.CONNECTED) {
                continue;
            }
            WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElse(null);
            Partner partner = partnerRepository.findById(a.getPartnerId()).orElse(null);
            if (waba == null || waba.getBillingMode() != BillingMode.TECH_PROVIDER || partner == null
                    || partner.getStatus() != PartnerStatus.ACTIVE) {
                continue;
            }
            RatePlan plan = ratePlanRepository.findById(partner.getRatePlanId()).orElse(null);
            BigDecimal fee = plan == null ? BigDecimal.ZERO : plan.getPlatformFeeMonthlyPerNumber();
            if (fee == null || fee.signum() <= 0) {
                continue;
            }
            PlatformFeeCharge charge = chargeRepository.findByPhoneNumberIdAndPeriod(pn.getId(), period).orElse(null);
            if (charge != null && (STATUS_CHARGED.equals(charge.getStatus())
                    || (charge.getUpdatedAt() != null && TimeUtil.toIstDate(charge.getUpdatedAt()).equals(today) && charge.getAttempts() > 0))) {
                continue; // paid, or already tried today
            }
            String result = chargeOne(partner, pn, period, MoneyUtil.scale(fee), graceDays);
            switch (result) {
                case STATUS_CHARGED -> charged++;
                case STATUS_PAUSED -> paused++;
                default -> insufficient++;
            }
        }
        return new Summary(charged, insufficient, paused);
    }

    private String chargeOne(Partner partner, PhoneNumber pn, String period, BigDecimal fee, int graceDays) {
        String label = pn.getDisplayPhoneNumber() == null ? pn.getPhoneNumberId() : pn.getDisplayPhoneNumber();
        try {
            return tx.execute(status -> {
                WalletTransaction t = walletService.debit(partner.getId(), fee, WalletRefType.PLATFORM_FEE, period + ":" + pn.getId(),
                        "Monthly platform fee " + period + " for " + label);
                PlatformFeeCharge c = chargeRepository.findByPhoneNumberIdAndPeriod(pn.getId(), period).orElseGet(PlatformFeeCharge::new);
                boolean wasPaused = STATUS_PAUSED.equals(c.getStatus());
                c.setPartnerId(partner.getId());
                c.setPhoneNumberId(pn.getId());
                c.setPeriod(period);
                c.setAmount(fee);
                c.setStatus(STATUS_CHARGED);
                c.setAttempts(c.getAttempts() + 1);
                c.setWalletTransactionId(t.getId());
                chargeRepository.save(c);
                PhoneNumber fresh = phoneNumberRepository.findById(pn.getId()).orElseThrow();
                if (wasPaused || (fresh.isPaused() && PAUSE_REASON.equals(fresh.getPauseReason()))) {
                    fresh.setPaused(false);
                    fresh.setPauseReason(null);
                    phoneNumberRepository.save(fresh);
                }
                return STATUS_CHARGED;
            });
        } catch (AppException insufficientBalance) {
            return tx.execute(status -> {
                PlatformFeeCharge c = chargeRepository.findByPhoneNumberIdAndPeriod(pn.getId(), period).orElseGet(PlatformFeeCharge::new);
                c.setPartnerId(partner.getId());
                c.setPhoneNumberId(pn.getId());
                c.setPeriod(period);
                c.setAmount(fee);
                c.setAttempts(c.getAttempts() + 1);
                String amount = "₹" + MoneyUtil.rupees(fee).toPlainString();
                if (c.getAttempts() > graceDays && !STATUS_PAUSED.equals(c.getStatus())) {
                    c.setStatus(STATUS_PAUSED);
                    chargeRepository.save(c);
                    PhoneNumber fresh = phoneNumberRepository.findById(pn.getId()).orElseThrow();
                    fresh.setPaused(true);
                    fresh.setPauseReason(PAUSE_REASON);
                    phoneNumberRepository.save(fresh);
                    notifications.notifyPartner(partner.getId(), "PLATFORM_FEE_PAUSED", NotificationService.ERROR,
                            "Number " + label + " is paused", "The monthly platform fee of " + amount + " for " + period
                                    + " could not be charged. Add money to your wallet and the number will resume automatically.",
                            "/partner/wallet/add", partner.getEmail());
                    notifications.notifyAdmins("PLATFORM_FEE_PAUSED", NotificationService.WARNING, "Sender paused for unpaid fee",
                            partner.getBusinessName() + " - " + label + " (" + period + ")", "/admin/partners/" + partner.getId());
                    return STATUS_PAUSED;
                }
                if (!STATUS_PAUSED.equals(c.getStatus())) {
                    c.setStatus(STATUS_INSUFFICIENT);
                }
                chargeRepository.save(c);
                if (c.getAttempts() == 1) {
                    notifications.notifyPartner(partner.getId(), "PLATFORM_FEE_DUE", NotificationService.WARNING,
                            "Platform fee due for " + label, "We could not charge the monthly platform fee of " + amount + " for "
                                    + period + ". Please add money within " + graceDays + " days to keep sending from this number.",
                            "/partner/wallet/add", partner.getEmail());
                }
                return c.getStatus();
            });
        }
    }
}
