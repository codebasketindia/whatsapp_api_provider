package in.codebasket.cbwa.common.job;

import in.codebasket.cbwa.common.constants.PaymentStatus;
import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.repository.RegistrationRepository;
import in.codebasket.cbwa.common.service.registration.RegistrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Safety net for paid registrations: if neither the browser nor the webhook confirmed a payment, ask
 * Razorpay about orders 2 minutes to 24 hours old (every 3 minutes).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationPaymentJob {

    private final RegistrationRepository repository;
    private final RegistrationService service;

    @Scheduled(fixedDelay = 180_000, initialDelay = 60_000)
    public void run() {
        runOnce();
    }

    public int runOnce() {
        Instant now = Instant.now();
        int paid = 0;
        for (Registration r : repository.findTop50ByPaymentStatusAndRazorpayOrderIdIsNotNullAndUpdatedAtBetweenOrderByIdAsc(
                PaymentStatus.PENDING, now.minus(Duration.ofHours(24)), now.minus(Duration.ofMinutes(2)))) {
            if (service.checkPayment(r) == PaymentStatus.PAID) {
                paid++;
            }
        }
        if (paid > 0) {
            log.info("Registration payment check completed {} registrations", paid);
        }
        return paid;
    }
}
