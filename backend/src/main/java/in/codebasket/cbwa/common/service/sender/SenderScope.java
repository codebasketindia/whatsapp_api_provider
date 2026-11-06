package in.codebasket.cbwa.common.service.sender;

import in.codebasket.cbwa.common.entity.PhoneNumber;
import in.codebasket.cbwa.common.repository.PhoneNumberRepository;

import in.codebasket.cbwa.security.AuthPrincipal;
import in.codebasket.cbwa.security.SecurityUtils;
import in.codebasket.cbwa.exception.ErrorCode;
import in.codebasket.cbwa.common.constants.SenderType;
import in.codebasket.cbwa.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the sender for the shared sender-tool endpoints (T1-T21) under /api/admin/senders/{id}
 * and /api/partner/senders/{id}. Admins need WHATSAPP_VIEW (read) / WHATSAPP_MANAGE (write);
 * partners only reach their own/assigned numbers and cannot touch registration of platform numbers.
 */
@Component
@RequiredArgsConstructor
public class SenderScope {

    public enum Access { READ, WRITE, OWNER }

    private final PhoneNumberRepository phoneNumberRepository;
    private final SenderAccessService access;

    public PhoneNumber resolve(Long senderId, Access level) {
        AuthPrincipal p = SecurityUtils.current();
        if (p.isAdmin()) {
            String perm = level == Access.READ ? "WHATSAPP_VIEW" : "WHATSAPP_MANAGE";
            if (!p.hasPermission(perm)) {
                throw AppException.forbidden();
            }
            return phoneNumberRepository.findById(senderId).orElseThrow(() -> AppException.notFound("Sender"));
        }
        PhoneNumber pn = access.requirePartnerSender(SecurityUtils.currentPartnerId(), senderId);
        if (level == Access.OWNER && pn.getSenderType() == SenderType.PLATFORM_SENDER) {
            throw new AppException(ErrorCode.FORBIDDEN, "This number is managed by the platform. Please contact support");
        }
        return pn;
    }

    public Long partnerIdOrNull() {
        AuthPrincipal p = SecurityUtils.current();
        return p.isAdmin() ? null : p.partnerId();
    }
}
