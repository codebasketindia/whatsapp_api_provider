package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.domain.SenderType;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.meta.MetaApiException;
import in.codebasket.cbwa.meta.MetaWabaApi;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDetailDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.SenderDto;
import in.codebasket.cbwa.sender.dto.SenderDtos.UpdateSenderRequest;
import in.codebasket.cbwa.waba.WabaStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sender listing/detail for admin and partner, admin updates, and disconnect (M13-M15, E1-E3, E8, E9). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SenderService {

    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final PartnerRepository partnerRepository;
    private final SenderMapper mapper;
    private final ChecklistService checklistService;
    private final SenderAccessService access;
    private final SenderAssignmentService assignmentService;
    private final MetaWabaApi wabaApi;
    private final CryptoService crypto;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public PageResponse<SenderDto> adminList(String senderType, Long partnerId, String quality, String status, Boolean assigned,
                                             String search, Integer page, Integer size) {
        Specification<PhoneNumber> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (senderType != null && !senderType.isBlank()) {
                ps.add(cb.equal(root.get("senderType"), SenderType.valueOf(senderType)));
            }
            if (quality != null && !quality.isBlank()) {
                ps.add(cb.equal(root.get("qualityRating"), quality));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), SenderStatus.valueOf(status)));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("displayPhoneNumber")), like),
                        cb.like(cb.lower(root.get("verifiedName")), like), cb.like(root.get("waNumber"), like),
                        cb.like(root.get("phoneNumberId"), like)));
            }
            if (partnerId != null || assigned != null) {
                Subquery<Long> sq = q.subquery(Long.class);
                var a = sq.from(PartnerPhoneNumber.class);
                List<Predicate> sp = new ArrayList<>();
                sp.add(cb.isTrue(a.get("active")));
                if (partnerId != null) {
                    sp.add(cb.equal(a.get("partnerId"), partnerId));
                }
                sq.select(a.get("phoneNumberId")).where(sp.toArray(Predicate[]::new));
                if (partnerId != null || Boolean.TRUE.equals(assigned)) {
                    ps.add(root.get("id").in(sq));
                } else {
                    ps.add(cb.not(root.get("id").in(sq)));
                }
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return tx.execute(st -> PageResponse.of(phoneNumberRepository.findAll(spec,
                Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"))), mapper::toDto));
    }

    public SenderDetailDto adminDetail(Long id) {
        return tx.execute(st -> detail(get(id)));
    }

    /** M15 */
    public SenderDto adminUpdate(Long id, UpdateSenderRequest req) {
        return tx.execute(st -> {
            PhoneNumber pn = get(id);
            Map<String, Object> old = snapshot(pn);
            if (Boolean.TRUE.equals(req.clearDailyCap())) {
                pn.setDailySendCap(null);
            } else if (req.dailySendCap() != null) {
                pn.setDailySendCap(req.dailySendCap());
            }
            if (req.maxMps() != null) {
                pn.setMaxMps(req.maxMps());
            }
            if (req.isPaused() != null) {
                pn.setPaused(req.isPaused());
                pn.setPauseReason(req.isPaused() ? (req.pauseReason() == null || req.pauseReason().isBlank()
                        ? "Paused by admin" : req.pauseReason().trim()) : null);
            }
            phoneNumberRepository.save(pn);
            auditService.log("SENDER_UPDATE", "SENDER", pn.getId(), old, snapshot(pn),
                    assignmentRepository.findByPhoneNumberIdAndActiveTrue(pn.getId()).map(PartnerPhoneNumber::getPartnerId).orElse(null));
            return mapper.toDto(pn);
        });
    }

    /** E1 */
    public List<SenderDto> partnerList(Long partnerId) {
        return tx.execute(st -> assignmentRepository.findByPartnerIdAndActiveTrue(partnerId).stream()
                .map(a -> phoneNumberRepository.findById(a.getPhoneNumberId()).orElse(null))
                .filter(java.util.Objects::nonNull).map(mapper::toDto).toList());
    }

    /** E2 */
    public SenderDetailDto partnerDetail(Long partnerId, Long id) {
        return tx.execute(st -> detail(access.requirePartnerSender(partnerId, id)));
    }

    /** E3 */
    public SenderDto makeDefault(Long partnerId, Long id) {
        return tx.execute(st -> {
            PhoneNumber pn = access.requirePartnerSender(partnerId, id);
            assignmentService.makeDefault(partnerId, id);
            return mapper.toDto(pn);
        });
    }

    /** E8: partner disconnects their own number. */
    public SenderDto partnerDisconnect(Long partnerId, Long id) {
        PhoneNumber pn = tx.execute(st -> access.requirePartnerSender(partnerId, id));
        if (pn.getSenderType() != SenderType.PARTNER_OWN_SENDER) {
            throw new AppException(in.codebasket.cbwa.common.api.ErrorCode.FORBIDDEN, "Platform numbers can only be removed by the admin");
        }
        return disconnect(pn, "Disconnected by partner");
    }

    /** E9: admin disconnect (own number: full disconnect; platform number: also unassigned). */
    public SenderDto adminDisconnect(Long id, String reason) {
        return disconnect(tx.execute(st -> get(id)), reason == null || reason.isBlank() ? "Disconnected by admin" : reason);
    }

    /**
     * Unsubscribes the app (only when no other active number uses the WABA) and deregisters the
     * number at Meta (best effort), then marks it DISCONNECTED and ends the assignment. History is kept.
     */
    private SenderDto disconnect(PhoneNumber pn, String reason) {
        if (pn.getStatus() == SenderStatus.DISCONNECTED) {
            throw AppException.conflict("This number is already disconnected");
        }
        WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
        String token = crypto.decrypt(waba.getAccessTokenEnc());
        boolean ownNumber = pn.getSenderType() == SenderType.PARTNER_OWN_SENDER;
        List<String> warnings = new ArrayList<>();
        if (ownNumber && !pn.isCoexistence()) {
            try {
                wabaApi.deregister(pn.getPhoneNumberId(), token);
            } catch (MetaApiException e) {
                warnings.add("deregister: " + e.friendlyMessage());
            }
        }
        boolean otherActive = phoneNumberRepository.findByWhatsappAccountId(waba.getId()).stream()
                .anyMatch(o -> !o.getId().equals(pn.getId()) && o.getStatus() != SenderStatus.DISCONNECTED);
        if (ownNumber && !otherActive) {
            try {
                wabaApi.unsubscribeApp(waba.getWabaId(), token);
            } catch (MetaApiException e) {
                warnings.add("unsubscribe: " + e.friendlyMessage());
            }
        }
        if (!warnings.isEmpty()) {
            log.warn("Disconnect of {} finished with warnings: {}", pn.getPhoneNumberId(), warnings);
        }
        return tx.execute(st -> {
            PhoneNumber fresh = get(pn.getId());
            Long partnerId = null;
            var assignment = assignmentRepository.findByPhoneNumberIdAndActiveTrue(fresh.getId());
            if (assignment.isPresent()) {
                partnerId = assignment.get().getPartnerId();
                assignmentService.deactivate(assignment.get());
            }
            if (ownNumber) {
                fresh.setStatus(SenderStatus.DISCONNECTED);
                fresh.setRegistered(false);
                if (!otherActive) {
                    WhatsappAccount w = wabaRepository.findById(fresh.getWhatsappAccountId()).orElseThrow();
                    w.setStatus(WabaStatus.DISCONNECTED);
                    w.setWebhookSubscribed(false);
                    w.setDisconnectedAt(Instant.now());
                    wabaRepository.save(w);
                }
            }
            phoneNumberRepository.save(fresh);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reason", reason);
            details.put("warnings", warnings);
            auditService.log("SENDER_DISCONNECT", "SENDER", fresh.getId(), null, details, partnerId);
            notifications.notifyAdmins("SENDER_DISCONNECTED", NotificationService.WARNING, "Number disconnected",
                    fresh.getDisplayPhoneNumber() + " was disconnected: " + reason, "/admin/whatsapp/senders/" + fresh.getId());
            if (partnerId != null) {
                String email = partnerRepository.findById(partnerId).map(p -> p.getEmail()).orElse(null);
                notifications.notifyPartner(partnerId, "SENDER_DISCONNECTED", NotificationService.WARNING, "Number disconnected",
                        fresh.getDisplayPhoneNumber() + " is no longer connected. Message history is still available.",
                        "/partner/senders", email);
            }
            return mapper.toDto(fresh);
        });
    }

    private SenderDetailDto detail(PhoneNumber pn) {
        WhatsappAccount waba = wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow();
        return new SenderDetailDto(mapper.toDto(pn), checklistService.build(pn, waba));
    }

    private PhoneNumber get(Long id) {
        return phoneNumberRepository.findById(id).orElseThrow(() -> AppException.notFound("Sender"));
    }

    private static Map<String, Object> snapshot(PhoneNumber pn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dailySendCap", pn.getDailySendCap());
        m.put("maxMps", pn.getMaxMps());
        m.put("isPaused", pn.isPaused());
        m.put("pauseReason", pn.getPauseReason());
        return m;
    }
}
