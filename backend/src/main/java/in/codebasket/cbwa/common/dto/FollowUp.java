package in.codebasket.cbwa.common.dto;

import in.codebasket.cbwa.common.dto.SendMessageRequest;

/**
 * A message scheduled for every new registrant: either {@code delayMinutes} after registering or at a
 * fixed {@code sendAt} time (skipped for people who register after it). {name}, {mobile} and {passNo}
 * in text, captions and template variables are replaced per person.
 */
public record FollowUp(String label, Integer delayMinutes, java.time.Instant sendAt, SendMessageRequest message) {
}
