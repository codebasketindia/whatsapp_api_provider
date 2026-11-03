package in.codebasket.cbwa.registration;

/** PENDING → SENT; WAITING_FOR_CHAT when no template is set and the customer has not messaged yet. */
public enum PassStatus {
    PENDING, SENT, WAITING_FOR_CHAT, FAILED, DISABLED
}
