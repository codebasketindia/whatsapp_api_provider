package in.codebasket.cbwa.sender;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.crypto.CryptoService;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.waba.TokenStatus;
import in.codebasket.cbwa.waba.WhatsappAccount;
import in.codebasket.cbwa.waba.WhatsappAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Shared checks for "can this partner use this sender" and access to the sender's Meta token. */
@Service
@RequiredArgsConstructor
public class SenderAccessService {

    private final PhoneNumberRepository phoneNumberRepository;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final WhatsappAccountRepository wabaRepository;
    private final CryptoService crypto;

    /** Sender assigned to / owned by the partner, in any state (for detail pages and tools). */
    @Transactional(readOnly = true)
    public PhoneNumber requirePartnerSender(Long partnerId, Long senderId) {
        assignmentRepository.findByPartnerIdAndPhoneNumberIdAndActiveTrue(partnerId, senderId)
                .orElseThrow(() -> AppException.notFound("Sender"));
        return phoneNumberRepository.findById(senderId).orElseThrow(() -> AppException.notFound("Sender"));
    }

    /** Sender that the partner may send from right now. */
    @Transactional(readOnly = true)
    public PhoneNumber requireUsableSender(Long partnerId, Long senderId) {
        PhoneNumber pn = assignmentRepository.findByPartnerIdAndPhoneNumberIdAndActiveTrue(partnerId, senderId)
                .flatMap(a -> phoneNumberRepository.findById(senderId))
                .orElseThrow(() -> new AppException(ErrorCode.SENDER_NOT_AVAILABLE, "This sender number is not assigned to you"));
        if (pn.getStatus() != SenderStatus.CONNECTED) {
            throw new AppException(ErrorCode.SENDER_NOT_AVAILABLE, "This sender number is not connected");
        }
        if (pn.isPaused()) {
            throw new AppException(ErrorCode.SENDER_PAUSED, "This sender is paused" + (pn.getPauseReason() == null ? "" : ": " + pn.getPauseReason()));
        }
        WhatsappAccount waba = waba(pn);
        if (waba.getTokenStatus() == TokenStatus.INVALID || waba.getTokenStatus() == TokenStatus.EXPIRED) {
            throw new AppException(ErrorCode.META_TOKEN_INVALID);
        }
        return pn;
    }

    /** Default sender of a partner, or its only usable sender. */
    @Transactional(readOnly = true)
    public Optional<PhoneNumber> defaultSender(Long partnerId) {
        var list = assignmentRepository.findByPartnerIdAndActiveTrue(partnerId);
        return list.stream().filter(PartnerPhoneNumber::isDefaultSender).findFirst()
                .or(() -> list.size() == 1 ? Optional.of(list.get(0)) : Optional.empty())
                .flatMap(a -> phoneNumberRepository.findById(a.getPhoneNumberId()));
    }

    @Transactional(readOnly = true)
    public Optional<Long> partnerOf(Long senderId) {
        return assignmentRepository.findByPhoneNumberIdAndActiveTrue(senderId).map(PartnerPhoneNumber::getPartnerId);
    }

    @Transactional(readOnly = true)
    public WhatsappAccount waba(PhoneNumber pn) {
        return wabaRepository.findById(pn.getWhatsappAccountId()).orElseThrow(() -> AppException.notFound("WhatsApp account"));
    }

    /** Decrypted Meta access token of the WABA that owns this number. Never log or return it. */
    @Transactional(readOnly = true)
    public String accessToken(PhoneNumber pn) {
        return crypto.decrypt(waba(pn).getAccessTokenEnc());
    }

    public String accessToken(WhatsappAccount waba) {
        return crypto.decrypt(waba.getAccessTokenEnc());
    }
}
