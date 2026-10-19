package in.codebasket.cbwa.payment;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Payment gateway abstraction (D3). Razorpay is implemented first; Cashfree / PhonePe can be added
 * as further implementations selected by the admin setting PAYMENT.active_gateway.
 */
public interface PaymentGateway {

    GatewayType type();

    /** Keys are present in settings. */
    boolean isConfigured();

    /** Public key shown to the browser checkout (never the secret). */
    String publicKeyId();

    GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes);

    GatewayPayment fetchPayment(String paymentId);

    List<GatewayPayment> fetchOrderPayments(String gatewayOrderId);

    boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature);

    boolean verifyWebhookSignature(byte[] rawBody, String signature);

    /** Throws GatewayException with a readable message when the keys don't work. */
    void testConnection();

    record GatewayOrder(String id, String status, JsonNode raw) {
    }

    enum PaymentState { CAPTURED, AUTHORIZED, FAILED, REFUNDED, PENDING }

    record GatewayPayment(String id, String orderId, long amountPaise, String currency, PaymentState state, String method,
                          String errorDescription, Instant createdAt, JsonNode raw) {
    }

    class GatewayException extends RuntimeException {
        private final int httpStatus;

        public GatewayException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
