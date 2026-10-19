package in.codebasket.cbwa.user;

import in.codebasket.cbwa.partner.Partner;
import in.codebasket.cbwa.user.dto.UserDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserDto toDto(User u, Partner partner, User impersonator) {
        List<String> perms = permissionsOf(u);
        return new UserDto(u.getUid(), u.getName(), u.getEmail(), u.getMobile(), u.getUserType().name(),
                new UserDto.RoleRef(u.getRole().getId(), u.getRole().getCode(), u.getRole().getName()), perms,
                u.isSuperAdmin(), u.isTwoFactorEnabled(), u.isMustChangePassword(), u.getTheme(), u.getStatus().name(),
                u.getLastLoginAt(), u.getCreatedAt(),
                partner == null ? null : new UserDto.PartnerRef(partner.getId(), partner.getUid(), partner.getPartnerCode(),
                        partner.getBusinessName(), partner.getStatus().name()),
                impersonator == null ? null : new UserDto.ImpersonatorRef(impersonator.getUid(), impersonator.getName()));
    }

    /** Super admin implicitly holds every permission. */
    public static List<String> permissionsOf(User u) {
        if (u.getRole() == null || u.getUserType() != UserType.ADMIN) {
            return List.of();
        }
        return u.getRole().getPermissions().stream().map(Permission::getCode).sorted().toList();
    }
}
