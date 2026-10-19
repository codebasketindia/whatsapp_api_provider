package in.codebasket.cbwa.publicapi;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.util.MoneyUtil;
import in.codebasket.cbwa.messaging.Message;
import in.codebasket.cbwa.messaging.MessageSource;
import in.codebasket.cbwa.messaging.OutboundCommand;
import in.codebasket.cbwa.messaging.OutboundMessageService;
import in.codebasket.cbwa.messaging.OutboundMessageServiceImpl;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.Accepted;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.Balance;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.BulkRecipient;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.BulkRequest;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.BulkResult;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.PublicSender;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.Rejected;
import in.codebasket.cbwa.publicapi.dto.PublicApiDtos.SendResult;
import in.codebasket.cbwa.sender.PartnerPhoneNumber;
import in.codebasket.cbwa.sender.PartnerPhoneNumberRepository;
import in.codebasket.cbwa.sender.PhoneNumber;
import in.codebasket.cbwa.sender.PhoneNumberRepository;
import in.codebasket.cbwa.sender.SenderAccessService;
import in.codebasket.cbwa.wallet.Wallet;
import in.codebasket.cbwa.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Partner Public API (Part G). Authentication, IP whitelist and rate limiting happen in
 * ApiKeyAuthFilter; this service resolves the sender ("from" phone, senderId or the partner's default
 * sender) and delegates to the messaging engine with source API and the calling key id.
 */
@Service
public class PublicApiService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final OutboundMessageService outbound;
    private final SenderAccessService senderAccess;
    private final PartnerPhoneNumberRepository assignmentRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final WalletService walletService;
    private final TransactionTemplate newTx;

    /** Idempotency-Key cache (single application instance): partnerId:key -> result. */
    private final Map<String, CachedResult> idempotency = new ConcurrentHashMap<>();

    private record CachedResult(SendResult result, Instant at) {
    }

    public PublicApiService(OutboundMessageService outbound, SenderAccessService senderAccess,
                            PartnerPhoneNumberRepository assignmentRepository, PhoneNumberRepository phoneNumberRepository,
                            WalletService walletService, PlatformTransactionManager txManager) {
        this.outbound = outbound;
        this.senderAccess = senderAccess;
        this.assignmentRepository = assignmentRepository;
        this.phoneNumberRepository = phoneNumberRepository;
        this.walletService = walletService;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** PU1 */
    public SendResult send(Long partnerId, Long apiKeyId, SendMessageRequest req, String idempotencyKey) {
        String cacheKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : partnerId + ":" + idempotencyKey.trim();
        if (cacheKey != null) {
            CachedResult cached = idempotency.get(cacheKey);
            if (cached != null && cached.at().isAfter(Instant.now().minus(IDEMPOTENCY_TTL))) {
                return cached.result();
            }
        }
        if (req.to() == null || req.to().isBlank()) {
            throw AppException.field("to", "Recipient number (to) is required");
        }
        Long senderId = resolveSender(partnerId, req.senderId(), req.from());
        SendResult result = newTx.execute(st -> {
            Message m = outbound.enqueue(command(partnerId, apiKeyId, senderId, req));
            return new SendResult(m.getUid(), m.getStatus().name(), m.getPrice(), m.getWaId(), m.getClientReference());
        });
        if (cacheKey != null) {
            idempotency.put(cacheKey, new CachedResult(result, Instant.now()));
        }
        return result;
    }

    /** PU2: each recipient is queued in its own transaction; failures are reported per recipient. */
    public BulkResult bulk(Long partnerId, Long apiKeyId, BulkRequest req) {
        Long senderId = resolveSender(partnerId, req.senderId(), req.from());
        List<Accepted> accepted = new ArrayList<>();
        List<Rejected> rejected = new ArrayList<>();
        BigDecimal total = MoneyUtil.ZERO;
        for (BulkRecipient r : req.recipients()) {
            SendMessageRequest one = templateRequest(senderId, r, req.template());
            try {
                Message m = newTx.execute(st -> outbound.enqueue(command(partnerId, apiKeyId, senderId, one)));
                Objects.requireNonNull(m);
                accepted.add(new Accepted(m.getWaId(), m.getUid(), m.getPrice(), r.clientReference()));
                total = total.add(m.getPrice());
            } catch (AppException e) {
                rejected.add(new Rejected(r.to(), e.getCode().name(), e.getMessage(), r.clientReference()));
            }
        }
        return new BulkResult(accepted, rejected, MoneyUtil.scale(total));
    }

    /** PU6 */
    public List<PublicSender> senders(Long partnerId) {
        List<PublicSender> out = new ArrayList<>();
        for (PartnerPhoneNumber a : assignmentRepository.findByPartnerIdAndActiveTrue(partnerId)) {
            phoneNumberRepository.findById(a.getPhoneNumberId()).ifPresent(pn -> out.add(new PublicSender(pn.getId(),
                    pn.getWaNumber(), pn.getDisplayPhoneNumber(), pn.getVerifiedName(), pn.getQualityRating(),
                    pn.getStatus().name(), pn.isPaused(), a.isDefaultSender())));
        }
        return out;
    }

    /** PU7 */
    public Balance balance(Long partnerId) {
        Wallet w = walletService.get(partnerId);
        return new Balance(w.getBalance(), w.getHeldAmount(), w.getCurrency());
    }

    /** senderId wins, then "from" (phone digits), then the partner's default / only sender. */
    public Long resolveSender(Long partnerId, Long senderId, String from) {
        if (senderId != null) {
            return senderId;
        }
        if (from != null && !from.isBlank()) {
            String digits = from.replaceAll("[^0-9]", "");
            for (PartnerPhoneNumber a : assignmentRepository.findByPartnerIdAndActiveTrue(partnerId)) {
                PhoneNumber pn = phoneNumberRepository.findById(a.getPhoneNumberId()).orElse(null);
                if (pn != null && (digits.equals(pn.getWaNumber())
                        || (pn.getDisplayPhoneNumber() != null && digits.equals(pn.getDisplayPhoneNumber().replaceAll("[^0-9]", ""))))) {
                    return pn.getId();
                }
            }
            throw new AppException(ErrorCode.SENDER_NOT_AVAILABLE, "Sender number " + from + " is not connected to your account");
        }
        return senderAccess.defaultSender(partnerId).map(PhoneNumber::getId)
                .orElseThrow(() -> AppException.field("from", "Specify the sender number (from) or senderId"));
    }

    private static OutboundCommand command(Long partnerId, Long apiKeyId, Long senderId, SendMessageRequest req) {
        return new OutboundCommand(partnerId, senderId, OutboundMessageServiceImpl.normaliseTo(req.to()), req,
                MessageSource.API, apiKeyId, null, null, null, 1);
    }

    private static SendMessageRequest templateRequest(Long senderId, BulkRecipient r, SendMessageRequest.TemplateRef base) {
        SendMessageRequest.TemplateVariables vars = base.variables();
        if (r.bodyVariables() != null && !r.bodyVariables().isEmpty()) {
            vars = new SendMessageRequest.TemplateVariables(r.bodyVariables(), vars == null ? null : vars.header(),
                    vars == null ? null : vars.buttons());
        }
        Map<String, String> named = r.variables() != null && !r.variables().isEmpty() ? r.variables() : base.namedVariables();
        SendMessageRequest.TemplateRef tpl = new SendMessageRequest.TemplateRef(base.templateId(), base.name(), base.language(), vars, named);
        return new SendMessageRequest(senderId, null, r.to(), "template", null, null, null, null, null, null, null, null, tpl,
                r.clientReference());
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void evictIdempotency() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL);
        idempotency.values().removeIf(c -> c.at().isBefore(cutoff));
    }
}
