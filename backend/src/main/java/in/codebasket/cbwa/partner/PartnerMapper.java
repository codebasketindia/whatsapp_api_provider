package in.codebasket.cbwa.partner;

import in.codebasket.cbwa.partner.dto.PartnerDtos.AddressDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.PartnerDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.RatePlanRef;
import in.codebasket.cbwa.partner.dto.PartnerDtos.WalletRef;
import in.codebasket.cbwa.pricing.RatePlan;
import in.codebasket.cbwa.pricing.RatePlanRepository;
import in.codebasket.cbwa.wallet.Wallet;
import in.codebasket.cbwa.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds PartnerDto with batched lookups of wallets and rate plans (no N+1 on list pages). */
@Component
@RequiredArgsConstructor
public class PartnerMapper {

    private final WalletRepository walletRepository;
    private final RatePlanRepository ratePlanRepository;

    public PartnerDto toDto(Partner p) {
        return toDtos(List.of(p)).get(0);
    }

    public List<PartnerDto> toDtos(Collection<Partner> partners) {
        Set<Long> ids = partners.stream().map(Partner::getId).collect(Collectors.toSet());
        Set<Long> planIds = partners.stream().map(Partner::getRatePlanId).collect(Collectors.toSet());
        Map<Long, Wallet> wallets = ids.isEmpty() ? Map.of()
                : walletRepository.findByPartnerIdIn(ids).stream().collect(Collectors.toMap(Wallet::getPartnerId, Function.identity()));
        Map<Long, RatePlan> plans = ratePlanRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(RatePlan::getId, Function.identity()));
        return partners.stream().map(p -> map(p, wallets.get(p.getId()), plans.get(p.getRatePlanId()))).toList();
    }

    private static PartnerDto map(Partner p, Wallet w, RatePlan plan) {
        return new PartnerDto(p.getId(), p.getUid(), p.getPartnerCode(), p.getBusinessName(), p.getContactName(), p.getEmail(),
                p.getMobile(), p.getGstin(), p.getPan(),
                new AddressDto(p.getAddressLine1(), p.getAddressLine2(), p.getCity(), p.getState(), p.getStateCode(),
                        p.getPincode(), p.getCountry()),
                p.getWebsite(), p.getStatus().name(), p.getSuspendReason(), p.isKycVerified(), p.isAllowOwnNumber(),
                plan == null ? null : new RatePlanRef(plan.getId(), plan.getName()),
                w == null ? null : new WalletRef(w.getBalance(), w.getHeldAmount()), p.getCreatedAt());
    }
}
