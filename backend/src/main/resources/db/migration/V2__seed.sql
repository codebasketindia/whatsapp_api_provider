-- Seed data. The super admin user is created at startup by SuperAdminSeeder from env variables
-- (no password is stored in SQL). Prices are NOT seeded: the default rate plan has no items, so
-- sending is blocked with RATE_NOT_CONFIGURED until an admin enters prices.

INSERT INTO permissions (code, module, description) VALUES
 ('DASHBOARD_VIEW',     'DASHBOARD', 'View admin dashboard'),
 ('PARTNER_VIEW',       'PARTNER',   'View partners'),
 ('PARTNER_MANAGE',     'PARTNER',   'Create, edit, suspend partners'),
 ('LOGIN_AS_PARTNER',   'PARTNER',   'Log in as a partner (audited)'),
 ('WALLET_VIEW',        'WALLET',    'View partner wallets and ledgers'),
 ('WALLET_ADJUST',      'WALLET',    'Manual wallet credit / debit'),
 ('TOPUP_VIEW',         'WALLET',    'View top-up requests'),
 ('TOPUP_APPROVE',      'WALLET',    'Approve / reject top-up requests'),
 ('PAYMENT_VIEW',       'PAYMENT',   'View gateway payments and invoices'),
 ('PAYMENT_RECONCILE',  'PAYMENT',   'Reconcile gateway payments'),
 ('PRICING_MANAGE',     'PRICING',   'Manage credit packs and rate plans'),
 ('WHATSAPP_VIEW',      'WHATSAPP',  'View WhatsApp accounts and senders'),
 ('WHATSAPP_MANAGE',    'WHATSAPP',  'Manage WhatsApp accounts and senders'),
 ('TEMPLATE_VIEW',      'TEMPLATE',  'View all templates'),
 ('TEMPLATE_REVIEW',    'TEMPLATE',  'Review, create and sync templates'),
 ('MESSAGE_LOG_VIEW',   'MESSAGE',   'View message logs'),
 ('CAMPAIGN_VIEW',      'CAMPAIGN',  'View all campaigns'),
 ('CAMPAIGN_MANAGE',    'CAMPAIGN',  'Pause / cancel campaigns'),
 ('REPORT_VIEW',        'REPORT',    'View and export reports'),
 ('SETTINGS_MANAGE',    'SETTINGS',  'Manage platform settings'),
 ('STAFF_MANAGE',       'STAFF',     'Manage staff, roles and permissions'),
 ('AUDIT_VIEW',         'AUDIT',     'View audit log');

INSERT INTO roles (code, name, user_type, is_system, description, created_at, updated_at) VALUES
 ('SUPER_ADMIN', 'Super Admin', 'ADMIN',   TRUE,  'Full access to everything', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('API_PARTNER', 'API Partner', 'PARTNER', TRUE,  'Customer account',          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SUPPORT',     'Support',     'ADMIN',   FALSE, 'Read-only support staff',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('FINANCE',     'Finance',     'ADMIN',   FALSE, 'Wallets, top-ups, payments, reports', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
 WHERE r.code = 'SUPPORT' AND p.code IN ('DASHBOARD_VIEW','PARTNER_VIEW','WALLET_VIEW','TOPUP_VIEW','PAYMENT_VIEW',
                                         'WHATSAPP_VIEW','TEMPLATE_VIEW','MESSAGE_LOG_VIEW','CAMPAIGN_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
 WHERE r.code = 'FINANCE' AND p.code IN ('DASHBOARD_VIEW','PARTNER_VIEW','WALLET_VIEW','WALLET_ADJUST','TOPUP_VIEW',
                                         'TOPUP_APPROVE','PAYMENT_VIEW','PAYMENT_RECONCILE','REPORT_VIEW');

INSERT INTO rate_plans (name, description, is_default, is_custom, currency, session_message_fee,
                        platform_fee_per_message, platform_fee_monthly_per_number, status, created_at, updated_at)
VALUES ('India Standard', 'Default plan for India (+91). Enter prices before partners can send.', TRUE, FALSE, 'INR',
        0, 0, 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO credit_packs (name, description, price, bonus_type, bonus_value, is_popular, is_active, sort_order, created_at, updated_at) VALUES
 ('Starter', 'Pay ₹1,000 and get 10% extra credit',  1000.0000,  'PERCENT', 10.0000, FALSE, FALSE, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('Growth',  'Pay ₹5,000 and get 12% extra credit',  5000.0000,  'PERCENT', 12.0000, TRUE,  FALSE, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('Pro',     'Pay ₹10,000 and get 15% extra credit', 10000.0000, 'PERCENT', 15.0000, FALSE, FALSE, 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO meta_settings (id, graph_api_version, billing_mode, created_at, updated_at)
VALUES (1, 'v25.0', 'TECH_PROVIDER', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO number_sequences (name, prefix, next_value, updated_at) VALUES
 ('PARTNER_CODE', 'CBP', 1, UTC_TIMESTAMP(6)),
 ('TOPUP',        'TR',  1, UTC_TIMESTAMP(6));

INSERT INTO settings (group_code, setting_key, value, value_type, is_encrypted, created_at, updated_at) VALUES
 ('COMPANY', 'name',          'CodeBasket IT Services & Co', 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'legal_name',    'CodeBasket IT Services & Co', 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'address',       NULL, 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'gstin',         NULL, 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'state_code',    NULL, 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'email',         NULL, 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'phone',         NULL, 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'support_email', NULL, 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'support_phone', NULL, 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('COMPANY', 'logo_file_uid', NULL, 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('GST', 'enabled',        'false', 'BOOLEAN', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('GST', 'percent',        '18',    'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('GST', 'invoice_prefix', 'CB',    'STRING',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('GST', 'sac_code',       '998319','STRING',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('GST', 'invoice_manual_topups', 'true', 'BOOLEAN', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('PAYMENT', 'active_gateway',          'NONE', 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('PAYMENT', 'razorpay_key_id',         NULL,   'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('PAYMENT', 'razorpay_key_secret',     NULL,   'STRING', TRUE,  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('PAYMENT', 'razorpay_webhook_secret', NULL,   'STRING', TRUE,  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('PAYMENT', 'min_amount',              '100',    'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('PAYMENT', 'max_amount',              '200000', 'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('SMTP', 'host',       NULL,  'STRING',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SMTP', 'port',       '587', 'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SMTP', 'username',   NULL,  'STRING',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SMTP', 'password',   NULL,  'STRING',  TRUE,  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SMTP', 'from_email', NULL,  'STRING',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SMTP', 'from_name',  'CB WhatsApp Gateway', 'STRING', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SMTP', 'starttls',   'true', 'BOOLEAN', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('CHARGING', 'charge_on',                     'DELIVERED', 'STRING',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('CHARGING', 'charge_free_utility_in_window', 'false',     'BOOLEAN', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('CHARGING', 'hold_timeout_hours',            '72',        'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('CHARGING', 'platform_fee_grace_days',       '7',         'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('WALLET', 'default_low_balance_threshold', '500', 'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('RATE_LIMIT', 'public_api_per_minute', '600', 'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('RATE_LIMIT', 'login_per_minute',      '10',  'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('SECURITY', 'admin_2fa_required', 'false', 'BOOLEAN', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SECURITY', 'max_login_attempts', '5',     'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('SECURITY', 'lockout_minutes',    '15',    'NUMBER',  FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('STORAGE', 'max_upload_mb', '2', 'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

 ('RETENTION', 'webhook_log_days',     '90',  'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('RETENTION', 'message_content_days', '180', 'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('RETENTION', 'media_days',           '90',  'NUMBER', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
