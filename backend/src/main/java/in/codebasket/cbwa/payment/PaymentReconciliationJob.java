package in.codebasket.cbwa.payment;

import in.codebasket.cbwa.payment.dto.PaymentDtos.ReconcileSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** D3: every 10 minutes settles gateway orders stuck in CREATED / ATTEMPTED (missed webhooks, closed browser). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    public void run() {
        try {
            ReconcileSummary s = paymentService.reconcilePending();
            if (s.checked() > 0) {
                log.info("Payment reconciliation: checked={} paid={} expired={} failed={}", s.checked(), s.paid(), s.expired(), s.failed());
            }
        } catch (RuntimeException e) {
            log.warn("Payment reconciliation skipped: {}", e.getMessage());
        }
    }
}
