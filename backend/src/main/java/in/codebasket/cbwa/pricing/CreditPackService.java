package in.codebasket.cbwa.pricing;

import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.payment.PaymentOrderRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** D4 credit packs ("Buy messages"): pay ₹X, get ₹X + bonus wallet credit. */
@Service
@RequiredArgsConstructor
public class CreditPackService {

    private final CreditPackRepository repository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final AuditService auditService;

    public record CreditPackDto(Long id, String name, String description, BigDecimal price, String bonusType,
                                BigDecimal bonusValue, BigDecimal bonusAmount, BigDecimal creditAmount, boolean isPopular,
                                boolean isActive, int sortOrder) {
    }

    public record CreditPackRequest(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description,
                                    @NotNull @DecimalMin(value = "1", message = "Price must be at least ₹1")
                                    @DecimalMax(value = "10000000") @Digits(integer = 8, fraction = 2) BigDecimal price,
                                    @NotBlank @Pattern(regexp = "FLAT|PERCENT") String bonusType,
                                    @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal bonusValue,
                                    boolean isPopular, boolean isActive, int sortOrder) {
    }

    @Transactional(readOnly = true)
    public List<CreditPackDto> list(Boolean active) {
        List<CreditPack> packs = Boolean.TRUE.equals(active) ? repository.findByActiveTrueOrderBySortOrderAscIdAsc()
                : repository.findAllByOrderBySortOrderAscIdAsc();
        return packs.stream().filter(p -> active == null || active || !p.isActive()).map(CreditPackService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CreditPack requireActive(Long id) {
        CreditPack p = repository.findById(id).orElseThrow(() -> AppException.field("creditPackId", "Credit pack not found"));
        if (!p.isActive()) {
            throw AppException.field("creditPackId", "This credit pack is not available");
        }
        return p;
    }

    @Transactional
    public CreditPackDto create(CreditPackRequest req) {
        CreditPack p = new CreditPack();
        apply(p, req);
        repository.save(p);
        auditService.log("CREDIT_PACK_CREATE", "CREDIT_PACK", p.getId(), null, toDto(p));
        return toDto(p);
    }

    @Transactional
    public CreditPackDto update(Long id, CreditPackRequest req) {
        CreditPack p = repository.findById(id).orElseThrow(() -> AppException.notFound("Credit pack"));
        CreditPackDto old = toDto(p);
        apply(p, req);
        repository.save(p);
        auditService.log("CREDIT_PACK_UPDATE", "CREDIT_PACK", id, old, toDto(p));
        return toDto(p);
    }

    /** Deletes an unused pack; a pack that was ever bought is only deactivated (keeps payment history intact). */
    @Transactional
    public boolean delete(Long id) {
        CreditPack p = repository.findById(id).orElseThrow(() -> AppException.notFound("Credit pack"));
        CreditPackDto old = toDto(p);
        if (paymentOrderRepository.countByCreditPackId(id) > 0) {
            p.setActive(false);
            repository.save(p);
            auditService.log("CREDIT_PACK_DEACTIVATE", "CREDIT_PACK", id, old, toDto(p));
            return false;
        }
        repository.delete(p);
        auditService.log("CREDIT_PACK_DELETE", "CREDIT_PACK", id, old, null);
        return true;
    }

    public static BigDecimal bonusOf(CreditPack p) {
        BigDecimal bonus = p.getBonusType() == BonusType.FLAT ? p.getBonusValue() : MoneyUtil.percentOf(p.getPrice(), p.getBonusValue());
        return MoneyUtil.scale(MoneyUtil.rupees(bonus));
    }

    public static CreditPackDto toDto(CreditPack p) {
        BigDecimal bonus = bonusOf(p);
        return new CreditPackDto(p.getId(), p.getName(), p.getDescription(), MoneyUtil.scale(p.getPrice()), p.getBonusType().name(),
                MoneyUtil.scale(p.getBonusValue()), bonus, MoneyUtil.scale(p.getPrice().add(bonus)), p.isPopular(), p.isActive(),
                p.getSortOrder());
    }

    private static void apply(CreditPack p, CreditPackRequest req) {
        BonusType type = BonusType.valueOf(req.bonusType());
        if (type == BonusType.PERCENT && req.bonusValue().compareTo(new BigDecimal("100")) > 0) {
            throw AppException.field("bonusValue", "Bonus percent can be at most 100");
        }
        p.setName(req.name().trim());
        p.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        p.setPrice(MoneyUtil.scale(req.price()));
        p.setBonusType(type);
        p.setBonusValue(MoneyUtil.scale(req.bonusValue()));
        p.setPopular(req.isPopular());
        p.setActive(req.isActive());
        p.setSortOrder(req.sortOrder());
    }
}
