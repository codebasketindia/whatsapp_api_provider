-- CB WhatsApp Gateway - complete schema (see docs/phase-0/02-database.md)
-- MySQL 8, InnoDB, utf8mb4. All DATETIME values are UTC.

-- ============================ A. Identity & access ============================
CREATE TABLE roles (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    user_type    VARCHAR(16)  NOT NULL,
    is_system    BOOLEAN      NOT NULL DEFAULT FALSE,
    description  VARCHAR(255) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    created_by   BIGINT UNSIGNED NULL,
    updated_by   BIGINT UNSIGNED NULL,
    version      INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_roles_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE permissions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    module      VARCHAR(32)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT uk_permissions_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE users (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid                   VARCHAR(26)     NOT NULL,
    user_type             VARCHAR(16)  NOT NULL,
    role_id               BIGINT UNSIGNED NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    email                 VARCHAR(190) NOT NULL,
    mobile                VARCHAR(20)  NULL,
    password_hash         VARCHAR(100) NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    two_factor_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at   DATETIME(6)  NULL,
    last_login_at         DATETIME(6)  NULL,
    last_login_ip         VARCHAR(45)  NULL,
    theme                 VARCHAR(8)   NOT NULL DEFAULT 'SYSTEM',
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            BIGINT UNSIGNED NULL,
    updated_by            BIGINT UNSIGNED NULL,
    version               INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_uid UNIQUE (uid),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id),
    INDEX idx_users_type_status (user_type, status)
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT UNSIGNED NOT NULL,
    token_hash           VARCHAR(64)    NOT NULL,
    family_id            VARCHAR(26)    NOT NULL,
    issued_at            DATETIME(6) NOT NULL,
    expires_at           DATETIME(6) NOT NULL,
    revoked_at           DATETIME(6) NULL,
    replaced_by_id       BIGINT UNSIGNED NULL,
    impersonator_user_id BIGINT UNSIGNED NULL,
    ip                   VARCHAR(45)  NULL,
    user_agent           VARCHAR(255) NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_family (family_id),
    INDEX idx_refresh_tokens_expires (expires_at)
) ENGINE=InnoDB;

CREATE TABLE user_otps (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT UNSIGNED NOT NULL,
    challenge_id VARCHAR(26)    NOT NULL,
    purpose      VARCHAR(24) NOT NULL,
    otp_hash     VARCHAR(64)    NOT NULL,
    attempts     INT         NOT NULL DEFAULT 0,
    resend_count INT         NOT NULL DEFAULT 0,
    expires_at   DATETIME(6) NOT NULL,
    consumed_at  DATETIME(6) NULL,
    created_at   DATETIME(6) NOT NULL,
    CONSTRAINT uk_user_otps_challenge UNIQUE (challenge_id),
    CONSTRAINT fk_user_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_user_otps_expires (expires_at)
) ENGINE=InnoDB;

-- ============================ Platform: files, sequences, settings ============================
CREATE TABLE stored_files (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid           VARCHAR(26)     NOT NULL,
    partner_id    BIGINT UNSIGNED NULL,
    category      VARCHAR(32)  NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_path  VARCHAR(500) NOT NULL,
    mime_type     VARCHAR(127) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        VARCHAR(64)     NOT NULL,
    created_by    BIGINT UNSIGNED NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_stored_files_uid UNIQUE (uid),
    INDEX idx_stored_files_partner (partner_id, category)
) ENGINE=InnoDB;

CREATE TABLE number_sequences (
    name       VARCHAR(40) NOT NULL PRIMARY KEY,
    prefix     VARCHAR(20) NOT NULL,
    next_value BIGINT      NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE settings (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_code   VARCHAR(32) NOT NULL,
    setting_key  VARCHAR(64) NOT NULL,
    value        TEXT        NULL,
    value_type   VARCHAR(16) NOT NULL,
    is_encrypted BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    created_by   BIGINT UNSIGNED NULL,
    updated_by   BIGINT UNSIGNED NULL,
    version      INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_settings_group_key UNIQUE (group_code, setting_key)
) ENGINE=InnoDB;

-- ============================ D. Pricing ============================
CREATE TABLE rate_plans (
    id                               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                             VARCHAR(100)  NOT NULL,
    description                      VARCHAR(255)  NULL,
    is_default                       BOOLEAN       NOT NULL DEFAULT FALSE,
    is_custom                        BOOLEAN       NOT NULL DEFAULT FALSE,
    partner_id                       BIGINT UNSIGNED NULL,
    currency                         VARCHAR(3)       NOT NULL DEFAULT 'INR',
    session_message_fee              DECIMAL(12,4) NOT NULL DEFAULT 0,
    platform_fee_per_message         DECIMAL(12,4) NOT NULL DEFAULT 0,
    platform_fee_monthly_per_number  DECIMAL(12,4) NOT NULL DEFAULT 0,
    status                           VARCHAR(16)   NOT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    INDEX idx_rate_plans_status (status)
) ENGINE=InnoDB;

CREATE TABLE rate_plan_items (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rate_plan_id BIGINT UNSIGNED NOT NULL,
    country_code VARCHAR(2)    NOT NULL,
    category     VARCHAR(32)   NOT NULL,
    price        DECIMAL(12,4) NOT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_rate_plan_items UNIQUE (rate_plan_id, country_code, category),
    CONSTRAINT fk_rate_plan_items_plan FOREIGN KEY (rate_plan_id) REFERENCES rate_plans (id) ON DELETE CASCADE,
    CONSTRAINT chk_rate_plan_items_price CHECK (price >= 0)
) ENGINE=InnoDB;

-- ============================ B. Partners ============================
CREATE TABLE partners (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid              VARCHAR(26)     NOT NULL,
    partner_code     VARCHAR(20)  NOT NULL,
    user_id          BIGINT UNSIGNED NOT NULL,
    business_name    VARCHAR(190) NOT NULL,
    contact_name     VARCHAR(120) NOT NULL,
    email            VARCHAR(190) NOT NULL,
    mobile           VARCHAR(20)  NOT NULL,
    gstin            VARCHAR(15)  NULL,
    pan              VARCHAR(10)  NULL,
    address_line1    VARCHAR(190) NULL,
    address_line2    VARCHAR(190) NULL,
    city             VARCHAR(100) NULL,
    state            VARCHAR(100) NULL,
    state_code       VARCHAR(2)      NULL,
    pincode          VARCHAR(10)  NULL,
    country          VARCHAR(2)      NOT NULL DEFAULT 'IN',
    website          VARCHAR(255) NULL,
    status           VARCHAR(16)  NOT NULL,
    suspend_reason   VARCHAR(255) NULL,
    kyc_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    rate_plan_id     BIGINT UNSIGNED NOT NULL,
    allow_own_number BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_partners_uid UNIQUE (uid),
    CONSTRAINT uk_partners_code UNIQUE (partner_code),
    CONSTRAINT uk_partners_user UNIQUE (user_id),
    CONSTRAINT fk_partners_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_partners_rate_plan FOREIGN KEY (rate_plan_id) REFERENCES rate_plans (id),
    INDEX idx_partners_status (status),
    INDEX idx_partners_business_name (business_name)
) ENGINE=InnoDB;

ALTER TABLE rate_plans ADD CONSTRAINT fk_rate_plans_partner FOREIGN KEY (partner_id) REFERENCES partners (id);
ALTER TABLE stored_files ADD CONSTRAINT fk_stored_files_partner FOREIGN KEY (partner_id) REFERENCES partners (id);

-- ============================ E. Meta / WhatsApp ============================
CREATE TABLE meta_settings (
    id                         BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    app_id                     VARCHAR(32)   NULL,
    app_secret_enc             VARCHAR(1024) NULL,
    embedded_signup_config_id  VARCHAR(32)   NULL,
    solution_id                VARCHAR(32)   NULL,
    business_portfolio_id      VARCHAR(32)   NULL,
    graph_api_version          VARCHAR(8)    NOT NULL,
    webhook_verify_token_enc   VARCHAR(1024) NULL,
    billing_mode               VARCHAR(24)   NOT NULL,
    extended_credit_line_id    VARCHAR(32)   NULL,
    last_test_at               DATETIME(6)   NULL,
    last_test_ok               BOOLEAN       NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE whatsapp_accounts (
    id                           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    waba_id                      VARCHAR(32)  NOT NULL,
    owner_type                   VARCHAR(16)  NOT NULL,
    partner_id                   BIGINT UNSIGNED NULL,
    business_id                  VARCHAR(32)  NULL,
    name                         VARCHAR(190) NULL,
    currency                     VARCHAR(3)      NULL,
    timezone_id                  VARCHAR(16)  NULL,
    message_template_namespace   VARCHAR(64)  NULL,
    account_review_status        VARCHAR(16)  NULL,
    business_verification_status VARCHAR(32)  NULL,
    payment_method_attached      BOOLEAN      NULL,
    access_token_enc             TEXT         NOT NULL,
    token_type                   VARCHAR(24)  NOT NULL,
    token_expires_at             DATETIME(6)  NULL,
    token_status                 VARCHAR(16)  NOT NULL,
    token_checked_at             DATETIME(6)  NULL,
    webhook_subscribed           BOOLEAN      NOT NULL DEFAULT FALSE,
    onboarding_type              VARCHAR(16)  NOT NULL,
    billing_mode                 VARCHAR(24)  NOT NULL,
    credit_line_shared           BOOLEAN      NOT NULL DEFAULT FALSE,
    status                       VARCHAR(16)  NOT NULL,
    restriction_info             JSON         NULL,
    connected_at                 DATETIME(6)  NULL,
    disconnected_at              DATETIME(6)  NULL,
    raw                          JSON         NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_whatsapp_accounts_waba UNIQUE (waba_id),
    CONSTRAINT fk_whatsapp_accounts_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    INDEX idx_whatsapp_accounts_owner (owner_type, status)
) ENGINE=InnoDB;

CREATE TABLE phone_numbers (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    whatsapp_account_id      BIGINT UNSIGNED NOT NULL,
    phone_number_id          VARCHAR(32)  NOT NULL,
    display_phone_number     VARCHAR(32)  NULL,
    wa_number                VARCHAR(20)  NULL,
    verified_name            VARCHAR(190) NULL,
    name_status              VARCHAR(32)  NULL,
    new_name_status          VARCHAR(32)  NULL,
    quality_rating           VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    messaging_limit_tier     VARCHAR(32)  NULL,
    throughput_level         VARCHAR(16)  NULL,
    max_mps                  INT          NOT NULL DEFAULT 60,
    code_verification_status VARCHAR(32)  NULL,
    platform_type            VARCHAR(16)  NULL,
    meta_status              VARCHAR(32)  NULL,
    health_status            JSON         NULL,
    sender_type              VARCHAR(24)  NOT NULL,
    is_coexistence           BOOLEAN      NOT NULL DEFAULT FALSE,
    is_registered            BOOLEAN      NOT NULL DEFAULT FALSE,
    pin_enc                  VARCHAR(255) NULL,
    daily_send_cap           INT          NULL,
    is_paused                BOOLEAN      NOT NULL DEFAULT FALSE,
    pause_reason             VARCHAR(255) NULL,
    status                   VARCHAR(16)  NOT NULL,
    connected_at             DATETIME(6)  NULL,
    last_synced_at           DATETIME(6)  NULL,
    raw                      JSON         NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_phone_numbers_pnid UNIQUE (phone_number_id),
    CONSTRAINT fk_phone_numbers_waba FOREIGN KEY (whatsapp_account_id) REFERENCES whatsapp_accounts (id),
    INDEX idx_pn_sender_type_status (sender_type, status)
) ENGINE=InnoDB;

CREATE TABLE partner_phone_numbers (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id      BIGINT UNSIGNED NOT NULL,
    phone_number_id BIGINT UNSIGNED NOT NULL,
    assignment_type VARCHAR(16) NOT NULL,
    is_default      BOOLEAN     NOT NULL DEFAULT FALSE,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    active_key      BIGINT UNSIGNED GENERATED ALWAYS AS (IF(active, phone_number_id, NULL)) STORED,
    assigned_by     BIGINT UNSIGNED NULL,
    assigned_at     DATETIME(6) NOT NULL,
    unassigned_at   DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppn_active_key UNIQUE (active_key),
    CONSTRAINT fk_ppn_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_ppn_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    INDEX idx_ppn_partner_active (partner_id, active)
) ENGINE=InnoDB;

CREATE TABLE partner_settings (
    partner_id              BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    low_balance_threshold   DECIMAL(12,4) NULL,
    low_balance_alerted_at  DATETIME(6)   NULL,
    opt_out_keywords        JSON          NOT NULL,
    opt_in_keywords         JSON          NOT NULL,
    opt_out_auto_reply      VARCHAR(1024) NULL,
    default_phone_number_id BIGINT UNSIGNED NULL,
    notify_email            BOOLEAN       NOT NULL DEFAULT TRUE,
    timezone                VARCHAR(40)   NOT NULL DEFAULT 'Asia/Kolkata',
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_partner_settings_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    CONSTRAINT fk_partner_settings_phone FOREIGN KEY (default_phone_number_id) REFERENCES phone_numbers (id)
) ENGINE=InnoDB;

-- ============================ C. Wallet / top-ups / payments ============================
CREATE TABLE wallets (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id   BIGINT UNSIGNED NOT NULL,
    balance      DECIMAL(12,4) NOT NULL DEFAULT 0,
    held_amount  DECIMAL(12,4) NOT NULL DEFAULT 0,
    currency     VARCHAR(3)       NOT NULL DEFAULT 'INR',
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_wallets_partner UNIQUE (partner_id),
    CONSTRAINT fk_wallets_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT chk_wallets_balance CHECK (balance >= 0),
    CONSTRAINT chk_wallets_held CHECK (held_amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE wallet_transactions (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid                  VARCHAR(26)      NOT NULL,
    wallet_id            BIGINT UNSIGNED NOT NULL,
    partner_id           BIGINT UNSIGNED NOT NULL,
    type                 VARCHAR(16)   NOT NULL,
    amount               DECIMAL(12,4) NOT NULL,
    balance_before       DECIMAL(12,4) NOT NULL,
    balance_after        DECIMAL(12,4) NOT NULL,
    held_before          DECIMAL(12,4) NOT NULL,
    held_after           DECIMAL(12,4) NOT NULL,
    reference_type       VARCHAR(32)   NOT NULL,
    reference_id         VARCHAR(64)   NULL,
    remarks              VARCHAR(500)  NULL,
    created_by           BIGINT UNSIGNED NULL,
    impersonator_user_id BIGINT UNSIGNED NULL,
    created_at           DATETIME(6)   NOT NULL,
    CONSTRAINT uk_wtx_uid UNIQUE (uid),
    CONSTRAINT fk_wtx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_wtx_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT chk_wtx_amount CHECK (amount > 0),
    INDEX idx_wtx_wallet_created (wallet_id, created_at),
    INDEX idx_wtx_ref (reference_type, reference_id),
    INDEX idx_wtx_partner_type_created (partner_id, type, created_at)
) ENGINE=InnoDB;

CREATE TABLE credit_packs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(255)  NULL,
    price       DECIMAL(12,4) NOT NULL,
    bonus_type  VARCHAR(16)   NOT NULL,
    bonus_value DECIMAL(12,4) NOT NULL DEFAULT 0,
    is_popular  BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE invoices (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    invoice_no        VARCHAR(32)   NOT NULL,
    partner_id        BIGINT UNSIGNED NOT NULL,
    payment_order_id  BIGINT UNSIGNED NULL,
    topup_request_id  BIGINT UNSIGNED NULL,
    invoice_date      DATE          NOT NULL,
    billing_name      VARCHAR(190)  NOT NULL,
    gstin             VARCHAR(15)   NULL,
    billing_address   TEXT          NULL,
    place_of_supply   VARCHAR(2)       NULL,
    taxable_amount    DECIMAL(12,4) NOT NULL,
    cgst_amount       DECIMAL(12,4) NOT NULL DEFAULT 0,
    sgst_amount       DECIMAL(12,4) NOT NULL DEFAULT 0,
    igst_amount       DECIMAL(12,4) NOT NULL DEFAULT 0,
    total_amount      DECIMAL(12,4) NOT NULL,
    file_id           BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_invoices_no UNIQUE (invoice_no),
    CONSTRAINT fk_invoices_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_invoices_file FOREIGN KEY (file_id) REFERENCES stored_files (id),
    INDEX idx_invoices_partner_date (partner_id, invoice_date)
) ENGINE=InnoDB;

CREATE TABLE topup_requests (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_no            VARCHAR(20)   NOT NULL,
    partner_id            BIGINT UNSIGNED NOT NULL,
    amount                DECIMAL(12,4) NOT NULL,
    payment_mode          VARCHAR(16)   NOT NULL,
    utr                   VARCHAR(64)   NOT NULL,
    payment_date          DATE          NOT NULL,
    screenshot_file_id    BIGINT UNSIGNED NULL,
    note                  VARCHAR(500)  NULL,
    status                VARCHAR(16)   NOT NULL,
    reject_reason         VARCHAR(500)  NULL,
    wallet_transaction_id BIGINT UNSIGNED NULL,
    invoice_id            BIGINT UNSIGNED NULL,
    reviewed_by           BIGINT UNSIGNED NULL,
    reviewed_at           DATETIME(6)   NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_topup_request_no UNIQUE (request_no),
    CONSTRAINT uk_topup_utr UNIQUE (utr),
    CONSTRAINT fk_topup_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_topup_file FOREIGN KEY (screenshot_file_id) REFERENCES stored_files (id),
    CONSTRAINT fk_topup_wtx FOREIGN KEY (wallet_transaction_id) REFERENCES wallet_transactions (id),
    CONSTRAINT fk_topup_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_topup_amount CHECK (amount > 0),
    INDEX idx_topup_status_created (status, created_at),
    INDEX idx_topup_partner (partner_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE payment_orders (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid                   VARCHAR(26)      NOT NULL,
    partner_id            BIGINT UNSIGNED NOT NULL,
    gateway               VARCHAR(16)   NOT NULL,
    gateway_order_id      VARCHAR(64)   NULL,
    gateway_payment_id    VARCHAR(64)   NULL,
    credit_pack_id        BIGINT UNSIGNED NULL,
    base_amount           DECIMAL(12,4) NOT NULL,
    bonus_amount          DECIMAL(12,4) NOT NULL DEFAULT 0,
    gst_percent           DECIMAL(5,2)  NOT NULL DEFAULT 0,
    gst_amount            DECIMAL(12,4) NOT NULL DEFAULT 0,
    total_amount          DECIMAL(12,4) NOT NULL,
    credit_amount         DECIMAL(12,4) NOT NULL,
    currency              VARCHAR(3)       NOT NULL DEFAULT 'INR',
    status                VARCHAR(16)   NOT NULL,
    method                VARCHAR(32)   NULL,
    failure_reason        VARCHAR(255)  NULL,
    verified_via          VARCHAR(16)   NULL,
    paid_at               DATETIME(6)   NULL,
    wallet_transaction_id BIGINT UNSIGNED NULL,
    invoice_id            BIGINT UNSIGNED NULL,
    raw_payload           JSON          NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_po_uid UNIQUE (uid),
    CONSTRAINT uk_po_gateway_order UNIQUE (gateway_order_id),
    CONSTRAINT uk_po_gateway_payment UNIQUE (gateway_payment_id),
    CONSTRAINT fk_po_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_po_pack FOREIGN KEY (credit_pack_id) REFERENCES credit_packs (id),
    CONSTRAINT fk_po_wtx FOREIGN KEY (wallet_transaction_id) REFERENCES wallet_transactions (id),
    CONSTRAINT fk_po_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    INDEX idx_po_partner_created (partner_id, created_at),
    INDEX idx_po_status_created (status, created_at)
) ENGINE=InnoDB;

ALTER TABLE invoices ADD CONSTRAINT fk_invoices_po FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id);
ALTER TABLE invoices ADD CONSTRAINT fk_invoices_topup FOREIGN KEY (topup_request_id) REFERENCES topup_requests (id);

CREATE TABLE payment_gateway_logs (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_order_id BIGINT UNSIGNED NULL,
    gateway          VARCHAR(16) NOT NULL,
    direction        VARCHAR(16) NOT NULL,
    event            VARCHAR(64) NOT NULL,
    http_status      INT         NULL,
    payload          JSON        NULL,
    created_at       DATETIME(6) NOT NULL,
    CONSTRAINT fk_pgl_po FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    INDEX idx_pgl_po (payment_order_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE platform_fee_charges (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id            BIGINT UNSIGNED NOT NULL,
    phone_number_id       BIGINT UNSIGNED NOT NULL,
    period                VARCHAR(7)       NOT NULL,
    amount                DECIMAL(12,4) NOT NULL,
    wallet_transaction_id BIGINT UNSIGNED NULL,
    status                VARCHAR(24)   NOT NULL,
    attempts              INT           NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    CONSTRAINT uk_pfc_phone_period UNIQUE (phone_number_id, period),
    CONSTRAINT fk_pfc_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_pfc_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    CONSTRAINT fk_pfc_wtx FOREIGN KEY (wallet_transaction_id) REFERENCES wallet_transactions (id)
) ENGINE=InnoDB;

-- ============================ E (cont). Sender tools ============================
CREATE TABLE business_profiles (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    phone_number_id         BIGINT UNSIGNED NOT NULL,
    about                   VARCHAR(139)  NULL,
    description             VARCHAR(512)  NULL,
    address                 VARCHAR(256)  NULL,
    email                   VARCHAR(128)  NULL,
    websites                JSON          NULL,
    vertical                VARCHAR(32)   NULL,
    profile_picture_url     VARCHAR(1024) NULL,
    profile_picture_file_id BIGINT UNSIGNED NULL,
    synced_at               DATETIME(6)   NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_business_profiles_phone UNIQUE (phone_number_id),
    CONSTRAINT fk_business_profiles_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    CONSTRAINT fk_business_profiles_file FOREIGN KEY (profile_picture_file_id) REFERENCES stored_files (id)
) ENGINE=InnoDB;

CREATE TABLE qr_codes (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id         BIGINT UNSIGNED NULL,
    phone_number_id    BIGINT UNSIGNED NOT NULL,
    meta_code          VARCHAR(64)   NOT NULL,
    prefilled_message  VARCHAR(140)  NOT NULL,
    deep_link_url      VARCHAR(255)  NULL,
    qr_image_png_url   VARCHAR(1024) NULL,
    qr_image_svg_url   VARCHAR(1024) NULL,
    short_link         VARCHAR(512)  NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_qr_codes_phone_code UNIQUE (phone_number_id, meta_code),
    CONSTRAINT fk_qr_codes_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_qr_codes_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id)
) ENGINE=InnoDB;

CREATE TABLE conversational_components (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    phone_number_id        BIGINT UNSIGNED NOT NULL,
    enable_welcome_message BOOLEAN     NOT NULL DEFAULT FALSE,
    prompts                JSON        NULL,
    commands               JSON        NULL,
    sync_status            VARCHAR(16) NULL,
    synced_at              DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_conv_components_phone UNIQUE (phone_number_id),
    CONSTRAINT fk_conv_components_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id)
) ENGINE=InnoDB;

CREATE TABLE blocked_users (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id      BIGINT UNSIGNED NULL,
    phone_number_id BIGINT UNSIGNED NOT NULL,
    wa_id           VARCHAR(20)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    meta_error      VARCHAR(255) NULL,
    blocked_at      DATETIME(6)  NULL,
    unblocked_at    DATETIME(6)  NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_blocked_users UNIQUE (phone_number_id, wa_id),
    CONSTRAINT fk_blocked_users_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_blocked_users_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id)
) ENGINE=InnoDB;

-- ============================ Templates ============================
CREATE TABLE templates (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid                   VARCHAR(26)     NOT NULL,
    partner_id            BIGINT UNSIGNED NULL,
    whatsapp_account_id   BIGINT UNSIGNED NOT NULL,
    meta_template_id      VARCHAR(32)  NULL,
    name                  VARCHAR(512) NOT NULL,
    language              VARCHAR(10)  NOT NULL,
    category              VARCHAR(32)  NOT NULL,
    previous_category     VARCHAR(32)  NULL,
    parameter_format      VARCHAR(16)  NOT NULL DEFAULT 'POSITIONAL',
    library_template_name VARCHAR(128) NULL,
    status                VARCHAR(24)  NOT NULL,
    rejected_reason       VARCHAR(500) NULL,
    quality_score         VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    admin_review_note     VARCHAR(500) NULL,
    reviewed_by           BIGINT UNSIGNED NULL,
    reviewed_at           DATETIME(6)  NULL,
    submitted_at          DATETIME(6)  NULL,
    last_synced_at        DATETIME(6)  NULL,
    meta_payload          JSON         NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_templates_uid UNIQUE (uid),
    CONSTRAINT uk_templates_meta_id UNIQUE (meta_template_id),
    CONSTRAINT uk_templates_waba_name_lang UNIQUE (whatsapp_account_id, name, language),
    CONSTRAINT fk_templates_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_templates_waba FOREIGN KEY (whatsapp_account_id) REFERENCES whatsapp_accounts (id),
    INDEX idx_templates_partner_status (partner_id, status),
    INDEX idx_templates_status (status)
) ENGINE=InnoDB;

CREATE TABLE template_components (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id                 BIGINT UNSIGNED NOT NULL,
    position                    INT         NOT NULL,
    type                        VARCHAR(16) NOT NULL,
    format                      VARCHAR(16) NULL,
    text                        TEXT        NULL,
    example                     JSON        NULL,
    buttons                     JSON        NULL,
    header_media_file_id        BIGINT UNSIGNED NULL,
    header_handle               VARCHAR(1024) NULL,
    add_security_recommendation BOOLEAN     NULL,
    code_expiration_minutes     INT         NULL,
    CONSTRAINT fk_tc_template FOREIGN KEY (template_id) REFERENCES templates (id) ON DELETE CASCADE,
    CONSTRAINT fk_tc_file FOREIGN KEY (header_media_file_id) REFERENCES stored_files (id),
    INDEX idx_tc_template (template_id, position)
) ENGINE=InnoDB;

CREATE TABLE template_analytics (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT UNSIGNED NOT NULL,
    date        DATE          NOT NULL,
    sent        INT           NOT NULL DEFAULT 0,
    delivered   INT           NOT NULL DEFAULT 0,
    read_count  INT           NOT NULL DEFAULT 0,
    clicked     JSON          NULL,
    cost        DECIMAL(12,4) NOT NULL DEFAULT 0,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT uk_template_analytics UNIQUE (template_id, date),
    CONSTRAINT fk_ta_template FOREIGN KEY (template_id) REFERENCES templates (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================ F. Contacts & campaigns ============================
CREATE TABLE contacts (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id      BIGINT UNSIGNED NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    name            VARCHAR(120) NULL,
    email           VARCHAR(190) NULL,
    country_code    VARCHAR(2)      NULL,
    tags            JSON         NULL,
    attributes      JSON         NULL,
    opted_out       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_blocked      BOOLEAN      NOT NULL DEFAULT FALSE,
    last_inbound_at DATETIME(6)  NULL,
    source          VARCHAR(16)  NOT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_contacts_partner_phone UNIQUE (partner_id, phone),
    CONSTRAINT fk_contacts_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    INDEX idx_contacts_partner_name (partner_id, name)
) ENGINE=InnoDB;

CREATE TABLE contact_groups (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id   BIGINT UNSIGNED NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255) NULL,
    member_count INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_contact_groups UNIQUE (partner_id, name),
    CONSTRAINT fk_contact_groups_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) ENGINE=InnoDB;

CREATE TABLE contact_group_members (
    group_id   BIGINT UNSIGNED NOT NULL,
    contact_id BIGINT UNSIGNED NOT NULL,
    added_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (group_id, contact_id),
    CONSTRAINT fk_cgm_group FOREIGN KEY (group_id) REFERENCES contact_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_cgm_contact FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE,
    INDEX idx_cgm_contact (contact_id)
) ENGINE=InnoDB;

CREATE TABLE opt_outs (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id      BIGINT UNSIGNED NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    phone_number_id BIGINT UNSIGNED NULL,
    source          VARCHAR(24) NOT NULL,
    keyword         VARCHAR(32) NULL,
    created_at      DATETIME(6) NOT NULL,
    created_by      BIGINT UNSIGNED NULL,
    CONSTRAINT uk_opt_outs UNIQUE (partner_id, phone),
    CONSTRAINT fk_opt_outs_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_opt_outs_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id)
) ENGINE=InnoDB;

CREATE TABLE campaigns (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid              VARCHAR(26)      NOT NULL,
    partner_id       BIGINT UNSIGNED NOT NULL,
    name             VARCHAR(150)  NOT NULL,
    phone_number_id  BIGINT UNSIGNED NOT NULL,
    template_id      BIGINT UNSIGNED NOT NULL,
    audience_type    VARCHAR(16)   NOT NULL,
    audience         JSON          NULL,
    source_file_id   BIGINT UNSIGNED NULL,
    column_mapping   JSON          NULL,
    header_media     JSON          NULL,
    default_country  VARCHAR(2)       NOT NULL DEFAULT 'IN',
    save_contacts    BOOLEAN       NOT NULL DEFAULT FALSE,
    status           VARCHAR(16)   NOT NULL,
    pause_reason     VARCHAR(64)   NULL,
    scheduled_at     DATETIME(6)   NULL,
    started_at       DATETIME(6)   NULL,
    completed_at     DATETIME(6)   NULL,
    total_rows       INT NOT NULL DEFAULT 0,
    valid_count      INT NOT NULL DEFAULT 0,
    invalid_count    INT NOT NULL DEFAULT 0,
    skipped_count    INT NOT NULL DEFAULT 0,
    queued_count     INT NOT NULL DEFAULT 0,
    sent_count       INT NOT NULL DEFAULT 0,
    delivered_count  INT NOT NULL DEFAULT 0,
    read_count       INT NOT NULL DEFAULT 0,
    failed_count     INT NOT NULL DEFAULT 0,
    unit_price       DECIMAL(12,4) NOT NULL DEFAULT 0,
    estimated_cost   DECIMAL(12,4) NOT NULL DEFAULT 0,
    charged_amount   DECIMAL(12,4) NOT NULL DEFAULT 0,
    refunded_amount  DECIMAL(12,4) NOT NULL DEFAULT 0,
    prepared_at      DATETIME(6)   NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_campaigns_uid UNIQUE (uid),
    CONSTRAINT fk_campaigns_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_campaigns_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    CONSTRAINT fk_campaigns_template FOREIGN KEY (template_id) REFERENCES templates (id),
    CONSTRAINT fk_campaigns_file FOREIGN KEY (source_file_id) REFERENCES stored_files (id),
    INDEX idx_campaigns_partner_status (partner_id, status),
    INDEX idx_campaigns_status_scheduled (status, scheduled_at)
) ENGINE=InnoDB;

-- ============================ G. Messaging ============================
CREATE TABLE messages (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid                   VARCHAR(26)      NOT NULL,
    partner_id            BIGINT UNSIGNED NOT NULL,
    phone_number_id       BIGINT UNSIGNED NOT NULL,
    direction             VARCHAR(8)    NOT NULL,
    wa_id                 VARCHAR(20)   NOT NULL,
    country_code          VARCHAR(2)       NULL,
    contact_id            BIGINT UNSIGNED NULL,
    campaign_id           BIGINT UNSIGNED NULL,
    campaign_recipient_id BIGINT UNSIGNED NULL,
    type                  VARCHAR(24)   NOT NULL,
    template_id           BIGINT UNSIGNED NULL,
    template_name         VARCHAR(512)  NULL,
    template_language     VARCHAR(10)   NULL,
    category              VARCHAR(32)   NULL,
    payload               JSON          NULL,
    preview               VARCHAR(1024) NULL,
    context_wamid         VARCHAR(128)  NULL,
    wamid                 VARCHAR(128)  NULL,
    status                VARCHAR(16)   NOT NULL,
    error_code            INT           NULL,
    error_title           VARCHAR(255)  NULL,
    error_message         VARCHAR(255)  NULL,
    price                 DECIMAL(12,4) NOT NULL DEFAULT 0,
    charge_status         VARCHAR(16)   NOT NULL DEFAULT 'NONE',
    billable              BOOLEAN       NULL,
    pricing_category      VARCHAR(32)   NULL,
    pricing_type          VARCHAR(32)   NULL,
    source                VARCHAR(16)   NOT NULL,
    api_key_id            BIGINT UNSIGNED NULL,
    client_reference      VARCHAR(100)  NULL,
    sent_at               DATETIME(6)   NULL,
    delivered_at          DATETIME(6)   NULL,
    read_at               DATETIME(6)   NULL,
    failed_at             DATETIME(6)   NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_messages_uid UNIQUE (uid),
    CONSTRAINT uk_messages_wamid UNIQUE (wamid),
    CONSTRAINT fk_messages_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_messages_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    CONSTRAINT fk_messages_contact FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE SET NULL,
    CONSTRAINT fk_messages_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_messages_template FOREIGN KEY (template_id) REFERENCES templates (id) ON DELETE SET NULL,
    INDEX idx_messages_partner_created (partner_id, created_at),
    INDEX idx_messages_status (status),
    INDEX idx_messages_sender_created (phone_number_id, created_at),
    INDEX idx_messages_conv (phone_number_id, wa_id, created_at),
    INDEX idx_messages_campaign (campaign_id),
    INDEX idx_messages_charge (charge_status, created_at)
) ENGINE=InnoDB;

CREATE TABLE campaign_recipients (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_id   BIGINT UNSIGNED NOT NULL,
    contact_id    BIGINT UNSIGNED NULL,
    row_no        INT          NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    variables     JSON         NULL,
    status        VARCHAR(16)  NOT NULL,
    skip_reason   VARCHAR(24)  NULL,
    message_id    BIGINT UNSIGNED NULL,
    error_message VARCHAR(255) NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_cr_campaign_phone UNIQUE (campaign_id, phone),
    CONSTRAINT fk_cr_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_cr_contact FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE SET NULL,
    CONSTRAINT fk_cr_message FOREIGN KEY (message_id) REFERENCES messages (id),
    INDEX idx_cr_campaign_status (campaign_id, status)
) ENGINE=InnoDB;

CREATE TABLE message_status_events (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id      BIGINT UNSIGNED NULL,
    wamid           VARCHAR(128) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    error_code      INT          NULL,
    error_title     VARCHAR(255) NULL,
    pricing         JSON         NULL,
    event_timestamp DATETIME(6)  NULL,
    raw             JSON         NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_mse_wamid_status UNIQUE (wamid, status),
    CONSTRAINT fk_mse_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    INDEX idx_mse_message (message_id)
) ENGINE=InnoDB;

CREATE TABLE message_queue (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id      BIGINT UNSIGNED NOT NULL,
    phone_number_id BIGINT UNSIGNED NOT NULL,
    priority        INT          NOT NULL DEFAULT 1,
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 5,
    next_attempt_at DATETIME(6)  NOT NULL,
    locked_by       VARCHAR(64)  NULL,
    locked_at       DATETIME(6)  NULL,
    last_error      VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_mq_message UNIQUE (message_id),
    CONSTRAINT fk_mq_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_mq_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    INDEX idx_mq_poll (status, next_attempt_at, priority),
    INDEX idx_mq_sender (phone_number_id, status)
) ENGINE=InnoDB;

CREATE TABLE conversation_windows (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id           BIGINT UNSIGNED NOT NULL,
    phone_number_id      BIGINT UNSIGNED NOT NULL,
    wa_id                VARCHAR(20)  NOT NULL,
    contact_name         VARCHAR(120) NULL,
    last_inbound_at      DATETIME(6)  NULL,
    expires_at           DATETIME(6)  NULL,
    unread_count         INT          NOT NULL DEFAULT 0,
    last_message_preview VARCHAR(255) NULL,
    last_message_at      DATETIME(6)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    CONSTRAINT uk_cw_phone_wa UNIQUE (phone_number_id, wa_id),
    CONSTRAINT fk_cw_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_cw_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    INDEX idx_cw_partner_last (partner_id, last_message_at)
) ENGINE=InnoDB;

CREATE TABLE inbound_media (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id      BIGINT UNSIGNED NOT NULL,
    partner_id      BIGINT UNSIGNED NOT NULL,
    media_id        VARCHAR(64)   NOT NULL,
    mime_type       VARCHAR(127)  NULL,
    sha256          VARCHAR(128)  NULL,
    file_size       BIGINT        NULL,
    caption         VARCHAR(1024) NULL,
    filename        VARCHAR(255)  NULL,
    file_id         BIGINT UNSIGNED NULL,
    download_status VARCHAR(16)   NOT NULL,
    attempts        INT           NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT fk_im_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_im_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_im_file FOREIGN KEY (file_id) REFERENCES stored_files (id),
    INDEX idx_im_status (download_status)
) ENGINE=InnoDB;

CREATE TABLE webhook_inbound_logs (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source          VARCHAR(16)   NOT NULL,
    event_id        VARCHAR(80)   NOT NULL,
    signature_valid BOOLEAN       NOT NULL,
    object_type     VARCHAR(64)   NULL,
    waba_id         VARCHAR(32)   NULL,
    fields          VARCHAR(255)  NULL,
    payload         JSON          NULL,
    status          VARCHAR(16)   NOT NULL,
    attempts        INT           NOT NULL DEFAULT 0,
    error           VARCHAR(1000) NULL,
    received_at     DATETIME(6)   NOT NULL,
    processed_at    DATETIME(6)   NULL,
    CONSTRAINT uk_wil_event UNIQUE (event_id),
    INDEX idx_wil_status (status, received_at)
) ENGINE=InnoDB;

-- ============================ Developer ============================
CREATE TABLE api_keys (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id            BIGINT UNSIGNED NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    api_key               VARCHAR(48)  NOT NULL,
    secret_hash           VARCHAR(64)     NOT NULL,
    secret_last4          VARCHAR(4)      NOT NULL,
    ip_whitelist          JSON         NULL,
    rate_limit_per_minute INT          NULL,
    status                VARCHAR(16)  NOT NULL,
    last_used_at          DATETIME(6)  NULL,
    last_used_ip          VARCHAR(45)  NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_api_keys_key UNIQUE (api_key),
    CONSTRAINT fk_api_keys_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    INDEX idx_api_keys_partner (partner_id)
) ENGINE=InnoDB;

ALTER TABLE messages ADD CONSTRAINT fk_messages_api_key FOREIGN KEY (api_key_id) REFERENCES api_keys (id);

CREATE TABLE partner_webhooks (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id           BIGINT UNSIGNED NOT NULL,
    url                  VARCHAR(500)  NOT NULL,
    secret_enc           VARCHAR(1024) NOT NULL,
    events               JSON          NOT NULL,
    is_active            BOOLEAN       NOT NULL DEFAULT TRUE,
    consecutive_failures INT           NOT NULL DEFAULT 0,
    last_success_at      DATETIME(6)   NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_partner_webhooks_partner UNIQUE (partner_id),
    CONSTRAINT fk_partner_webhooks_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) ENGINE=InnoDB;

CREATE TABLE webhook_delivery_logs (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_webhook_id BIGINT UNSIGNED NOT NULL,
    partner_id         BIGINT UNSIGNED NOT NULL,
    event_id           VARCHAR(26)      NOT NULL,
    event_type         VARCHAR(40)   NOT NULL,
    payload            JSON          NOT NULL,
    attempt            INT           NOT NULL DEFAULT 0,
    status             VARCHAR(16)   NOT NULL,
    response_code      INT           NULL,
    response_body      VARCHAR(2000) NULL,
    duration_ms        INT           NULL,
    next_retry_at      DATETIME(6)   NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    CONSTRAINT fk_wdl_webhook FOREIGN KEY (partner_webhook_id) REFERENCES partner_webhooks (id) ON DELETE CASCADE,
    CONSTRAINT fk_wdl_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    INDEX idx_wdl_status_retry (status, next_retry_at),
    INDEX idx_wdl_partner_created (partner_id, created_at)
) ENGINE=InnoDB;

-- ============================ H. Platform ============================
CREATE TABLE notifications (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NULL,
    partner_id BIGINT UNSIGNED NULL,
    audience   VARCHAR(16)   NOT NULL,
    type       VARCHAR(48)   NOT NULL,
    severity   VARCHAR(16)   NOT NULL,
    title      VARCHAR(150)  NOT NULL,
    body       VARCHAR(1000) NULL,
    link       VARCHAR(255)  NULL,
    is_read    BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at    DATETIME(6)   NULL,
    created_at DATETIME(6)   NOT NULL,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    INDEX idx_notif_user_read (user_id, is_read, created_at),
    INDEX idx_notif_partner_read (partner_id, is_read, created_at),
    INDEX idx_notif_audience (audience, created_at)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id        BIGINT UNSIGNED NULL,
    actor_type           VARCHAR(16)  NOT NULL,
    impersonator_user_id BIGINT UNSIGNED NULL,
    partner_id           BIGINT UNSIGNED NULL,
    action               VARCHAR(64)  NOT NULL,
    entity_type          VARCHAR(48)  NULL,
    entity_id            VARCHAR(64)  NULL,
    old_value            JSON         NULL,
    new_value            JSON         NULL,
    ip                   VARCHAR(45)  NULL,
    user_agent           VARCHAR(255) NULL,
    created_at           DATETIME(6)  NOT NULL,
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_actor (actor_user_id, created_at),
    INDEX idx_audit_partner (partner_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE meta_pricing_analytics (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    whatsapp_account_id BIGINT UNSIGNED NOT NULL,
    date                DATE          NOT NULL,
    phone_number        VARCHAR(32)   NOT NULL,
    country             VARCHAR(2)    NOT NULL,
    pricing_category    VARCHAR(32)   NOT NULL,
    pricing_type        VARCHAR(32)   NOT NULL,
    volume              INT           NOT NULL DEFAULT 0,
    cost                DECIMAL(12,4) NOT NULL DEFAULT 0,
    currency            VARCHAR(3)       NOT NULL DEFAULT 'INR',
    synced_at           DATETIME(6)   NOT NULL,
    CONSTRAINT uk_mpa UNIQUE (whatsapp_account_id, date, phone_number, country, pricing_category, pricing_type),
    CONSTRAINT fk_mpa_waba FOREIGN KEY (whatsapp_account_id) REFERENCES whatsapp_accounts (id)
) ENGINE=InnoDB;
