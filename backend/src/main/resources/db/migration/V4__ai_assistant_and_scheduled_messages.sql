-- AI Assistant (contract §4.19) and scheduled messages (§4.20)

CREATE TABLE ai_assistants (
    partner_id                   BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    enabled                      BOOLEAN       NOT NULL DEFAULT FALSE,
    sender_ids                   JSON          NULL,
    business_name                VARCHAR(190)  NULL,
    instructions                 TEXT          NULL,
    language                     VARCHAR(16)   NOT NULL DEFAULT 'AUTO',
    tone                         VARCHAR(16)   NOT NULL DEFAULT 'FRIENDLY',
    fallback_message             VARCHAR(1000) NULL,
    handover_keywords            JSON          NULL,
    max_replies_per_chat_per_day INT           NOT NULL DEFAULT 20,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_assistants_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE ai_knowledge_items (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id  BIGINT UNSIGNED NOT NULL,
    title       VARCHAR(120) NOT NULL,
    content     TEXT         NOT NULL,
    file_id     BIGINT UNSIGNED NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_knowledge_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_knowledge_file FOREIGN KEY (file_id) REFERENCES stored_files (id),
    INDEX idx_ai_knowledge_partner (partner_id, active, sort_order)
) ENGINE=InnoDB;

CREATE TABLE ai_usage_logs (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    partner_id         BIGINT UNSIGNED NOT NULL,
    phone_number_id    BIGINT UNSIGNED NOT NULL,
    wa_id              VARCHAR(20)   NOT NULL,
    inbound_message_id BIGINT UNSIGNED NOT NULL,
    reply_message_id   BIGINT UNSIGNED NULL,
    model              VARCHAR(64)   NULL,
    outcome            VARCHAR(16)   NOT NULL,
    reason             VARCHAR(255)  NULL,
    inbound_preview    VARCHAR(255)  NULL,
    reply_preview      VARCHAR(500)  NULL,
    input_tokens       INT           NOT NULL DEFAULT 0,
    output_tokens      INT           NOT NULL DEFAULT 0,
    cache_read_tokens  INT           NOT NULL DEFAULT 0,
    cache_write_tokens INT           NOT NULL DEFAULT 0,
    charged            DECIMAL(12,4) NOT NULL DEFAULT 0,
    wallet_transaction_id BIGINT UNSIGNED NULL,
    created_at         DATETIME(6)   NOT NULL,
    CONSTRAINT uk_ai_usage_inbound UNIQUE (inbound_message_id),
    CONSTRAINT fk_ai_usage_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_ai_usage_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    INDEX idx_ai_usage_partner_created (partner_id, created_at),
    INDEX idx_ai_usage_chat (phone_number_id, wa_id, created_at)
) ENGINE=InnoDB;

ALTER TABLE conversation_windows
    ADD COLUMN ai_paused   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN needs_human BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE scheduled_messages (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uid             VARCHAR(26)   NOT NULL,
    partner_id      BIGINT UNSIGNED NOT NULL,
    phone_number_id BIGINT UNSIGNED NOT NULL,
    wa_id           VARCHAR(20)   NOT NULL,
    type            VARCHAR(24)   NOT NULL,
    request         JSON          NOT NULL,
    preview         VARCHAR(500)  NULL,
    scheduled_at    DATETIME(6)   NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    message_id      BIGINT UNSIGNED NULL,
    error           VARCHAR(500)  NULL,
    attempted_at    DATETIME(6)   NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL, updated_by BIGINT UNSIGNED NULL, version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_scheduled_messages_uid UNIQUE (uid),
    CONSTRAINT fk_sm_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_sm_phone FOREIGN KEY (phone_number_id) REFERENCES phone_numbers (id),
    CONSTRAINT fk_sm_message FOREIGN KEY (message_id) REFERENCES messages (id),
    INDEX idx_sm_due (status, scheduled_at),
    INDEX idx_sm_partner (partner_id, scheduled_at)
) ENGINE=InnoDB;

INSERT INTO settings (group_code, setting_key, value, value_type, is_encrypted, created_at, updated_at) VALUES
 ('AI', 'enabled',                      'false',           'BOOLEAN', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'anthropic_api_key',            NULL,              'STRING',  TRUE,  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'model',                        'claude-haiku-5-5','STRING',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'fee_per_reply',                '0.10',            'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'max_output_tokens',            '400',             'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'history_messages',             '6',               'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('AI', 'max_replies_per_chat_per_day', '20',              'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
