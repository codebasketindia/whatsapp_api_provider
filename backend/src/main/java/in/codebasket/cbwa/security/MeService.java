package in.codebasket.cbwa.security;

import in.codebasket.cbwa.common.constants.UserType;
import in.codebasket.cbwa.common.entity.User;
import in.codebasket.cbwa.common.repository.UserRepository;
import in.codebasket.cbwa.common.utils.MobileNumbers;

import in.codebasket.cbwa.security.AuthPrincipal;
import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.exception.ErrorCode;
import in.codebasket.cbwa.common.service.audit.AuditService;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.repository.PartnerRepository;
import in.codebasket.cbwa.staff.dto.UserDto;
import in.codebasket.cbwa.staff.dto.UserRequests.UpdateProfileRequest;
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
    private final MobileNumbers mobileNumbers;

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
        user.setMobile(mobileNumbers.requireUnique(req.mobile(), user.getId(), "mobile"));
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
