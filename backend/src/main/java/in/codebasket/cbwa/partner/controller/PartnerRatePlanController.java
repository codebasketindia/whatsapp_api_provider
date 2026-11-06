package in.codebasket.cbwa.partner.controller;

import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.repository.PartnerRepository;

import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.common.dto.ApiResponse;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.admin.dto.PartnerDtos.PartnerRatePlanDto;
import in.codebasket.cbwa.admin.dto.PartnerDtos.RateItemDto;
import in.codebasket.cbwa.common.entity.RatePlan;
import in.codebasket.cbwa.common.repository.RatePlanItemRepository;
import in.codebasket.cbwa.common.repository.RatePlanRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** R7: a partner sees the prices of its own rate plan. */
@Tag(name = "Partner - Rate plan")
@RestController
@RequiredArgsConstructor
public class PartnerRatePlanController {

    private final PartnerRepository partnerRepository;
    private final RatePlanRepository ratePlanRepository;
    private final RatePlanItemRepository itemRepository;

    @GetMapping("/api/partner/rate-plan")
    @Transactional(readOnly = true)
    public ApiResponse<PartnerRatePlanDto> myPlan() {
        Partner p = partnerRepository.findById(SecurityUtils.currentPartnerId()).orElseThrow(() -> AppException.notFound("Partner"));
        RatePlan plan = ratePlanRepository.findById(p.getRatePlanId()).orElseThrow(() -> AppException.notFound("Rate plan"));
        var items = itemRepository.findByRatePlanIdOrderByCountryCodeAscCategoryAsc(plan.getId()).stream()
                .map(i -> new RateItemDto(i.getCountryCode(), i.getCategory().name(), i.getPrice())).toList();
        return ApiResponse.ok(new PartnerRatePlanDto(plan.getId(), plan.getName(), plan.getDescription(), plan.getCurrency(),
                plan.getSessionMessageFee(), plan.getPlatformFeePerMessage(), plan.getPlatformFeeMonthlyPerNumber(), items));
    }
}
