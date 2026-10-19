package in.codebasket.cbwa.payment;

import in.codebasket.cbwa.common.api.ErrorCode;
import in.codebasket.cbwa.common.exception.AppException;
import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves the gateway selected in Settings (PAYMENT.active_gateway). */
@Component
public class PaymentGatewayRegistry {

    private final Map<GatewayType, PaymentGateway> gateways = new EnumMap<>(GatewayType.class);
    private final SettingsService settings;

    public PaymentGatewayRegistry(List<PaymentGateway> all, SettingsService settings) {
        all.forEach(g -> gateways.put(g.type(), g));
        this.settings = settings;
    }

    /** Active and configured gateway, if any. */
    public Optional<PaymentGateway> active() {
        String name = settings.getString(SettingKeys.PAYMENT, "active_gateway", "NONE");
        if ("NONE".equalsIgnoreCase(name)) {
            return Optional.empty();
        }
        try {
            PaymentGateway g = gateways.get(GatewayType.valueOf(name.toUpperCase()));
            return g != null && g.isConfigured() ? Optional.of(g) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public PaymentGateway requireActive() {
        return active().orElseThrow(() -> new AppException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED));
    }

    /** Gateway that created an order (used for verification/reconciliation even if admin switched later). */
    public PaymentGateway of(GatewayType type) {
        PaymentGateway g = gateways.get(type);
        if (g == null || !g.isConfigured()) {
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED);
        }
        return g;
    }
}
