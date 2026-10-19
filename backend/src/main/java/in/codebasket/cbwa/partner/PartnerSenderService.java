package in.codebasket.cbwa.partner;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.partner.dto.PartnerDtos.PartnerRef;
import in.codebasket.cbwa.partner.dto.PartnerDtos.SenderDto;
import in.codebasket.cbwa.partner.dto.PartnerDtos.WabaRef;
import in.codebasket.cbwa.sender.AssignmentType;
import in.codebasket.cbwa.sender.PartnerPhoneNumber;
import in.codebasket.cbwa.sender.PartnerPhoneNumberRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P11-P13: platform senders assigned to a partner (one active partner per number, audited). */
@Service
@RequiredArgsConstructor
public class PartnerSenderService {

    private final PartnerService partnerService;
    private final PartnerSettingsRepository settingsRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<SenderDto> list(Long partnerId) {
        Partner partner = partnerService.require(partnerId);
        List<PartnerPhoneNumber> assignments = assignmentRepository.findByPartnerIdAndActiveTrue(partnerId);
        Map<Long, Long> sentToday = sentToday(assignments.stream().map(PartnerPhoneNumber::getPhoneNumberId).toList());
        List<SenderDto> out = new ArrayList<>();
        for (PartnerPhoneNumber a : assignments) {
            phoneNumberRepository.findById(a.getPhoneNumberId()).ifPresent(pn ->
                    out.add(toDto(pn, wabaRepository.findById(pn.getWhatsappAccountId()).orElse(null), partner, a.isDefaultSender(),
                            sentToday.getOrDefault(pn.getId(), 0L))));
        }
        out.sort(Comparator.comparing(SenderDto::isDefault).reversed().thenComparing(SenderDto::id));
        return out;
    }

    @Transactional
    public SenderDto assign(Long partnerId, Long phoneNumberDbId, boolean isDefault) {
        Partner partner = partnerService.require(partnerId);
        PhoneNumber pn = phoneNumberRepository.findById(phoneNumberDbId)
                .orElseThrow(() -> AppException.field("phoneNumberDbId", "Sender number not found"));
        if (pn.getSenderType() != SenderType.PLATFORM_SENDER) {
            throw AppException.field("phoneNumberDbId", "Only platform numbers can be assigned. Partner-own numbers are connected by the partner");
        }
        if (pn.getStatus() == SenderStatus.DISCONNECTED) {
            throw AppException.field("phoneNumberDbId", "This number is disconnected");
        }
        assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).ifPresent(a -> {
            throw AppException.conflict(a.getPartnerId().equals(partnerId)
                    ? "This number is already assigned to this partner"
                    : "This number is assigned to another partner. Unassign it first");
        });
        List<PartnerPhoneNumber> existing = assignmentRepository.findByPartnerIdAndActiveTrue(partnerId);
        boolean makeDefault = isDefault || existing.isEmpty();
        PartnerPhoneNumber a = new PartnerPhoneNumber();
        a.setPartnerId(partnerId);
        a.setPhoneNumberId(pn.getId());
        a.setAssignmentType(AssignmentType.ASSIGNED);
        a.setActive(true);
        a.setDefaultSender(makeDefault);
        a.setAssignedAt(Instant.now());
        a.setAssignedBy(in.codebasket.cbwa.auth.SecurityUtils.currentUserIdOrNull());
        assignmentRepository.saveAndFlush(a);
        if (makeDefault) {
            setDefault(partnerId, existing, pn.getId());
        }
        auditService.log("SENDER_ASSIGN", "PHONE_NUMBER", pn.getId(), null,
                Map.of("partnerId", partnerId, "phoneNumberId", pn.getPhoneNumberId(), "isDefault", makeDefault), partnerId);
        notifications.notifyPartner(partnerId, "SENDER_ASSIGNED", NotificationService.SUCCESS, "New sender number added",
                "WhatsApp number " + (pn.getDisplayPhoneNumber() == null ? "" : pn.getDisplayPhoneNumber())
                        + " is now available for sending.", "/partner/senders/" + pn.getId(), null);
        return toDto(pn, wabaRepository.findById(pn.getWhatsappAccountId()).orElse(null), partner, makeDefault,
                sentToday(List.of(pn.getId())).getOrDefault(pn.getId(), 0L));
    }

    @Transactional
    public void unassign(Long partnerId, Long phoneNumberDbId) {
        partnerService.require(partnerId);
        PartnerPhoneNumber a = assignmentRepository.findByPartnerIdAndPhoneNumberIdAndActiveTrue(partnerId, phoneNumberDbId)
                .orElseThrow(() -> AppException.notFound("Sender assignment"));
        if (a.getAssignmentType() == AssignmentType.OWN) {
            throw AppException.conflict("This is the partner's own number. Use Disconnect instead");
        }
        a.setActive(false);
        a.setDefaultSender(false);
        a.setUnassignedAt(Instant.now());
        assignmentRepository.saveAndFlush(a);
        List<PartnerPhoneNumber> rest = assignmentRepository.findByPartnerIdAndActiveTrue(partnerId);
        PartnerSettings ps = settingsRepository.findById(partnerId).orElse(null);
        boolean hasDefault = rest.stream().anyMatch(PartnerPhoneNumber::isDefaultSender);
        if (!hasDefault && !rest.isEmpty()) {
            setDefault(partnerId, rest, rest.get(0).getPhoneNumberId());
        } else if (rest.isEmpty() && ps != null) {
            ps.setDefaultPhoneNumberId(null);
            settingsRepository.save(ps);
        }
        auditService.log("SENDER_UNASSIGN", "PHONE_NUMBER", phoneNumberDbId, Map.of("partnerId", partnerId), null, partnerId);
    }

    private void setDefault(Long partnerId, List<PartnerPhoneNumber> assignments, Long phoneNumberDbId) {
        for (PartnerPhoneNumber other : assignments) {
            boolean shouldBe = other.getPhoneNumberId().equals(phoneNumberDbId);
            if (other.isDefaultSender() != shouldBe) {
                other.setDefaultSender(shouldBe);
                assignmentRepository.save(other);
            }
        }
        settingsRepository.findById(partnerId).ifPresent(ps -> {
            ps.setDefaultPhoneNumberId(phoneNumberDbId);
            settingsRepository.save(ps);
        });
    }

    private Map<Long, Long> sentToday(List<Long> senderIds) {
        Map<Long, Long> out = new HashMap<>();
        if (senderIds.isEmpty()) {
            return out;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", senderIds)
                .addValue("day", Timestamp.from(TimeUtil.startOfDayIst(TimeUtil.todayIst())));
        jdbc.query("""
                select phone_number_id, count(*) c from messages
                where phone_number_id in (:ids) and direction = 'OUTBOUND' and created_at >= :day group by phone_number_id
                """, params, rs -> {
            out.put(rs.getLong(1), rs.getLong(2));
        });
        return out;
    }

    static SenderDto toDto(PhoneNumber pn, WhatsappAccount waba, Partner partner, boolean isDefault, long sentToday) {
        return new SenderDto(pn.getId(), pn.getPhoneNumberId(), pn.getDisplayPhoneNumber(), pn.getVerifiedName(), pn.getNameStatus(),
                pn.getNewNameStatus(), pn.getQualityRating(), pn.getMessagingLimitTier(), pn.getThroughputLevel(), pn.getMaxMps(),
                pn.getMetaStatus(), health(pn.getHealthStatus()), pn.getSenderType().name(), pn.isCoexistence(), pn.isRegistered(),
                pn.isPaused(), pn.getPauseReason(), pn.getDailySendCap(), sentToday, pn.getStatus().name(),
                waba == null ? null : new WabaRef(waba.getId(), waba.getWabaId(), waba.getName(), waba.getTokenStatus().name()),
                partner == null ? null : new PartnerRef(partner.getId(), partner.getBusinessName()), isDefault,
                pn.getConnectedAt(), pn.getLastSyncedAt());
    }

    /** Flattens Meta's health_status into {canSendMessage, errors[]}. */
    static Map<String, Object> health(String json) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        if (json == null || json.isBlank()) {
            out.put("canSendMessage", "UNKNOWN");
            out.put("errors", errors);
            return out;
        }
        JsonNode node = JsonUtil.readTree(json);
        out.put("canSendMessage", node.path("can_send_message").asText("UNKNOWN"));
        for (JsonNode entity : node.path("entities")) {
            for (JsonNode e : entity.path("errors")) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("entityType", entity.path("entity_type").asText(null));
                err.put("errorCode", e.path("error_code").isMissingNode() ? null : e.path("error_code").asInt());
                err.put("description", e.path("error_description").asText(null));
                err.put("solution", e.path("possible_solution").asText(null));
                errors.add(err);
            }
        }
        out.put("errors", errors);
        return out;
    }
}
