package in.codebasket.cbwa.campaign;

import in.codebasket.cbwa.campaign.dto.CampaignDtos.CostEstimateDto;
import in.codebasket.cbwa.campaign.dto.CampaignDtos.CountryLine;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.messaging.OutboundMessageService;
import in.codebasket.cbwa.pricing.PriceResolver;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.wallet.Wallet;
import in.codebasket.cbwa.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cost estimate for a set of recipients: one price lookup per country (prices do not depend on
 * the individual number), summed per country. Uses the messaging engine's unitPrice when present
 * and falls back to PriceResolver (same rules) otherwise.
 */
@Component
@RequiredArgsConstructor
public class CampaignPricing {

    private final ObjectProvider<OutboundMessageService> outbound;
    private final PriceResolver priceResolver;
    private final WalletRepository walletRepository;

    /** phonesByCountry: country code -> one example number and the recipient count for that country. */
    public CostEstimateDto estimate(Long partnerId, PhoneNumber sender, MessageCategory category,
                                    Map<String, CountryBucket> phonesByCountry) {
        List<CountryLine> lines = new ArrayList<>();
        BigDecimal total = MoneyUtil.ZERO;
        long recipients = 0;
        for (Map.Entry<String, CountryBucket> e : new TreeMap<>(phonesByCountry).entrySet()) {
            BigDecimal unit = unitPrice(partnerId, sender, category, e.getKey(), e.getValue().examplePhone());
            BigDecimal subtotal = MoneyUtil.scale(unit.multiply(BigDecimal.valueOf(e.getValue().count())));
            lines.add(new CountryLine(e.getKey(), e.getValue().count(), unit, subtotal));
            total = total.add(subtotal);
            recipients += e.getValue().count();
        }
        BigDecimal available = walletRepository.findByPartnerId(partnerId).map(Wallet::getBalance).orElse(MoneyUtil.ZERO);
        BigDecimal after = MoneyUtil.scale(available.subtract(total));
        return new CostEstimateDto(category.name(), lines, recipients, MoneyUtil.scale(total), MoneyUtil.scale(available), after,
                after.signum() >= 0);
    }

    public BigDecimal unitPrice(Long partnerId, PhoneNumber sender, MessageCategory category, String country, String phone) {
        OutboundMessageService svc = outbound.getIfAvailable();
        if (svc != null) {
            return MoneyUtil.scale(svc.unitPrice(partnerId, sender.getId(), category, phone));
        }
        return MoneyUtil.scale(priceResolver.priceFor(partnerId, sender, category, country));
    }

    public static final class CountryBucket {
        private final String examplePhone;
        private long count;

        public CountryBucket(String examplePhone) {
            this.examplePhone = examplePhone;
        }

        public String examplePhone() {
            return examplePhone;
        }

        public long count() {
            return count;
        }

        public void add() {
            count++;
        }
    }
}
