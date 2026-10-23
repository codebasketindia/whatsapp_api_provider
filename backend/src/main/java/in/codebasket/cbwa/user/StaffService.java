package in.codebasket.cbwa.user;

import in.codebasket.cbwa.auth.PasswordPolicy;
import in.codebasket.cbwa.auth.RefreshTokenRepository;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.PageResponse;
import in.codebasket.cbwa.common.api.Paging;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.mail.EmailTemplates;
import in.codebasket.cbwa.common.mail.MailService;
import in.codebasket.cbwa.common.notification.NotificationService;
import in.codebasket.cbwa.common.util.Ulids;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.user.dto.UserDto;
import in.codebasket.cbwa.user.dto.UserRequests.CreateStaffRequest;
import in.codebasket.cbwa.user.dto.UserRequests.PermissionDto;
import in.codebasket.cbwa.user.dto.UserRequests.RoleDto;
import in.codebasket.cbwa.user.dto.UserRequests.RoleRequest;
import in.codebasket.cbwa.user.dto.UserRequests.UpdateStaffRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Staff users (admin sub-users) and custom staff roles with permissions (S1-S10). */
@Service
@RequiredArgsConstructor
public class StaffService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final MailService mailService;
    private final AppProperties props;
    private final MobileNumbers mobileNumbers;

    @Transactional(readOnly = true)
    public List<PermissionDto> permissions() {
        return permissionRepository.findAll(Sort.by("module", "code")).stream()
                .map(p -> new PermissionDto(p.getCode(), p.getModule(), p.getDescription())).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roleRepository.findAllByOrderByIdAsc().stream()
                .filter(r -> r.getUserType() == UserType.ADMIN)
                .map(this::toRoleDto).toList();
    }

    @Transactional
    public RoleDto createRole(RoleRequest req) {
        if (roleRepository.findByCode(req.code()).isPresent()) {
            throw AppException.field("code", "A role with this code already exists");
        }
        Role role = new Role();
        role.setCode(req.code());
        role.setUserType(UserType.ADMIN);
        role.setSystem(false);
        apply(role, req);
        roleRepository.save(role);
        auditService.log("ROLE_CREATE", "ROLE", role.getId(), null, toRoleDto(role));
        return toRoleDto(role);
    }

    @Transactional
    public RoleDto updateRole(Long id, RoleRequest req) {
        Role role = roleRepository.findById(id).orElseThrow(() -> AppException.notFound("Role"));
        if (role.isSystem() || role.getUserType() != UserType.ADMIN) {
            throw new AppException(in.codebasket.cbwa.common.api.ErrorCode.FORBIDDEN, "System roles cannot be changed");
        }
        RoleDto old = toRoleDto(role);
        apply(role, req);
        roleRepository.save(role);
        auditService.log("ROLE_UPDATE", "ROLE", role.getId(), old, toRoleDto(role));
        return toRoleDto(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> AppException.notFound("Role"));
        if (role.isSystem()) {
            throw new AppException(in.codebasket.cbwa.common.api.ErrorCode.FORBIDDEN, "System roles cannot be deleted");
        }
        if (userRepository.countByRoleId(id) > 0) {
            throw AppException.conflict("This role is assigned to staff members. Move them to another role first");
        }
        roleRepository.delete(role);
        auditService.log("ROLE_DELETE", "ROLE", id, toRoleDto(role), null);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> staff(String search, String status, Integer page, Integer size) {
        Specification<User> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("userType"), UserType.ADMIN));
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), UserStatus.valueOf(status)));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("email")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var pageable = Paging.of(page, size, null, Set.of("name", "email", "createdAt"), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(userRepository.findAll(spec, pageable), u -> userMapper.toDto(u, null, null));
    }

    @Transactional
    public UserDto createStaff(CreateStaffRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw AppException.field("email", "This email is already registered");
        }
        Role role = adminRole(req.roleId());
        String password = req.password();
        boolean temporary = password == null || password.isBlank();
        if (temporary) {
            password = PasswordPolicy.temporary();
        } else {
            PasswordPolicy.check(password);
        }
        User u = new User();
        u.setUid(Ulids.next());
        u.setUserType(UserType.ADMIN);
        u.setRole(role);
        u.setName(req.name().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setMobile(mobileNumbers.requireUnique(req.mobile(), null, "mobile"));
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setStatus(UserStatus.ACTIVE);
        u.setMustChangePassword(true);
        userRepository.save(u);
        auditService.log("STAFF_CREATE", "USER", u.getUid(), null, Map.of("email", u.getEmail(), "role", role.getCode()));
        sendCredentials(u, password);
        return userMapper.toDto(u, null, null);
    }

    @Transactional
    public UserDto updateStaff(String uid, UpdateStaffRequest req) {
        User u = staffUser(uid);
        if (u.isSuperAdmin() && !SecurityUtils.current().superAdmin()) {
            throw AppException.forbidden();
        }
        Map<String, Object> old = Map.of("name", u.getName(), "role", u.getRole().getCode());
        Role role = adminRole(req.roleId());
        if (u.isSuperAdmin() && !role.getCode().equals("SUPER_ADMIN") && userRepository.findByUserTypeAndStatus(UserType.ADMIN, UserStatus.ACTIVE)
                .stream().filter(User::isSuperAdmin).count() <= 1) {
            throw AppException.conflict("At least one active super admin is required");
        }
        u.setName(req.name().trim());
        u.setMobile(mobileNumbers.requireUnique(req.mobile(), u.getId(), "mobile"));
        u.setRole(role);
        userRepository.save(u);
        refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        auditService.log("STAFF_UPDATE", "USER", u.getUid(), old, Map.of("name", u.getName(), "role", role.getCode()));
        return userMapper.toDto(u, null, null);
    }

    @Transactional
    public UserDto setStatus(String uid, String status) {
        User u = staffUser(uid);
        if (u.getId().equals(SecurityUtils.current().userId())) {
            throw new AppException(in.codebasket.cbwa.common.api.ErrorCode.FORBIDDEN, "You cannot change your own status");
        }
        if (u.isSuperAdmin()) {
            throw new AppException(in.codebasket.cbwa.common.api.ErrorCode.FORBIDDEN, "Super admin cannot be disabled");
        }
        UserStatus old = u.getStatus();
        u.setStatus(UserStatus.valueOf(status));
        userRepository.save(u);
        if (u.getStatus() == UserStatus.DISABLED) {
            refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        }
        auditService.log("STAFF_STATUS", "USER", u.getUid(), Map.of("status", old), Map.of("status", u.getStatus()));
        return userMapper.toDto(u, null, null);
    }

    @Transactional
    public void resetPassword(String uid) {
        User u = staffUser(uid);
        if (u.isSuperAdmin() && !SecurityUtils.current().superAdmin()) {
            throw AppException.forbidden();
        }
        String password = PasswordPolicy.temporary();
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setMustChangePassword(true);
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        userRepository.save(u);
        refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        auditService.log("STAFF_PASSWORD_RESET", "USER", u.getUid(), null, null);
        sendCredentials(u, password);
    }

    /** Emails login details after commit. The temporary password must be changed at first login. */
    public void sendCredentials(User u, String password) {
        String html = EmailTemplates.simple("Your login details",
                "<p>Hello " + EmailTemplates.esc(u.getName()) + ",</p><p>Your account is ready.</p>"
                        + "<p>Email: <b>" + EmailTemplates.esc(u.getEmail()) + "</b><br>Temporary password: <b>"
                        + EmailTemplates.esc(password) + "</b></p><p>You will be asked to choose a new password after logging in.</p>",
                "Log in", props.getFrontendUrl() + "/login");
        NotificationService.afterCommit(() -> mailService.sendAsync(u.getEmail(), "Your CB WhatsApp Gateway login", html));
    }

    private void apply(Role role, RoleRequest req) {
        role.setName(req.name().trim());
        role.setDescription(req.description());
        Set<Permission> perms = new HashSet<>();
        for (String code : req.permissions()) {
            perms.add(permissionRepository.findByCode(code).orElseThrow(() -> AppException.field("permissions", "Unknown permission " + code)));
        }
        role.setPermissions(perms);
    }

    private Role adminRole(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> AppException.field("roleId", "Role not found"));
        if (role.getUserType() != UserType.ADMIN) {
            throw AppException.field("roleId", "Choose a staff role");
        }
        if ("SUPER_ADMIN".equals(role.getCode()) && !SecurityUtils.current().superAdmin()) {
            throw AppException.forbidden();
        }
        return role;
    }

    private User staffUser(String uid) {
        User u = userRepository.findByUid(uid).orElseThrow(() -> AppException.notFound("Staff member"));
        if (u.getUserType() != UserType.ADMIN) {
            throw AppException.notFound("Staff member");
        }
        return u;
    }

    private RoleDto toRoleDto(Role r) {
        return new RoleDto(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isSystem(), r.getUserType().name(),
                r.getPermissions().stream().map(Permission::getCode).sorted().toList(),
                r.getId() == null ? 0 : userRepository.countByRoleId(r.getId()));
    }
}
