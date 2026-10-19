package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.partner.PartnerStatus;
import in.codebasket.cbwa.partner.PartnerSettingsRepository;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Assignment of senders to partners. A number belongs to at most one partner at a time
 * (DB unique key on the active assignment) so inbound messages always route to one partner.
 */
@Service
@RequiredArgsConstructor
public class SenderAssignmentService {

    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerSettingsRepository partnerSettingsRepository;
    private final SenderMapper mapper;
    private final AuditService auditService;
    private final NotificationService notifications;

    /** M16: assign a platform number to a partner. */
    @Transactional
    public SenderDto assign(Long senderId, Long partnerId, boolean isDefault) {
        PhoneNumber pn = phoneNumberRepository.findById(senderId).orElseThrow(() -> AppException.notFound("Sender"));
        Partner partner = partnerRepository.findById(partnerId).orElseThrow(() -> AppException.field("partnerId", "Partner not found"));
        if (partner.getStatus() != PartnerStatus.ACTIVE) {
            throw AppException.field("partnerId", "Partner is suspended");
        }
        if (pn.getSenderType() != SenderType.PLATFORM_SENDER) {
            throw AppException.conflict("Only platform numbers can be assigned. Partner-owned numbers stay with their owner");
        }
        if (pn.getStatus() == SenderStatus.DISCONNECTED) {
            throw AppException.conflict("This number is disconnected");
        }
        var existing = assignmentRepository.findByPhoneNumberIdAndActiveTrue(senderId);
        if (existing.isPresent()) {
            if (existing.get().getPartnerId().equals(partnerId)) {
                if (isDefault) {
                    makeDefault(partnerId, senderId);
                }
                return mapper.toDto(pn);
            }
            throw AppException.conflict("This number is already assigned to another partner. Unassign it first");
        }
        PartnerPhoneNumber a = new PartnerPhoneNumber();
        a.setPartnerId(partnerId);
        a.setPhoneNumberId(senderId);
        a.setAssignmentType(AssignmentType.ASSIGNED);
        a.setActive(true);
        a.setDefaultSender(false);
        a.setAssignedAt(Instant.now());
        a.setAssignedBy(in.codebasket.cbwa.auth.SecurityUtils.currentUserIdOrNull());
        assignmentRepository.saveAndFlush(a);
        boolean first = assignmentRepository.findByPartnerIdAndActiveTrue(partnerId).size() == 1;
        if (isDefault || first) {
            makeDefault(partnerId, senderId);
        }
        auditService.log("SENDER_ASSIGN", "SENDER", pn.getId(), null,
                Map.of("partnerId", partnerId, "phoneNumberId", pn.getPhoneNumberId()), partnerId);
        notifications.notifyPartner(partnerId, "SENDER_ASSIGNED", NotificationService.SUCCESS, "New sender number added",
                "WhatsApp number " + pn.getDisplayPhoneNumber() + " is now available for sending.", "/partner/senders/" + pn.getId(),
                partner.getEmail());
        return mapper.toDto(pn);
    }

    /** M17: remove the active assignment (history kept). */
    @Transactional
    public SenderDto unassign(Long senderId) {
        PhoneNumber pn = phoneNumberRepository.findById(senderId).orElseThrow(() -> AppException.notFound("Sender"));
        PartnerPhoneNumber a = assignmentRepository.findByPhoneNumberIdAndActiveTrue(senderId)
                .orElseThrow(() -> AppException.conflict("This number is not assigned"));
        if (a.getAssignmentType() == AssignmentType.OWN) {
            throw AppException.conflict("This is the partner's own number. Disconnect it instead");
        }
        deactivate(a);
        auditService.log("SENDER_UNASSIGN", "SENDER", pn.getId(), Map.of("partnerId", a.getPartnerId()), null, a.getPartnerId());
        return mapper.toDto(pn);
    }

    @Transactional
    public void deactivate(PartnerPhoneNumber a) {
        a.setActive(false);
        a.setDefaultSender(false);
        a.setUnassignedAt(Instant.now());
        assignmentRepository.save(a);
        partnerSettingsRepository.findById(a.getPartnerId()).ifPresent(ps -> {
            if (a.getPhoneNumberId().equals(ps.getDefaultPhoneNumberId())) {
                ps.setDefaultPhoneNumberId(null);
                partnerSettingsRepository.save(ps);
            }
        });
    }

    /** Marks one sender as the partner's default (E3 / M16 isDefault). */
    @Transactional
    public void makeDefault(Long partnerId, Long senderId) {
        for (PartnerPhoneNumber a : assignmentRepository.findByPartnerIdAndActiveTrue(partnerId)) {
            boolean def = a.getPhoneNumberId().equals(senderId);
            if (a.isDefaultSender() != def) {
                a.setDefaultSender(def);
                assignmentRepository.save(a);
            }
        }
        partnerSettingsRepository.findById(partnerId).ifPresent(ps -> {
            ps.setDefaultPhoneNumberId(senderId);
            partnerSettingsRepository.save(ps);
        });
    }
}
