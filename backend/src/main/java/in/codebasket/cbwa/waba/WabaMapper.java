package in.codebasket.cbwa.waba;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.waba.dto.WabaDtos.PartnerRef;
import in.codebasket.cbwa.waba.dto.WabaDtos.WabaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WabaMapper {

    private final PartnerRepository partnerRepository;

    public WabaDto toDto(WhatsappAccount w) {
        PartnerRef partner = w.getPartnerId() == null ? null : partnerRepository.findById(w.getPartnerId())
                .map(p -> new PartnerRef(p.getId(), p.getBusinessName())).orElse(null);
        return new WabaDto(w.getId(), w.getWabaId(), w.getOwnerType().name(), partner, w.getName(), w.getBusinessId(),
                w.getAccountReviewStatus(), w.getBusinessVerificationStatus(), w.getPaymentMethodAttached(),
                w.getTokenStatus().name(), w.getTokenCheckedAt(), w.isWebhookSubscribed(), w.getOnboardingType().name(),
                w.getBillingMode().name(), w.isCreditLineShared(), w.getStatus().name(), w.getConnectedAt());
    }

    /** Copies GET /{WABA_ID} fields onto the entity. */
    public static void apply(WhatsappAccount w, JsonNode n) {
        if (n.hasNonNull("name")) {
            w.setName(n.get("name").asText());
        }
        if (n.hasNonNull("currency")) {
            w.setCurrency(n.get("currency").asText());
        }
        if (n.hasNonNull("timezone_id")) {
            w.setTimezoneId(n.get("timezone_id").asText());
        }
        if (n.hasNonNull("message_template_namespace")) {
            w.setMessageTemplateNamespace(n.get("message_template_namespace").asText());
        }
        if (n.hasNonNull("account_review_status")) {
            w.setAccountReviewStatus(n.get("account_review_status").asText());
        }
        if (n.hasNonNull("business_verification_status")) {
            w.setBusinessVerificationStatus(n.get("business_verification_status").asText());
        }
        if (n.path("owner_business_info").hasNonNull("id") && w.getBusinessId() == null) {
            w.setBusinessId(n.path("owner_business_info").get("id").asText());
        }
        w.setPaymentMethodAttached(n.hasNonNull("primary_funding_id") ? Boolean.TRUE : w.getPaymentMethodAttached());
        w.setRaw(n.toString());
    }
}
