package in.codebasket.cbwa.common.service.registration;

import in.codebasket.cbwa.common.entity.PartnerPaymentAccount;
import in.codebasket.cbwa.common.repository.PartnerPaymentAccountRepository;

import com.fasterxml.jackson.databind.JsonNode;
import in.codebasket.cbwa.exception.ErrorCode;
import in.codebasket.cbwa.common.service.crypto.CryptoService;
import in.codebasket.cbwa.common.utils.HashUtil;
import in.codebasket.cbwa.exception.AppException;
import in.codebasket.cbwa.config.AppProperties;
import in.codebasket.cbwa.common.repository.PartnerRepository;
import in.codebasket.cbwa.common.service.payment.PaymentGateway;
import in.codebasket.cbwa.common.service.payment.RazorpayGateway;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PA1–PA3: the partner's own Razorpay account. Registration fees are paid straight into it; the platform
 * only stores the keys (encrypted) to create orders and verify payments.
 */
@Service
@RequiredArgsConstructor
public class PartnerPaymentService {

    private final PartnerPaymentAccountRepository repository;
    private final PartnerRepository partnerRepository;
    private final CryptoService crypto;
    private final RazorpayGateway razorpay;
    private final AppProperties props;

    public record AccountDto(String provider, String keyId, boolean keySecretSet, boolean webhookSecretSet, boolean enabled,
                             Instant verifiedAt, String webhookUrl) {
    }

    /** Secrets are replace-only: null/blank keeps the stored value. */
    public record AccountRequest(@Pattern(regexp = "^rzp_(test|live)_[A-Za-z0-9]{6,40}$", message = "Key ID looks like rzp_live_XXXX or rzp_test_XXXX")
                                 String keyId, @Size(max = 200) String keySecret, @Size(max = 200) String webhookSecret, Boolean enabled) {
    }

    /** What the browser needs to open Razorpay Checkout. */
    public record Checkout(String keyId, String orderId, long amount, String currency, String name, String description) {
    }

    @Transactional(readOnly = true)
    public AccountDto get(Long partnerId) {
        return toDto(partnerId, repository.findById(partnerId).orElse(null));
    }

    @Transactional
    public AccountDto save(Long partnerId, AccountRequest req) {
        PartnerPaymentAccount a = repository.findById(partnerId).orElseGet(() -> {
            PartnerPaymentAccount n = new PartnerPaymentAccount();
            n.setPartnerId(partnerId);
            return n;
        });
        if (req.keyId() != null && !req.keyId().isBlank()) {
            if (!req.keyId().trim().equals(a.getKeyId())) {
                a.setVerifiedAt(null);
            }
            a.setKeyId(req.keyId().trim());
        }
        if (req.keySecret() != null && !req.keySecret().isBlank()) {
            a.setKeySecretEnc(crypto.encrypt(req.keySecret().trim()));
            a.setVerifiedAt(null);
        }
        if (req.webhookSecret() != null && !req.webhookSecret().isBlank()) {
            a.setWebhookSecretEnc(crypto.encrypt(req.webhookSecret().trim()));
        }
        boolean enable = Boolean.TRUE.equals(req.enabled());
        if (enable) {
            if (a.getKeyId() == null || a.getKeySecretEnc() == null) {
                throw AppException.field("keySecret", "Enter your Razorpay Key ID and Key Secret");
            }
            test(a);
        }
        a.setEnabled(enable);
        return toDto(partnerId, repository.save(a));
    }

    @Transactional
    public AccountDto testNow(Long partnerId) {
        PartnerPaymentAccount a = repository.findById(partnerId)
                .filter(x -> x.getKeyId() != null && x.getKeySecretEnc() != null)
                .orElseThrow(() -> AppException.field("keySecret", "Enter your Razorpay Key ID and Key Secret first"));
        test(a);
        return toDto(partnerId, repository.save(a));
    }

    private void test(PartnerPaymentAccount a) {
        try {
            razorpay.callWith(a.getKeyId(), crypto.decrypt(a.getKeySecretEnc()), "GET", "/v1/orders?count=1", null);
            a.setVerifiedAt(Instant.now());
        } catch (PaymentGateway.GatewayException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    e.getHttpStatus() == 401 ? "Razorpay did not accept these keys. Check the Key ID and Key Secret" : "Razorpay: " + e.getMessage(),
                    null, List.of(new in.codebasket.cbwa.common.dto.FieldErrorDto("keySecret", "Razorpay did not accept these keys")));
        }
    }

    /** True when the partner can take registration fees right now. */
    @Transactional(readOnly = true)
    public boolean ready(Long partnerId) {
        return repository.findById(partnerId).map(a -> a.isEnabled() && a.getKeyId() != null && a.getKeySecretEnc() != null).orElse(false);
    }

    public Checkout createOrder(Long partnerId, long amountPaise, String receipt, Map<String, String> notes, String name, String description) {
        PartnerPaymentAccount a = active(partnerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", notes);
        try {
            JsonNode res = razorpay.callWith(a.getKeyId(), crypto.decrypt(a.getKeySecretEnc()), "POST", "/v1/orders", body);
            return new Checkout(a.getKeyId(), res.path("id").asText(), amountPaise, "INR", name, description);
        } catch (PaymentGateway.GatewayException e) {
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED, "Online payment is not available right now. Please try again in a few minutes");
        }
    }

    public boolean verifyCheckoutSignature(Long partnerId, String orderId, String paymentId, String signature) {
        PartnerPaymentAccount a = repository.findById(partnerId).orElse(null);
        if (a == null || a.getKeySecretEnc() == null || signature == null || orderId == null || paymentId == null) {
            return false;
        }
        String expected = HashUtil.hmacSha256Hex(crypto.decrypt(a.getKeySecretEnc()), orderId + "|" + paymentId);
        return HashUtil.constantTimeEquals(expected, signature.trim().toLowerCase());
    }

    public boolean verifyWebhookSignature(Long partnerId, byte[] body, String signature) {
        PartnerPaymentAccount a = repository.findById(partnerId).orElse(null);
        if (a == null || a.getWebhookSecretEnc() == null || signature == null || body == null) {
            return false;
        }
        String expected = HashUtil.hmacSha256Hex(crypto.decrypt(a.getWebhookSecretEnc()).getBytes(StandardCharsets.UTF_8), body);
        return HashUtil.constantTimeEquals(expected, signature.trim().toLowerCase());
    }

    /** Payments of an order, asked from Razorpay (for reconciliation when the browser never came back). */
    public List<PaymentGateway.GatewayPayment> orderPayments(Long partnerId, String orderId) {
        PartnerPaymentAccount a = repository.findById(partnerId).orElse(null);
        if (a == null || a.getKeySecretEnc() == null) {
            return List.of();
        }
        JsonNode res = razorpay.callWith(a.getKeyId(), crypto.decrypt(a.getKeySecretEnc()), "GET", "/v1/orders/" + orderId + "/payments", null);
        List<PaymentGateway.GatewayPayment> out = new java.util.ArrayList<>();
        res.path("items").forEach(i -> out.add(RazorpayGateway.toPayment(i)));
        return out;
    }

    private PartnerPaymentAccount active(Long partnerId) {
        return repository.findById(partnerId).filter(PartnerPaymentAccount::isEnabled)
                .orElseThrow(() -> new AppException(ErrorCode.CONFLICT, "Online payment is not set up for this registration"));
    }

    private AccountDto toDto(Long partnerId, PartnerPaymentAccount a) {
        String uid = partnerRepository.findById(partnerId).map(p -> p.getUid()).orElse("");
        String base = props.getPublicBaseUrl() == null ? "" : props.getPublicBaseUrl().replaceAll("/+$", "");
        String hook = base + "/api/webhooks/razorpay/partners/" + uid;
        if (a == null) {
            return new AccountDto("RAZORPAY", null, false, false, false, null, hook);
        }
        return new AccountDto(a.getProvider(), a.getKeyId(), a.getKeySecretEnc() != null, a.getWebhookSecretEnc() != null, a.isEnabled(),
                a.getVerifiedAt(), hook);
    }
}
