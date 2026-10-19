package in.codebasket.cbwa.messaging;

import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.common.util.PhoneUtil;
import in.codebasket.cbwa.wallet.WalletRefType;
import in.codebasket.cbwa.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D5 charging rules applied to a message that is already row-locked by the caller:
 *  - HOLD is placed when the message is queued (OutboundMessageServiceImpl).
 *  - Immediate Meta rejection (never sent)        -> RELEASE, charge_status NONE.
 *  - Final status FAILED while the hold is open   -> REFUND of the hold, charge_status REFUNDED.
 *  - Meta says not billable (template category)   -> REFUND unless "charge_free_utility_in_window" is on.
 *  - charge_on = SENT: finalize on sent (or any later status); DELIVERED: finalize on delivered/read.
 * Every transition happens at most once because it is guarded by charge_status under the row lock.
 */
@Service
@RequiredArgsConstructor
public class ChargingService {

    private final WalletService walletService;
    private final SettingsService settings;

    public enum ChargeOn { SENT, DELIVERED }

    public ChargeOn chargeOn() {
        return "SENT".equalsIgnoreCase(settings.getString(SettingKeys.CHARGING, SettingKeys.CHARGE_ON, "DELIVERED"))
                ? ChargeOn.SENT : ChargeOn.DELIVERED;
    }

    /** Apply charging after the message status (and billable flag) has been updated. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onStatus(Message m) {
        if (m.getChargeStatus() != ChargeStatus.HELD) {
            return;
        }
        if (m.getStatus() == MessageStatus.FAILED) {
            refundHold(m, "Refund - message failed");
            return;
        }
        if (Boolean.FALSE.equals(m.getBillable()) && m.getCategory() != null && m.getCategory() != MessageCategory.SERVICE
                && !settings.getBool(SettingKeys.CHARGING, SettingKeys.CHARGE_FREE_UTILITY_IN_WINDOW, false)) {
            refundHold(m, "Refund - free message (not charged by WhatsApp)");
            return;
        }
        int rank = rank(m.getStatus());
        boolean finalize = chargeOn() == ChargeOn.SENT ? rank >= rank(MessageStatus.SENT) : rank >= rank(MessageStatus.DELIVERED);
        if (finalize) {
            finalizeCharge(m);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void finalizeCharge(Message m) {
        if (m.getChargeStatus() != ChargeStatus.HELD) {
            return;
        }
        walletService.finalizeHold(m.getPartnerId(), m.getPrice(), WalletRefType.MESSAGE, m.getUid(),
                "Message to " + PhoneUtil.mask(m.getWaId()));
        m.setChargeStatus(ChargeStatus.CHARGED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refundHold(Message m, String remarks) {
        if (m.getChargeStatus() != ChargeStatus.HELD) {
            return;
        }
        walletService.refundHold(m.getPartnerId(), m.getPrice(), WalletRefType.MESSAGE, m.getUid(), remarks);
        m.setChargeStatus(ChargeStatus.REFUNDED);
    }

    /** Message rejected by Meta before it was ever sent: the hold is released (not a refund). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Message m, String remarks) {
        if (m.getChargeStatus() != ChargeStatus.HELD) {
            return;
        }
        walletService.release(m.getPartnerId(), m.getPrice(), WalletRefType.MESSAGE, m.getUid(), remarks);
        m.setChargeStatus(ChargeStatus.NONE);
    }

    public static int rank(MessageStatus s) {
        return switch (s) {
            case QUEUED -> 0;
            case ACCEPTED -> 1;
            case SENT -> 2;
            case DELIVERED -> 3;
            case READ -> 4;
            default -> -1;
        };
    }
}
