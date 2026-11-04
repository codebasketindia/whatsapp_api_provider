-- Paid registrations (v1.5): partner's own Razorpay account, registration fee, pass only after payment

CREATE TABLE partner_payment_accounts (
    partner_id         BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    provider           VARCHAR(16)  NOT NULL DEFAULT 'RAZORPAY',
    key_id             VARCHAR(64)  NULL,
    key_secret_enc     VARCHAR(512) NULL,
    webhook_secret_enc VARCHAR(512) NULL,
    enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at        DATETIME(6)  NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ppa_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE
) ENGINE=InnoDB;

ALTER TABLE registration_forms
    ADD COLUMN fee_amount DECIMAL(12,2) NULL,
    ADD COLUMN fee_label  VARCHAR(120)  NULL;

ALTER TABLE registrations
    MODIFY COLUMN pass_no VARCHAR(24) NULL,
    ADD COLUMN payment_status      VARCHAR(16)   NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN amount              DECIMAL(12,2) NULL,
    ADD COLUMN razorpay_order_id   VARCHAR(40)   NULL,
    ADD COLUMN razorpay_payment_id VARCHAR(40)   NULL,
    ADD COLUMN payment_method      VARCHAR(24)   NULL,
    ADD COLUMN paid_at             DATETIME(6)   NULL,
    ADD CONSTRAINT uk_registrations_rzp_order UNIQUE (razorpay_order_id),
    ADD CONSTRAINT uk_registrations_rzp_payment UNIQUE (razorpay_payment_id),
    ADD INDEX idx_reg_payment_pending (payment_status, created_at);
