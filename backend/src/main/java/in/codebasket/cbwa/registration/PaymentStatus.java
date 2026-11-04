package in.codebasket.cbwa.registration;

/** NOT_REQUIRED = free link. PENDING → PAID (pass created and sent) or FAILED (person can try again). */
public enum PaymentStatus {
    NOT_REQUIRED, PENDING, PAID, FAILED
}
