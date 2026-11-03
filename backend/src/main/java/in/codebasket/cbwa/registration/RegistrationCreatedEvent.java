package in.codebasket.cbwa.registration;

/** Published inside the registration transaction; the pass is sent after it commits. */
public record RegistrationCreatedEvent(Long registrationId) {
}
