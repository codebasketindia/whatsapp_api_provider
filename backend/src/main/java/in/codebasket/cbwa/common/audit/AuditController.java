package in.codebasket.cbwa.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.common.api.ApiResponse;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.util.JsonUtil;
import in.codebasket.cbwa.common.util.TimeUtil;
import in.codebasket.cbwa.user.User;
import in.codebasket.cbwa.user.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "Admin - Audit log")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    public record ActorRef(String uid, String name, String email) {
    }

    public record AuditLogDto(Long id, String actorType, ActorRef actor, ActorRef impersonator, Long partnerId, String action,
                              String entityType, String entityId, JsonNode oldValue, JsonNode newValue, String ip,
                              String userAgent, Instant createdAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
    public ApiResponse<PageResponse<AuditLogDto>> list(@RequestParam(required = false) String actorUid,
                                                       @RequestParam(required = false) Long partnerId,
                                                       @RequestParam(required = false) String action,
                                                       @RequestParam(required = false) String entityType,
                                                       @RequestParam(required = false) String entityId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        Long actorId = actorUid == null || actorUid.isBlank() ? null
                : userRepository.findByUid(actorUid).map(User::getId).orElse(-1L);
        return ApiResponse.ok(search(actorId, partnerId, action, entityType, entityId, from, to, page, size));
    }

    public PageResponse<AuditLogDto> search(Long actorId, Long partnerId, String action, String entityType, String entityId,
                                            LocalDate from, LocalDate to, Integer page, Integer size) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (actorId != null) {
                ps.add(cb.equal(root.get("actorUserId"), actorId));
            }
            if (partnerId != null) {
                ps.add(cb.equal(root.get("partnerId"), partnerId));
            }
            if (action != null && !action.isBlank()) {
                ps.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                ps.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null && !entityId.isBlank()) {
                ps.add(cb.equal(root.get("entityId"), entityId));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeUtil.startOfDayIst(from)));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), TimeUtil.endOfDayIstExclusive(to)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<AuditLog> result = repository.findAll(spec, Paging.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id")));
        Set<Long> userIds = new HashSet<>();
        result.forEach(a -> {
            userIds.add(a.getActorUserId());
            userIds.add(a.getImpersonatorUserId());
        });
        userIds.remove(null);
        Map<Long, User> users = userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(result, a -> new AuditLogDto(a.getId(), a.getActorType(), ref(users.get(a.getActorUserId())),
                ref(users.get(a.getImpersonatorUserId())), a.getPartnerId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getOldValue() == null ? null : JsonUtil.readTree(a.getOldValue()),
                a.getNewValue() == null ? null : JsonUtil.readTree(a.getNewValue()), a.getIp(), a.getUserAgent(), a.getCreatedAt()));
    }

    private static ActorRef ref(User u) {
        return Objects.isNull(u) ? null : new ActorRef(u.getUid(), u.getName(), u.getEmail());
    }
}
