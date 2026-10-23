package in.codebasket.cbwa.common.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error catalogue from the API contract (docs/phase-0/03-api-contract.md §2).
 * The frontend and partners rely on these codes - never rename one.
 */
@Getter
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Please fix the highlighted fields"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request is not valid"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Mobile number or password is incorrect"),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts. Please try again later"),
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Your account is disabled. Please contact support"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Your session has expired"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Please log in to continue"),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "Your session has ended. Please log in again"),
    AUTH_OTP_INVALID(HttpStatus.BAD_REQUEST, "The code is not correct"),
    AUTH_OTP_EXPIRED(HttpStatus.BAD_REQUEST, "The code has expired. Please request a new one"),
    AUTH_PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters with upper case, lower case and a number"),
    AUTH_PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Please change your password to continue"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have permission to do this"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    CONFLICT(HttpStatus.CONFLICT, "This conflicts with existing data"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down"),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "The file is too large"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "This file type is not allowed"),
    WALLET_INSUFFICIENT_BALANCE(HttpStatus.PAYMENT_REQUIRED, "Insufficient wallet balance. Please add money"),
    WALLET_NEGATIVE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Wallet balance cannot go below zero"),
    TOPUP_DUPLICATE_UTR(HttpStatus.CONFLICT, "This UTR / reference number is already used"),
    TOPUP_ALREADY_PROCESSED(HttpStatus.CONFLICT, "This request is already processed"),
    PAYMENT_GATEWAY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Online payment is not available right now"),
    PAYMENT_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "Payment verification failed"),
    PAYMENT_AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "Amount is outside the allowed range"),
    RATE_NOT_CONFIGURED(HttpStatus.UNPROCESSABLE_ENTITY, "Price is not set for this message type / country in your plan"),
    META_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "WhatsApp platform settings are not configured yet"),
    META_API_ERROR(HttpStatus.BAD_GATEWAY, "WhatsApp returned an error"),
    META_TOKEN_INVALID(HttpStatus.CONFLICT, "WhatsApp access has expired. Please reconnect the number"),
    OWN_NUMBER_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Connecting your own number is not enabled for your account"),
    SENDER_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "This sender number is not available"),
    SENDER_PAUSED(HttpStatus.UNPROCESSABLE_ENTITY, "This sender number is paused"),
    SENDER_DAILY_CAP_REACHED(HttpStatus.TOO_MANY_REQUESTS, "Daily sending limit reached for this number"),
    TEMPLATE_NOT_APPROVED(HttpStatus.UNPROCESSABLE_ENTITY, "This template is not approved"),
    TEMPLATE_INVALID_STATE(HttpStatus.CONFLICT, "This template cannot be changed in its current status"),
    TEMPLATE_VARIABLES_MISMATCH(HttpStatus.BAD_REQUEST, "Template variables don't match the template"),
    WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "24-hour window is closed - use an approved template"),
    RECIPIENT_INVALID(HttpStatus.BAD_REQUEST, "The phone number is not valid"),
    RECIPIENT_OPTED_OUT(HttpStatus.UNPROCESSABLE_ENTITY, "This contact has opted out of marketing messages"),
    RECIPIENT_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "This contact is blocked"),
    CAMPAIGN_INVALID_STATE(HttpStatus.CONFLICT, "This action is not allowed for the campaign's current status"),
    IMPORT_INVALID_FILE(HttpStatus.BAD_REQUEST, "The file could not be read"),
    API_KEY_INVALID(HttpStatus.UNAUTHORIZED, "Invalid API key or secret"),
    API_KEY_DISABLED(HttpStatus.FORBIDDEN, "This API key is disabled"),
    IP_NOT_WHITELISTED(HttpStatus.FORBIDDEN, "Requests from this IP address are not allowed"),
    PARTNER_SUSPENDED(HttpStatus.FORBIDDEN, "This account is suspended"),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "Invalid webhook signature"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
