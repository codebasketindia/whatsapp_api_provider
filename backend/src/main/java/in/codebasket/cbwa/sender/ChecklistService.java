package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.domain.BillingMode;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.sender.dto.SenderDtos.ChecklistDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.ChecklistItem;
import in.codebasket.cbwa.waba.WhatsappAccount;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** F2 step 6: plain-language "What to do next" checklist for a connected number. */
@Component
public class ChecklistService {

    public static final String DONE = "DONE";
    public static final String PENDING = "PENDING";
    public static final String ACTION_REQUIRED = "ACTION_REQUIRED";

    private static final String WA_MANAGER = "https://business.facebook.com/wa/manage/home/";
    private static final String SECURITY_CENTER = "https://business.facebook.com/settings/security";

    public ChecklistDto build(PhoneNumber pn, WhatsappAccount waba) {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(paymentMethod(pn, waba));
        String bv = waba.getBusinessVerificationStatus() == null ? "" : waba.getBusinessVerificationStatus().toLowerCase();
        if (bv.equals("verified")) {
            items.add(new ChecklistItem("BUSINESS_VERIFICATION", DONE, "Business verified", "Your business is verified by Meta.", null));
        } else if (bv.equals("rejected") || bv.equals("failed")) {
            items.add(new ChecklistItem("BUSINESS_VERIFICATION", ACTION_REQUIRED, "Business verification was not approved",
                    "Open Meta Business Settings > Security Center and submit verification again with correct documents.", SECURITY_CENTER));
        } else {
            items.add(new ChecklistItem("BUSINESS_VERIFICATION", PENDING, "Verify your business",
                    "Without business verification you can message only a limited number of customers per day. "
                            + "Start verification in Meta Business Settings > Security Center.", SECURITY_CENTER));
        }
        items.add(displayName(pn));
        if (pn.isRegistered() || pn.isCoexistence()) {
            items.add(new ChecklistItem("NUMBER_REGISTERED", DONE, "Number registered",
                    pn.isCoexistence() ? "Number works with the WhatsApp Business App and the API together." : "The number is registered for the Cloud API.", null));
        } else {
            items.add(new ChecklistItem("NUMBER_REGISTERED", ACTION_REQUIRED, "Register the number",
                    "Verify the number with the code sent by SMS/voice if asked, then register it with a 6-digit PIN.", null));
        }
        items.add(waba.isWebhookSubscribed()
                ? new ChecklistItem("WEBHOOK", DONE, "Message updates connected", "Delivery ticks and replies will appear automatically.", null)
                : new ChecklistItem("WEBHOOK", ACTION_REQUIRED, "Message updates not connected",
                "We could not subscribe to updates for this account. Please contact support.", null));
        items.add(pn.getPinEnc() != null || pn.isCoexistence()
                ? new ChecklistItem("TWO_STEP_PIN", DONE, "Two-step PIN set", "Keep your 6-digit PIN safe.", null)
                : new ChecklistItem("TWO_STEP_PIN", PENDING, "Set a two-step PIN",
                "A 6-digit PIN protects your number from being registered elsewhere.", null));
        return new ChecklistDto(items);
    }

    private ChecklistItem paymentMethod(PhoneNumber pn, WhatsappAccount waba) {
        boolean ownTechProvider = pn.getSenderType() == SenderType.PARTNER_OWN_SENDER && waba.getBillingMode() == BillingMode.TECH_PROVIDER;
        if (!ownTechProvider) {
            if (waba.getBillingMode() == BillingMode.SOLUTION_PARTNER && !waba.isCreditLineShared()) {
                return new ChecklistItem("PAYMENT_METHOD", PENDING, "Billing being set up",
                        "We are attaching our credit line to your account. Please contact support if this stays pending.", null);
            }
            return new ChecklistItem("PAYMENT_METHOD", DONE, "Billing handled by us", "Meta charges are paid from your wallet.", null);
        }
        if (Boolean.TRUE.equals(waba.getPaymentMethodAttached())) {
            return new ChecklistItem("PAYMENT_METHOD", DONE, "Payment method added", "Meta bills your payment method directly.", null);
        }
        return new ChecklistItem("PAYMENT_METHOD", ACTION_REQUIRED, "Add a payment method",
                "Add a payment method in WhatsApp Manager > Account tools > Payment methods. Template messages fail until this is done.", WA_MANAGER);
    }

    private ChecklistItem displayName(PhoneNumber pn) {
        String s = pn.getNameStatus() == null ? "" : pn.getNameStatus().toUpperCase();
        return switch (s) {
            case "APPROVED", "AVAILABLE_WITHOUT_REVIEW" -> new ChecklistItem("DISPLAY_NAME", DONE, "Display name approved",
                    "Customers see \"" + nullToEmpty(pn.getVerifiedName()) + "\".", null);
            case "DECLINED", "REJECTED", "NON_EXISTS" -> new ChecklistItem("DISPLAY_NAME", ACTION_REQUIRED, "Display name not approved",
                    "The name must match your business name/brand and follow Meta's display name rules (no generic words only, "
                            + "no extra punctuation, correct capitalisation). Edit it in WhatsApp Manager > Phone numbers.", WA_MANAGER);
            default -> new ChecklistItem("DISPLAY_NAME", PENDING, "Display name in review",
                    "Meta usually reviews display names within 1-2 days. You can still send messages meanwhile.", null);
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
