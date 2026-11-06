package in.codebasket.cbwa.common.service.pricing;

import in.codebasket.cbwa.common.entity.RatePlan;
import in.codebasket.cbwa.common.repository.RatePlanItemRepository;
import in.codebasket.cbwa.common.repository.RatePlanRepository;

import in.codebasket.cbwa.exception.ErrorCode;
import in.codebasket.cbwa.common.constants.BillingMode;
import in.codebasket.cbwa.common.constants.MessageCategory;
import in.codebasket.cbwa.common.constants.SenderType;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.common.utils.MoneyUtil;
import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.repository.PartnerRepository;
import in.codebasket.cbwa.common.entity.PhoneNumber;
import in.codebasket.cbwa.common.entity.WhatsappAccount;
import in.codebasket.cbwa.common.repository.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * D5/D6 price per message for a partner:
 *  - Partner-own number in TECH_PROVIDER mode: Meta bills the partner directly, we charge only the
 *    plan's platform fee per message.
 *  - Platform number, or partner-own number in SOLUTION_PARTNER mode: template messages use the
 *    rate plan item for (country, category), falling back to country "*"; free-form (SERVICE)
 *    messages use the plan's session message fee.
 * No configured price -> RATE_NOT_CONFIGURED (nothing is sent).
 */
@Service
@RequiredArgsConstructor
public class PriceResolver {

    public static final String ANY_COUNTRY = "*";

    private final PartnerRepository partnerRepository;
    private final RatePlanRepository ratePlanRepository;
    private final RatePlanItemRepository itemRepository;
    private final WhatsappAccountRepository wabaRepository;

    @Transactional(readOnly = true)
    public BigDecimal priceFor(Long partnerId, PhoneNumber sender, MessageCategory category, String countryCode) {
        Partner partner = partnerRepository.findById(partnerId).orElseThrow(() -> AppException.notFound("Partner"));
        RatePlan plan = ratePlanRepository.findById(partner.getRatePlanId())
                .orElseThrow(() -> new AppException(ErrorCode.RATE_NOT_CONFIGURED, "No rate plan is assigned to your account"));
        WhatsappAccount waba = wabaRepository.findById(sender.getWhatsappAccountId()).orElseThrow(() -> AppException.notFound("WhatsApp account"));
        boolean techProviderOwn = sender.getSenderType() == SenderType.PARTNER_OWN_SENDER && waba.getBillingMode() == BillingMode.TECH_PROVIDER;
        if (techProviderOwn) {
            return MoneyUtil.scale(plan.getPlatformFeePerMessage());
        }
        if (category == MessageCategory.SERVICE) {
            return MoneyUtil.scale(plan.getSessionMessageFee());
        }
        String country = countryCode == null || countryCode.isBlank() ? ANY_COUNTRY : countryCode.toUpperCase();
        return itemRepository.findByRatePlanIdAndCountryCodeAndCategory(plan.getId(), country, category)
                .or(() -> itemRepository.findByRatePlanIdAndCountryCodeAndCategory(plan.getId(), ANY_COUNTRY, category))
                .map(i -> MoneyUtil.scale(i.getPrice()))
                .orElseThrow(() -> new AppException(ErrorCode.RATE_NOT_CONFIGURED,
                        "Price for " + category.name().toLowerCase() + " messages to " + country
                                + " is not set in your plan. Please contact support"));
    }
}
