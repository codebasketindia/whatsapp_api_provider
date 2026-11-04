package in.codebasket.cbwa.registration;

/** AWAITING_PAYMENT (paid links, before payment) → PENDING → SENT; WAITING_FOR_CHAT when no template is set and the customer has not messaged yet. */
public enum PassStatus {
    AWAITING_PAYMENT, PENDING, SENT, WAITING_FOR_CHAT, FAILED, DISABLED
}
