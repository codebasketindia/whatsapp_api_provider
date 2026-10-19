package in.codebasket.cbwa.pricing;

import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.domain.MessageCategory;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.pricing.dto.RatePlanDtos.ItemDto;
import in.codebasket.cbwa.pricing.dto.RatePlanDtos.RatePlanDto;
import in.codebasket.cbwa.pricing.dto.RatePlanDtos.RatePlanRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** R1-R6: rate plans with per-country / per-category prices (D5). */
@Service
@RequiredArgsConstructor
public class RatePlanService {

    private final RatePlanRepository planRepository;
    private final RatePlanItemRepository itemRepository;
    private final RatePlanQueryRepository queryRepository;
    private final PartnerRepository partnerRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<RatePlanDto> list(String status, boolean includeCustom, Integer page, Integer size) {
        Specification<RatePlan> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), RatePlanStatus.valueOf(status)));
            }
            if (!includeCustom) {
                ps.add(cb.isFalse(root.get("custom")));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResponse.of(planRepository.findAll(spec, Paging.of(page, size, null, Set.of(),
                Sort.by(Sort.Order.desc("defaultPlan"), Sort.Order.asc("name")))), this::toDto);
    }

    @Transactional(readOnly = true)
    public RatePlanDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public RatePlanDto create(RatePlanRequest req) {
        RatePlan p = new RatePlan();
        p.setStatus(RatePlanStatus.ACTIVE);
        p.setCurrency("INR");
        apply(p, req);
        planRepository.save(p);
        replaceItems(p, req.items());
        if (req.isDefault()) {
            makeDefault(p);
        }
        RatePlanDto dto = toDto(p);
        auditService.log("RATE_PLAN_CREATE", "RATE_PLAN", p.getId(), null, dto);
        return dto;
    }

    @Transactional
    public RatePlanDto update(Long id, RatePlanRequest req) {
        RatePlan p = find(id);
        RatePlanDto old = toDto(p);
        if (p.isDefaultPlan() && !req.isDefault()) {
            throw AppException.field("isDefault", "Choose another default plan first");
        }
        apply(p, req);
        planRepository.save(p);
        replaceItems(p, req.items());
        if (req.isDefault()) {
            makeDefault(p);
        }
        RatePlanDto dto = toDto(p);
        auditService.log("RATE_PLAN_UPDATE", "RATE_PLAN", p.getId(), old, dto);
        return dto;
    }

    @Transactional
    public RatePlanDto clone(Long id, String name, Long partnerId) {
        RatePlan src = find(id);
        RatePlan p = new RatePlan();
        p.setName(name.trim());
        p.setDescription(src.getDescription());
        p.setCurrency(src.getCurrency());
        p.setSessionMessageFee(src.getSessionMessageFee());
        p.setPlatformFeePerMessage(src.getPlatformFeePerMessage());
        p.setPlatformFeeMonthlyPerNumber(src.getPlatformFeeMonthlyPerNumber());
        p.setStatus(RatePlanStatus.ACTIVE);
        setOwner(p, partnerId);
        planRepository.save(p);
        List<RatePlanItem> items = new ArrayList<>();
        for (RatePlanItem i : itemRepository.findByRatePlanIdOrderByCountryCodeAscCategoryAsc(src.getId())) {
            RatePlanItem c = new RatePlanItem();
            c.setRatePlanId(p.getId());
            c.setCountryCode(i.getCountryCode());
            c.setCategory(i.getCategory());
            c.setPrice(i.getPrice());
            items.add(c);
        }
        itemRepository.saveAll(items);
        RatePlanDto dto = toDto(p);
        auditService.log("RATE_PLAN_CLONE", "RATE_PLAN", p.getId(), null, java.util.Map.of("from", src.getId(), "name", p.getName()));
        return dto;
    }

    @Transactional
    public RatePlanDto setStatus(Long id, String status) {
        RatePlan p = find(id);
        RatePlanStatus s = RatePlanStatus.valueOf(status);
        if (s == RatePlanStatus.ARCHIVED) {
            if (p.isDefaultPlan()) {
                throw AppException.conflict("The default plan cannot be archived");
            }
            long inUse = partnerRepository.countByRatePlanId(p.getId());
            if (inUse > 0) {
                throw AppException.conflict("This plan is used by " + inUse + " partner(s). Move them to another plan first");
            }
        }
        RatePlanStatus old = p.getStatus();
        p.setStatus(s);
        planRepository.save(p);
        auditService.log("RATE_PLAN_STATUS", "RATE_PLAN", p.getId(), java.util.Map.of("status", old), java.util.Map.of("status", s));
        return toDto(p);
    }

    private void apply(RatePlan p, RatePlanRequest req) {
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setSessionMessageFee(MoneyUtil.scale(req.sessionMessageFee()));
        p.setPlatformFeePerMessage(MoneyUtil.scale(req.platformFeePerMessage()));
        p.setPlatformFeeMonthlyPerNumber(MoneyUtil.scale(req.platformFeeMonthlyPerNumber()));
        setOwner(p, req.partnerId());
        if (req.isDefault() && p.isCustom()) {
            throw AppException.field("isDefault", "A custom (partner-specific) plan cannot be the default plan");
        }
    }

    private void setOwner(RatePlan p, Long partnerId) {
        if (partnerId != null) {
            Partner partner = partnerRepository.findById(partnerId).orElseThrow(() -> AppException.field("partnerId", "Partner not found"));
            p.setPartnerId(partner.getId());
            p.setCustom(true);
        } else {
            p.setPartnerId(null);
            p.setCustom(false);
        }
    }

    private void replaceItems(RatePlan p, List<ItemDto> items) {
        Set<String> seen = new HashSet<>();
        for (ItemDto i : items) {
            if (!seen.add(i.countryCode() + "/" + i.category())) {
                throw AppException.field("items", "Duplicate price for " + i.countryCode() + " / " + i.category());
            }
        }
        itemRepository.deleteByRatePlanId(p.getId());
        itemRepository.flush();
        List<RatePlanItem> rows = new ArrayList<>();
        for (ItemDto i : items) {
            RatePlanItem r = new RatePlanItem();
            r.setRatePlanId(p.getId());
            r.setCountryCode(i.countryCode());
            r.setCategory(MessageCategory.valueOf(i.category()));
            r.setPrice(MoneyUtil.scale(i.price()));
            rows.add(r);
        }
        itemRepository.saveAll(rows);
    }

    private void makeDefault(RatePlan p) {
        for (RatePlan other : planRepository.findByDefaultPlanTrue()) {
            if (!other.getId().equals(p.getId())) {
                other.setDefaultPlan(false);
                planRepository.save(other);
            }
        }
        p.setDefaultPlan(true);
        p.setStatus(RatePlanStatus.ACTIVE);
        planRepository.save(p);
    }

    private RatePlan find(Long id) {
        return planRepository.findById(id).orElseThrow(() -> AppException.notFound("Rate plan"));
    }

    private RatePlanDto toDto(RatePlan p) {
        List<ItemDto> items = itemRepository.findByRatePlanIdOrderByCountryCodeAscCategoryAsc(p.getId()).stream()
                .map(i -> new ItemDto(i.getCountryCode(), i.getCategory().name(), i.getPrice())).toList();
        String partnerName = p.getPartnerId() == null ? null
                : partnerRepository.findById(p.getPartnerId()).map(Partner::getBusinessName).orElse(null);
        return new RatePlanDto(p.getId(), p.getName(), p.getDescription(), p.isDefaultPlan(), p.isCustom(), p.getPartnerId(),
                partnerName, p.getSessionMessageFee(), p.getPlatformFeePerMessage(), p.getPlatformFeeMonthlyPerNumber(),
                p.getStatus().name(), items, queryRepository.countPartners(p.getId()), p.getCreatedAt(), p.getUpdatedAt());
    }
}
