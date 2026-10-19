package in.codebasket.cbwa.user;

import in.codebasket.cbwa.auth.AuthPrincipal;
import in.codebasket.cbwa.auth.SecurityUtils;
import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.audit.AuditService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.partner.PartnerRepository;
import in.codebasket.cbwa.user.dto.UserDto;
import in.codebasket.cbwa.user.dto.UserRequests.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MeService {

    private final UserRepository userRepository;
    private final PartnerRepository partnerRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserDto me() {
        AuthPrincipal p = SecurityUtils.current();
        User user = currentUser(p);
        Partner partner = user.getUserType() == UserType.PARTNER ? partnerRepository.findByUserId(user.getId()).orElse(null) : null;
        User imp = p.impersonatorUserId() == null ? null : userRepository.findById(p.impersonatorUserId()).orElse(null);
        return userMapper.toDto(user, partner, imp);
    }

    @Transactional
    public UserDto update(UpdateProfileRequest req) {
        User user = currentUser(SecurityUtils.current());
        user.setName(req.name().trim());
        user.setMobile(req.mobile() == null || req.mobile().isBlank() ? null : req.mobile().trim());
        if (req.theme() != null) {
            user.setTheme(req.theme());
        }
        userRepository.save(user);
        return me();
    }

    @Transactional
    public UserDto toggleTwoFactor(boolean enabled, String password) {
        AuthPrincipal p = SecurityUtils.current();
        if (!p.isAdmin()) {
            throw AppException.forbidden();
        }
        User user = currentUser(p);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Password is incorrect");
        }
        user.setTwoFactorEnabled(enabled);
        userRepository.save(user);
        auditService.log("TWO_FACTOR_" + (enabled ? "ENABLE" : "DISABLE"), "USER", user.getUid(), null, Map.of("enabled", enabled));
        return me();
    }

    private User currentUser(AuthPrincipal p) {
        if (p.userId() == null) {
            throw AppException.forbidden();
        }
        return userRepository.findById(p.userId()).orElseThrow(() -> AppException.notFound("User"));
    }
}
