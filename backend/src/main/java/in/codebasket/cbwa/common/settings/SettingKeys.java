package in.codebasket.cbwa.common.settings;

/** Setting group codes and keys (seeded in V2__seed.sql). */
public final class SettingKeys {

    private SettingKeys() {
    }

    public static final String COMPANY = "COMPANY";
    public static final String GST = "GST";
    public static final String PAYMENT = "PAYMENT";
    public static final String SMTP = "SMTP";
    public static final String CHARGING = "CHARGING";
    public static final String WALLET = "WALLET";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    public static final String SECURITY = "SECURITY";
    public static final String STORAGE = "STORAGE";
    public static final String RETENTION = "RETENTION";

    public static final String CHARGE_ON = "charge_on";
    public static final String CHARGE_FREE_UTILITY_IN_WINDOW = "charge_free_utility_in_window";
    public static final String HOLD_TIMEOUT_HOURS = "hold_timeout_hours";
    public static final String PLATFORM_FEE_GRACE_DAYS = "platform_fee_grace_days";
    public static final String DEFAULT_LOW_BALANCE = "default_low_balance_threshold";
    public static final String PUBLIC_API_PER_MINUTE = "public_api_per_minute";
    public static final String LOGIN_PER_MINUTE = "login_per_minute";
    public static final String ADMIN_2FA_REQUIRED = "admin_2fa_required";
    public static final String MAX_LOGIN_ATTEMPTS = "max_login_attempts";
    public static final String LOCKOUT_MINUTES = "lockout_minutes";
    public static final String MAX_UPLOAD_MB = "max_upload_mb";
}
