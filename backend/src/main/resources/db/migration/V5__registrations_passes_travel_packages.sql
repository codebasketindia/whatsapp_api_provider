-- Public registration links + free pass (§4.21), travel packages & bookings (§4.22), AI provider choice (v1.4)

CREATE TABLE travel_packages (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id   BIGINT UNSIGNED NOT NULL,
    code         VARCHAR(40)   NOT NULL,
    name         VARCHAR(160)  NOT NULL,
    destination  VARCHAR(160)  NULL,
    duration     VARCHAR(60)   NULL,
    price        DECIMAL(12,2) NULL,
    price_note   VARCHAR(255)  NULL,
    start_date   DATE          NULL,
    end_date     DATE          NULL,
    seats_total  INT           NULL,
    inclusions   TEXT          NULL,
    exclusions   TEXT          NULL,
    itinerary    TEXT          NULL,
    details      TEXT          NULL,
    file_id      BIGINT UNSIGNED NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    sort_order   INT           NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_travel_packages_code UNIQUE (partner_id, code),
    CONSTRAINT fk_tp_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_file FOREIGN KEY (file_id) REFERENCES stored_files (id)
) ENGINE=InnoDB;

CREATE TABLE travel_bookings (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid           VARCHAR(26)   NOT NULL,
    partner_id    BIGINT UNSIGNED NOT NULL,
    package_id    BIGINT UNSIGNED NOT NULL,
    phone         VARCHAR(20)   NOT NULL,
    customer_name VARCHAR(120)  NULL,
    travellers    INT           NOT NULL DEFAULT 1,
    amount        DECIMAL(12,2) NULL,
    paid_amount   DECIMAL(12,2) NULL,
    status        VARCHAR(16)   NOT NULL,
    travel_date   DATE          NULL,
    notes         VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_travel_bookings_uid UNIQUE (uid),
    CONSTRAINT fk_tb_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_package FOREIGN KEY (package_id) REFERENCES travel_packages (id),
    INDEX idx_tb_partner_phone (partner_id, phone),
    INDEX idx_tb_package (package_id, status)
) ENGINE=InnoDB;

CREATE TABLE registration_forms (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid                 VARCHAR(26)   NOT NULL,
    partner_id          BIGINT UNSIGNED NOT NULL,
    slug                VARCHAR(64)   NOT NULL,
    title               VARCHAR(160)  NOT NULL,
    description         VARCHAR(2000) NULL,
    phone_number_id     BIGINT UNSIGNED NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    closes_at           DATETIME(6)   NULL,
    max_registrations   INT           NULL,
    consent_text        VARCHAR(500)  NOT NULL,
    success_message     VARCHAR(1000) NULL,
    collect_photo       BOOLEAN       NOT NULL DEFAULT FALSE,
    pass_enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    pass_design         VARCHAR(40)   NULL,
    pass_design_file_id BIGINT UNSIGNED NULL,
    pass_layout         JSON          NULL,
    pass_prefix         VARCHAR(10)   NOT NULL DEFAULT 'PASS',
    pass_template_id    BIGINT UNSIGNED NULL,
    pass_template_body  JSON          NULL,
    pass_caption        VARCHAR(1000) NULL,
    contact_group_id    BIGINT UNSIGNED NULL,
    contact_tags        JSON          NULL,
    follow_ups          JSON          NULL,
    registrations_count INT           NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_registration_forms_uid UNIQUE (uid),
    CONSTRAINT uk_registration_forms_slug UNIQUE (slug),
    CONSTRAINT fk_rf_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    CONSTRAINT fk_rf_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    CONSTRAINT fk_rf_design FOREIGN KEY (pass_design_file_id) REFERENCES stored_files (id),
    CONSTRAINT fk_rf_template FOREIGN KEY (pass_template_id) REFERENCES templates (id),
    INDEX idx_rf_partner (partner_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE registrations (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid             VARCHAR(26)   NOT NULL,
    form_id         BIGINT UNSIGNED NOT NULL,
    partner_id      BIGINT UNSIGNED NOT NULL,
    pass_no         VARCHAR(24)   NOT NULL,
    name            VARCHAR(120)  NOT NULL,
    phone           VARCHAR(20)   NOT NULL,
    photo_file_id   BIGINT UNSIGNED NULL,
    pass_file_id    BIGINT UNSIGNED NULL,
    pass_status     VARCHAR(20)   NOT NULL,
    pass_message_id BIGINT UNSIGNED NULL,
    pass_error      VARCHAR(500)  NULL,
    claim_code      VARCHAR(12)   NOT NULL,
    contact_id      BIGINT UNSIGNED NULL,
    consent_at      DATETIME(6)   NOT NULL,
    ip              VARCHAR(45)   NULL,
    user_agent      VARCHAR(255)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT uk_registrations_uid UNIQUE (uid),
    CONSTRAINT uk_registrations_form_phone UNIQUE (form_id, phone),
    CONSTRAINT uk_registrations_claim UNIQUE (claim_code),
    CONSTRAINT fk_reg_form FOREIGN KEY (form_id) REFERENCES registration_forms (id) ON DELETE CASCADE,
    CONSTRAINT fk_reg_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    CONSTRAINT fk_reg_photo FOREIGN KEY (photo_file_id) REFERENCES stored_files (id),
    CONSTRAINT fk_reg_pass FOREIGN KEY (pass_file_id) REFERENCES stored_files (id),
    INDEX idx_reg_partner_phone (partner_id, phone),
    INDEX idx_reg_form_created (form_id, created_at)
) ENGINE=InnoDB;

ALTER TABLE scheduled_messages
    ADD COLUMN registration_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_sm_registration FOREIGN KEY (registration_id) REFERENCES registrations (id) ON DELETE SET NULL;

ALTER TABLE ai_assistants
    ADD COLUMN support_number    VARCHAR(20)  NULL,
    ADD COLUMN allowed_topics    VARCHAR(500) NULL,
    ADD COLUMN pause_on_fallback BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN use_packages      BOOLEAN      NOT NULL DEFAULT TRUE;

INSERT INTO settings (group_code, setting_key, value, value_type, is_encrypted, created_at, updated_at) VALUES
 ('AI', 'provider',                'OPENAI',     'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'openai_api_key',          NULL,         'STRING', TRUE,  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'openai_model',            'gpt-5-mini', 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'openai_reasoning_effort', 'low',        'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
