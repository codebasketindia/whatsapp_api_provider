package in.codebasket.cbwa.sender;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.dto.SenderDtos.Health;
import in.codebasket.cbwa.sender.dto.SenderDtos.HealthError;
import in.codebasket.cbwa.sender.dto.SenderDtos.PartnerRef;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.WabaRef;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds SenderDto and copies Graph API phone-number fields onto the entity. */
@Component
@RequiredArgsConstructor
public class SenderMapper {

    private final WhatsappAccountRepository wabaRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PartnerRepository partnerRepository;
    private final SenderStatsRepository statsRepository;

    public SenderDto toDto(PhoneNumber pn) {
        WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElse(null);
        PartnerPhoneNumber assignment = assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).orElse(null);
        Partner partner = assignment == null ? null : partnerRepository.findById(assignment.getPartnerId()).orElse(null);
        Instant since = TimeUtil.startOfDayIst(TimeUtil.todayIst());
        return new SenderDto(pn.getId(), pn.getPhoneNumberId(), pn.getDisplayPhoneNumber(), pn.getVerifiedName(),
                pn.getNameStatus(), pn.getNewNameStatus(), pn.getQualityRating(), pn.getMessagingLimitTier(),
                pn.getThroughputLevel(), pn.getMaxMps(), pn.getMetaStatus(), health(pn.getHealthStatus()),
                pn.getSenderType().name(), pn.isCoexistence(), pn.isRegistered(), pn.isPaused(), pn.getPauseReason(),
                pn.getDailySendCap(), statsRepository.countOutboundSince(pn.getId(), since), pn.getStatus().name(),
                waba == null ? null : new WabaRef(waba.getId(), waba.getWabaId(), waba.getName(), waba.getTokenStatus().name()),
                partner == null ? null : new PartnerRef(partner.getId(), partner.getBusinessName()),
                assignment != null && assignment.isDefaultSender(), pn.getConnectedAt(), pn.getLastSyncedAt());
    }

    public static Health health(String json) {
        if (json == null || json.isBlank()) {
            return new Health(null, List.of());
        }
        JsonNode h = JsonUtil.readTree(json);
        List<HealthError> errors = new ArrayList<>();
        for (JsonNode entity : h.path("entities")) {
            for (JsonNode e : entity.path("errors")) {
                errors.add(new HealthError(entity.path("entity_type").asText(null),
                        e.hasNonNull("error_code") ? e.get("error_code").asInt() : null,
                        e.path("error_description").asText(null), e.path("possible_solution").asText(null)));
            }
        }
        return new Health(h.path("can_send_message").asText(null), errors);
    }

    /** Copies fields returned by GET /{PHONE_NUMBER_ID} (only those present) onto the entity. */
    public static void apply(PhoneNumber pn, JsonNode n) {
        if (n.hasNonNull("display_phone_number")) {
            pn.setDisplayPhoneNumber(n.get("display_phone_number").asText());
            pn.setWaNumber(n.get("display_phone_number").asText().replaceAll("[^0-9]", ""));
        }
        if (n.hasNonNull("verified_name")) {
            pn.setVerifiedName(n.get("verified_name").asText());
        }
        if (n.hasNonNull("name_status")) {
            pn.setNameStatus(n.get("name_status").asText());
        }
        if (n.has("new_name_status")) {
            pn.setNewNameStatus(n.path("new_name_status").asText(null));
        }
        if (n.hasNonNull("quality_rating")) {
            pn.setQualityRating(normaliseQuality(n.get("quality_rating").asText()));
        }
        if (n.hasNonNull("messaging_limit_tier")) {
            pn.setMessagingLimitTier(n.get("messaging_limit_tier").asText());
        }
        if (n.path("throughput").hasNonNull("level")) {
            pn.setThroughputLevel(n.path("throughput").get("level").asText());
        }
        if (n.hasNonNull("code_verification_status")) {
            pn.setCodeVerificationStatus(n.get("code_verification_status").asText());
        }
        if (n.hasNonNull("platform_type")) {
            pn.setPlatformType(n.get("platform_type").asText());
        }
        if (n.hasNonNull("status")) {
            pn.setMetaStatus(n.get("status").asText());
        }
        if (n.has("health_status") && !n.get("health_status").isNull()) {
            pn.setHealthStatus(n.get("health_status").toString());
        }
        pn.setRaw(n.toString());
        pn.setLastSyncedAt(Instant.now());
    }

    public static String normaliseQuality(String q) {
        if (q == null) {
            return "UNKNOWN";
        }
        String u = q.toUpperCase();
        return switch (u) {
            case "GREEN", "YELLOW", "RED" -> u;
            case "HIGH" -> "GREEN";
            case "MEDIUM" -> "YELLOW";
            case "LOW" -> "RED";
            default -> "UNKNOWN";
        };
    }
}
