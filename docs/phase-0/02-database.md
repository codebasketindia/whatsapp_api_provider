# CB WhatsApp Gateway — Phase 0 · 02 Database

Owner: [LEAD ARCHITECT] · MySQL 8.0, InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`, schema `cbwa`

## Conventions
- PK `id BIGINT UNSIGNED AUTO_INCREMENT`. Public identifiers exposed to partners use `uid CHAR(26)` (ULID) where noted — sequential ids are never exposed in the public API.
- Times `DATETIME(6)` in **UTC** (JDBC `connectionTimeZone=UTC`, `hibernate.jdbc.time_zone=UTC`); UI converts to Asia/Kolkata.
- Money `DECIMAL(12,4)`; currency `CHAR(3)` default `INR`.
- Enums stored as `VARCHAR(32)` (`@Enumerated(STRING)`), validated in Java.
- Audited base columns (marked **[A]**): `created_at, updated_at, created_by BIGINT NULL, updated_by BIGINT NULL, version INT NOT NULL DEFAULT 0`.
- JSON columns use MySQL `JSON`. Encrypted columns end with `_enc` (`VARCHAR(1024)` / `TEXT`, AES-GCM `v1:` format).
- FK naming `fk_<table>_<ref>`, index `idx_<table>_<cols>`, unique `uk_<table>_<cols>`.

## ER diagram (text)

```
roles 1─* role_permissions *─1 permissions
roles 1─* users 1─* refresh_tokens
             users 1─* user_otps
users 1─1 partners 1─1 partner_settings
             partners 1─1 wallets 1─* wallet_transactions
             partners 1─* topup_requests ─0..1 wallet_transactions
             partners 1─* payment_orders 1─* payment_gateway_logs ; payment_orders 0..1─1 invoices
             partners *─1 rate_plans 1─* rate_plan_items
credit_packs 1─* payment_orders
meta_settings (singleton)
whatsapp_accounts(WABA) 1─* phone_numbers 1─* partner_phone_numbers *─1 partners
             whatsapp_accounts *─0..1 partners (owner when PARTNER_OWN)
phone_numbers 1─1 business_profiles ; 1─1 conversational_components ; 1─* qr_codes ; 1─* blocked_users
whatsapp_accounts 1─* templates 1─* template_components ; templates 1─* template_analytics
partners 1─* contacts *─* contact_groups (contact_group_members) ; partners 1─* opt_outs
partners 1─* campaigns 1─* campaign_recipients 0..1─1 messages
partners 1─* messages 1─* message_status_events ; messages 1─0..1 message_queue ; messages 1─* inbound_media
phone_numbers 1─* conversation_windows (per wa_id)
partners 1─* api_keys ; partners 1─0..1 partner_webhooks 1─* webhook_delivery_logs
webhook_inbound_logs (Meta & Razorpay raw events)
notifications *─1 users / partners ; audit_logs *─1 users ; settings (key-value)
stored_files (uploads) ; number_sequences ; meta_pricing_analytics ; platform_fee_charges
```

---

## A. Identity & access

### `roles` [A]
| col | type | notes |
|---|---|---|
| id | BIGINT PK | |
| code | VARCHAR(50) UNIQUE | `SUPER_ADMIN`, `API_PARTNER`, custom staff roles e.g. `FINANCE` |
| name | VARCHAR(100) | |
| user_type | VARCHAR(16) | `ADMIN` / `PARTNER` |
| is_system | BOOLEAN | system roles cannot be edited/deleted |
| description | VARCHAR(255) | |

### `permissions`
| id PK | code VARCHAR(64) UNIQUE | module VARCHAR(32) | description VARCHAR(255) |

Seeded codes: `DASHBOARD_VIEW, PARTNER_VIEW, PARTNER_MANAGE, LOGIN_AS_PARTNER, WALLET_VIEW, WALLET_ADJUST, TOPUP_VIEW, TOPUP_APPROVE, PAYMENT_VIEW, PAYMENT_RECONCILE, PRICING_MANAGE, WHATSAPP_VIEW, WHATSAPP_MANAGE, TEMPLATE_VIEW, TEMPLATE_REVIEW, MESSAGE_LOG_VIEW, CAMPAIGN_VIEW, CAMPAIGN_MANAGE, REPORT_VIEW, SETTINGS_MANAGE, STAFF_MANAGE, AUDIT_VIEW`.

### `role_permissions`
`role_id FK, permission_id FK, PK(role_id, permission_id)`

### `users` [A]
| col | type | notes |
|---|---|---|
| id | BIGINT PK | |
| uid | CHAR(26) UNIQUE | |
| user_type | VARCHAR(16) | `ADMIN` / `PARTNER` |
| role_id | FK roles | |
| name | VARCHAR(120) | |
| email | VARCHAR(190) UNIQUE | login id, stored lower-case |
| mobile | VARCHAR(20) | E.164 |
| password_hash | VARCHAR(100) | BCrypt(12) |
| status | VARCHAR(16) | `ACTIVE`, `DISABLED` |
| two_factor_enabled | BOOLEAN | admin users only |
| failed_login_attempts | INT | |
| locked_until | DATETIME(6) NULL | |
| must_change_password | BOOLEAN | first login / admin reset |
| password_changed_at | DATETIME(6) | |
| last_login_at / last_login_ip | DATETIME(6) / VARCHAR(45) | |
| theme | VARCHAR(8) | `LIGHT/DARK/SYSTEM` |

### `refresh_tokens`
| id PK | user_id FK | token_hash CHAR(64) UNIQUE | family_id CHAR(26) | issued_at | expires_at | revoked_at NULL | replaced_by_id NULL | impersonator_user_id NULL | ip VARCHAR(45) | user_agent VARCHAR(255) |
Index `idx_refresh_tokens_user (user_id)`, `idx_refresh_tokens_family (family_id)`.

### `user_otps`
| id PK | user_id FK | challenge_id CHAR(26) UNIQUE | purpose (`PASSWORD_RESET`,`LOGIN_2FA`) | otp_hash CHAR(64) | attempts INT | expires_at | consumed_at NULL | created_at |

---

## B. Partners

### `partners` [A]
| col | type | notes |
|---|---|---|
| id | BIGINT PK | |
| uid | CHAR(26) UNIQUE | |
| partner_code | VARCHAR(20) UNIQUE | `CBP000123` from `number_sequences` |
| user_id | FK users UNIQUE | primary login |
| business_name | VARCHAR(190) | |
| contact_name | VARCHAR(120) | |
| email, mobile | VARCHAR(190), VARCHAR(20) | |
| gstin | VARCHAR(15) NULL | regex validated |
| pan | VARCHAR(10) NULL | |
| address_line1, address_line2, city | VARCHAR(190)/(190)/(100) | |
| state, state_code | VARCHAR(100), CHAR(2) | GST place of supply |
| pincode | VARCHAR(10) | |
| country | CHAR(2) default `IN` | |
| website | VARCHAR(255) | |
| status | VARCHAR(16) | `ACTIVE`, `SUSPENDED` |
| suspend_reason | VARCHAR(255) | |
| kyc_verified | BOOLEAN | admin ticks after checking |
| rate_plan_id | FK rate_plans | |
| allow_own_number | BOOLEAN | D6 |
Indexes: `idx_partners_status`, `idx_partners_business_name`.

### `partner_settings` [A]
| partner_id PK/FK | low_balance_threshold DECIMAL(12,4) NULL (null → admin default) | low_balance_alerted_at DATETIME NULL | opt_out_keywords JSON (default `["STOP","UNSUBSCRIBE"]`) | opt_in_keywords JSON (default `["START"]`) | opt_out_auto_reply VARCHAR(1024) NULL | default_phone_number_id FK NULL | notify_email BOOLEAN | timezone VARCHAR(40) default `Asia/Kolkata` |

---

## C. Wallet, top-ups, payments

### `wallets` [A]
| id PK | partner_id FK UNIQUE | balance DECIMAL(12,4) ≥0 | held_amount DECIMAL(12,4) ≥0 | currency CHAR(3) |
`CHECK (balance >= 0)`, `CHECK (held_amount >= 0)`.

### `wallet_transactions` (immutable, no updates)
| col | type | notes |
|---|---|---|
| id | BIGINT PK | |
| uid | CHAR(26) UNIQUE | shown in passbook |
| wallet_id | FK | |
| partner_id | FK | denormalised for reports |
| type | VARCHAR(16) | `CREDIT, DEBIT, HOLD, RELEASE, REFUND` |
| amount | DECIMAL(12,4) > 0 | |
| balance_before, balance_after | DECIMAL(12,4) | available balance |
| held_before, held_after | DECIMAL(12,4) | |
| reference_type | VARCHAR(32) | `TOPUP_REQUEST, PAYMENT_ORDER, BONUS, ADMIN_ADJUSTMENT, MESSAGE, PLATFORM_FEE, CAMPAIGN` |
| reference_id | VARCHAR(64) | id/uid of referenced row |
| remarks | VARCHAR(500) | |
| created_by | BIGINT NULL | null = system |
| impersonator_user_id | BIGINT NULL | |
| created_at | DATETIME(6) | |
Indexes: `idx_wtx_wallet_created (wallet_id, created_at)`, `idx_wtx_ref (reference_type, reference_id)`, `idx_wtx_partner_type_created (partner_id, type, created_at)`.

### `topup_requests` [A]
| id PK | request_no VARCHAR(20) UNIQUE (`TR2610000123`) | partner_id FK | amount DECIMAL(12,4) | payment_mode (`UPI,NEFT,IMPS,CASH,OTHER`) | utr VARCHAR(64) **UNIQUE** (normalised upper/trim; CASH gets generated ref) | payment_date DATE | screenshot_file_id FK stored_files | note VARCHAR(500) | status (`PENDING,APPROVED,REJECTED`) | reject_reason VARCHAR(500) | wallet_transaction_id FK NULL | invoice_id FK NULL | reviewed_by FK users NULL | reviewed_at NULL |
Index `idx_topup_status_created (status, created_at)`, `idx_topup_partner (partner_id, created_at)`.

### `credit_packs` [A]
| id PK | name VARCHAR(100) | description VARCHAR(255) | price DECIMAL(12,4) | bonus_type (`FLAT`,`PERCENT`) | bonus_value DECIMAL(12,4) | is_popular BOOLEAN | is_active BOOLEAN | sort_order INT |

### `payment_orders` [A]
| col | type | notes |
|---|---|---|
| id, uid | | uid = Razorpay `receipt` |
| partner_id | FK | |
| gateway | VARCHAR(16) | `RAZORPAY` (later `CASHFREE`,`PHONEPE`) |
| gateway_order_id | VARCHAR(64) UNIQUE | |
| gateway_payment_id | VARCHAR(64) **UNIQUE** NULL | idempotency key |
| credit_pack_id | FK NULL | |
| base_amount | DECIMAL(12,4) | wallet value purchased (pack price or custom) |
| bonus_amount | DECIMAL(12,4) | |
| gst_percent, gst_amount | DECIMAL(5,2), DECIMAL(12,4) | |
| total_amount | DECIMAL(12,4) | charged to partner = base + GST |
| credit_amount | DECIMAL(12,4) | base + bonus → credited to wallet |
| currency | CHAR(3) | |
| status | VARCHAR(16) | `CREATED, ATTEMPTED, PAID, FAILED, EXPIRED` |
| method | VARCHAR(32) | upi/card/netbanking (masked info only) |
| failure_reason | VARCHAR(255) | |
| verified_via | VARCHAR(16) | `CHECKOUT, WEBHOOK, RECONCILE` (first one wins) |
| paid_at | DATETIME(6) | |
| wallet_transaction_id, invoice_id | FK NULL | |
| raw_payload | JSON | last full gateway payment entity |
Index `idx_po_partner_created`, `idx_po_status_created`.

### `payment_gateway_logs`
| id PK | payment_order_id FK NULL | gateway | direction (`REQUEST`,`RESPONSE`,`CALLBACK`,`WEBHOOK`) | event VARCHAR(64) | http_status INT | payload JSON (secrets stripped) | created_at |

### `invoices` [A]
| id PK | invoice_no VARCHAR(32) UNIQUE (`CB/2026-27/000123`) | partner_id FK | payment_order_id FK NULL | topup_request_id FK NULL | invoice_date DATE | billing_name, gstin, billing_address TEXT, place_of_supply CHAR(2) | taxable_amount | cgst_amount | sgst_amount | igst_amount | total_amount | file_id FK stored_files |

### `number_sequences`
| name VARCHAR(40) PK (`INVOICE_FY2026-27`, `PARTNER_CODE`, `TOPUP`) | prefix VARCHAR(20) | next_value BIGINT | updated_at | — incremented with `SELECT … FOR UPDATE`.

### `platform_fee_charges`
| id PK | partner_id FK | phone_number_id FK | period CHAR(7) (`2026-10`) | amount | wallet_transaction_id FK NULL | status (`CHARGED`,`SKIPPED_INSUFFICIENT`) | created_at | **UNIQUE(phone_number_id, period)** |

---

## D. Pricing

### `rate_plans` [A]
| id PK | name VARCHAR(100) | description | is_default BOOLEAN (exactly one) | is_custom BOOLEAN | partner_id FK NULL (custom plan owner) | currency | session_message_fee DECIMAL(12,4) | platform_fee_per_message DECIMAL(12,4) | platform_fee_monthly_per_number DECIMAL(12,4) | status (`ACTIVE`,`ARCHIVED`) |

### `rate_plan_items` [A]
| id PK | rate_plan_id FK | country_code CHAR(2) or `*` (fallback for all other countries) | category (`MARKETING, UTILITY, AUTHENTICATION, AUTHENTICATION_INTERNATIONAL, SERVICE`) | price DECIMAL(12,4) | **UNIQUE(rate_plan_id, country_code, category)** |

Price resolution: exact country → `*` → none ⇒ `RATE_NOT_CONFIGURED` error (nothing sent).
- PLATFORM_SENDER or SOLUTION_PARTNER mode: category price (+0 platform fee).
- PARTNER_OWN_SENDER in TECH_PROVIDER mode: `platform_fee_per_message` only.

---

## E. Meta / WhatsApp

### `meta_settings` [A] (singleton id=1)
| app_id VARCHAR(32) | app_secret_enc | embedded_signup_config_id VARCHAR(32) | solution_id VARCHAR(32) NULL | business_portfolio_id VARCHAR(32) | graph_api_version VARCHAR(8) (`v25.0`) | webhook_verify_token_enc | billing_mode (`TECH_PROVIDER`,`SOLUTION_PARTNER`) | extended_credit_line_id VARCHAR(32) NULL | last_test_at, last_test_ok |

### `whatsapp_accounts` (WABA) [A]
| col | type | notes |
|---|---|---|
| id | PK | |
| waba_id | VARCHAR(32) UNIQUE | |
| owner_type | VARCHAR(16) | `PLATFORM` / `PARTNER` |
| partner_id | FK NULL | owner when PARTNER |
| business_id | VARCHAR(32) | partner's business portfolio |
| name, currency, timezone_id | | |
| message_template_namespace | VARCHAR(64) | |
| account_review_status | VARCHAR(16) | `PENDING, APPROVED, REJECTED` |
| business_verification_status | VARCHAR(32) | |
| payment_method_attached | BOOLEAN NULL | for checklist |
| access_token_enc | TEXT | system-user or business-integration token |
| token_type | VARCHAR(24) | `SYSTEM_USER`, `BUSINESS_INTEGRATION` |
| token_expires_at | DATETIME NULL | null = never |
| token_status | VARCHAR(16) | `VALID, INVALID, EXPIRED, UNKNOWN` |
| token_checked_at | DATETIME | |
| webhook_subscribed | BOOLEAN | |
| onboarding_type | VARCHAR(16) | `EMBEDDED_SIGNUP`, `MANUAL` |
| billing_mode | VARCHAR(16) | `PLATFORM`, `TECH_PROVIDER`, `SOLUTION_PARTNER` |
| credit_line_shared | BOOLEAN | |
| status | VARCHAR(16) | `ACTIVE, RESTRICTED, DISABLED, DISCONNECTED` |
| restriction_info | JSON | from `account_update` |
| connected_at, disconnected_at | | |
| raw | JSON | last GET response |

### `phone_numbers` (senders) [A]
| col | type | notes |
|---|---|---|
| id | PK | |
| whatsapp_account_id | FK | |
| phone_number_id | VARCHAR(32) UNIQUE | Meta id |
| display_phone_number | VARCHAR(32) | |
| wa_number | VARCHAR(20) | digits only, E.164 w/o + |
| verified_name | VARCHAR(190) | |
| name_status | VARCHAR(32) | `APPROVED, PENDING_REVIEW, DECLINED, …` |
| new_name_status | VARCHAR(32) NULL | |
| quality_rating | VARCHAR(16) | `GREEN, YELLOW, RED, UNKNOWN` |
| messaging_limit_tier | VARCHAR(32) | as returned by Meta |
| throughput_level | VARCHAR(16) | `STANDARD`, `HIGH`, … |
| max_mps | INT | our throttle (default 60, ≤ Meta throughput) |
| code_verification_status | VARCHAR(32) | |
| platform_type | VARCHAR(16) | `CLOUD_API` |
| meta_status | VARCHAR(32) | `CONNECTED, PENDING, FLAGGED, RESTRICTED, OFFLINE, DISCONNECTED, BANNED…` |
| health_status | JSON | `health_status` field (can_send_message + errors) |
| sender_type | VARCHAR(24) | `PLATFORM_SENDER`, `PARTNER_OWN_SENDER` |
| is_coexistence | BOOLEAN | |
| is_registered | BOOLEAN | |
| pin_enc | VARCHAR(255) | 2-step PIN |
| daily_send_cap | INT NULL | our cap |
| is_paused / pause_reason | BOOLEAN / VARCHAR(255) | auto-pause on quality drop |
| status | VARCHAR(16) | our lifecycle: `CONNECTED, PENDING_SETUP, DISCONNECTED` |
| connected_at, last_synced_at | | |
| raw | JSON | |
Index `idx_pn_waba`, `idx_pn_sender_type_status`.

### `partner_phone_numbers` [A]
| id PK | partner_id FK | phone_number_id FK | assignment_type (`ASSIGNED`,`OWN`) | is_default BOOLEAN | active BOOLEAN | assigned_by FK NULL | assigned_at | unassigned_at NULL | `active_key` generated col = IF(active, phone_number_id, NULL) **UNIQUE** → a number belongs to max one partner at a time (needed to route inbound messages) |

### `business_profiles` [A]
| id PK | phone_number_id FK UNIQUE | about VARCHAR(139) | description VARCHAR(512) | address VARCHAR(256) | email VARCHAR(128) | websites JSON (≤2) | vertical VARCHAR(32) | profile_picture_url VARCHAR(1024) | profile_picture_file_id FK NULL | synced_at |

### `qr_codes` [A]
| id PK | partner_id FK | phone_number_id FK | meta_code VARCHAR(64) | prefilled_message VARCHAR(140) | deep_link_url VARCHAR(255) | qr_image_png_url VARCHAR(1024) | qr_image_svg_url VARCHAR(1024) | short_link VARCHAR(512) | UNIQUE(phone_number_id, meta_code) |

### `conversational_components` [A]
| id PK | phone_number_id FK UNIQUE | enable_welcome_message BOOLEAN | prompts JSON (ice breakers ≤4, ≤80 chars) | commands JSON (≤30, name ≤32 / description ≤256) | sync_status (`SYNCED`,`FAILED`) | synced_at |

### `blocked_users` [A]
| id PK | partner_id FK | phone_number_id FK | wa_id VARCHAR(20) | status (`BLOCKED`,`UNBLOCKED`,`FAILED`) | meta_error VARCHAR(255) | blocked_at | unblocked_at | UNIQUE(phone_number_id, wa_id) |

### `templates` [A]
| col | type | notes |
|---|---|---|
| id, uid | | |
| partner_id | FK NULL | null = platform/admin template |
| whatsapp_account_id | FK | |
| meta_template_id | VARCHAR(32) NULL UNIQUE | |
| name | VARCHAR(512) | lowercase/underscore |
| language | VARCHAR(10) | `en`, `en_US`, `hi` … |
| category | VARCHAR(32) | |
| previous_category | VARCHAR(32) NULL | from `template_category_update` |
| parameter_format | VARCHAR(16) | `POSITIONAL`, `NAMED` |
| library_template_name | VARCHAR(128) NULL | |
| status | VARCHAR(24) | `DRAFT, PENDING_REVIEW` (internal admin queue), `INTERNAL_REJECTED, PENDING` (at Meta), `APPROVED, REJECTED, PAUSED, DISABLED, IN_APPEAL, PENDING_DELETION, DELETED, LIMIT_EXCEEDED, ARCHIVED` |
| rejected_reason | VARCHAR(500) | Meta or admin reason |
| quality_score | VARCHAR(16) | `GREEN, YELLOW, RED, UNKNOWN` |
| admin_review_note | VARCHAR(500) | |
| reviewed_by / reviewed_at | | |
| submitted_at / last_synced_at | | |
| meta_payload | JSON | last Meta representation |
`UNIQUE(whatsapp_account_id, name, language)`, `idx_templates_partner_status`.

### `template_components`
| id PK | template_id FK | position INT | type (`HEADER, BODY, FOOTER, BUTTONS`) | format (`TEXT, IMAGE, VIDEO, DOCUMENT, LOCATION`) NULL | text TEXT | example JSON | buttons JSON | header_media_file_id FK NULL | header_handle VARCHAR(512) NULL | add_security_recommendation BOOLEAN NULL | code_expiration_minutes INT NULL |

### `template_analytics`
| id PK | template_id FK | date DATE | sent INT | delivered INT | read INT | clicked JSON (per button) | cost DECIMAL(12,4) | UNIQUE(template_id, date) |

---

## F. Contacts & campaigns

### `contacts` [A]
| id PK | partner_id FK | phone VARCHAR(20) (E.164 digits) | name VARCHAR(120) | email VARCHAR(190) | country_code CHAR(2) | tags JSON (array of strings, multi-valued index) | attributes JSON (custom fields for variables) | opted_out BOOLEAN | is_blocked BOOLEAN | last_inbound_at NULL | source (`MANUAL,IMPORT,API,INBOUND`) | **UNIQUE(partner_id, phone)** |

### `contact_groups` [A]
| id PK | partner_id FK | name VARCHAR(100) | description VARCHAR(255) | member_count INT | UNIQUE(partner_id, name) |

### `contact_group_members`
| group_id FK, contact_id FK, PK(group_id, contact_id) | added_at |

### `opt_outs`
| id PK | partner_id FK | phone VARCHAR(20) | phone_number_id FK NULL | source (`KEYWORD, MANUAL, API, IMPORT, META_131050`) | keyword VARCHAR(32) NULL | created_at | created_by NULL | UNIQUE(partner_id, phone) — row deleted on opt-in (audited) |

### `campaigns` [A]
| col | type | notes |
|---|---|---|
| id, uid | | |
| partner_id | FK | |
| name | VARCHAR(150) | |
| phone_number_id | FK | sender |
| template_id | FK | |
| audience_type | VARCHAR(16) | `FILE, GROUPS, CONTACTS` |
| source_file_id | FK NULL | |
| column_mapping | JSON | `{phone:"Mobile", vars:{"1":"Name"}}` |
| header_media | JSON NULL | |
| status | VARCHAR(16) | `DRAFT, SCHEDULED, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED` |
| pause_reason | VARCHAR(64) | `USER, ADMIN, INSUFFICIENT_BALANCE, SENDER_QUALITY, TEMPLATE_PAUSED` |
| scheduled_at, started_at, completed_at | | UTC |
| total_rows, valid_count, invalid_count, skipped_count | INT | |
| queued_count, sent_count, delivered_count, read_count, failed_count | INT | |
| unit_price, estimated_cost, charged_amount, refunded_amount | DECIMAL(12,4) | |
Index `idx_campaigns_partner_status`, `idx_campaigns_status_scheduled (status, scheduled_at)`.

### `campaign_recipients`
| id PK | campaign_id FK | contact_id FK NULL | row_no INT | phone VARCHAR(20) | variables JSON | status (`PENDING, SKIPPED, QUEUED, SENT, DELIVERED, READ, FAILED`) | skip_reason (`INVALID_NUMBER, OPTED_OUT, BLOCKED, DUPLICATE, MISSING_VARIABLE`) | message_id FK NULL | error_message VARCHAR(255) | updated_at |
Index `idx_cr_campaign_status (campaign_id, status)`, `UNIQUE(campaign_id, phone)`.

---

## G. Messaging & webhooks

### `messages` [A]
| col | type | notes |
|---|---|---|
| id | PK | |
| uid | CHAR(26) UNIQUE | public message id |
| partner_id | FK | |
| phone_number_id | FK | sender |
| direction | VARCHAR(8) | `OUTBOUND`, `INBOUND` |
| wa_id | VARCHAR(20) | counter-party number |
| country_code | CHAR(2) | derived via libphonenumber |
| contact_id | FK NULL | |
| campaign_id / campaign_recipient_id | FK NULL | |
| type | VARCHAR(24) | `TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, STICKER, LOCATION, CONTACTS, REACTION, INTERACTIVE, TEMPLATE, UNSUPPORTED, SYSTEM, BUTTON, ORDER` |
| template_id / template_name / template_language | | |
| category | VARCHAR(32) | priced category (SERVICE for free-form) |
| payload | JSON | request body sent (outbound) / message object (inbound) |
| preview | VARCHAR(1024) | text preview for lists |
| context_wamid | VARCHAR(128) NULL | reply-to |
| wamid | VARCHAR(128) **UNIQUE** NULL | |
| status | VARCHAR(16) | `QUEUED, ACCEPTED, SENT, DELIVERED, READ, FAILED, RECEIVED, DELETED` |
| error_code | INT NULL | Meta code |
| error_title / error_message | VARCHAR(255) | raw / friendly |
| price | DECIMAL(12,4) | partner price |
| charge_status | VARCHAR(16) | `NONE, HELD, CHARGED, REFUNDED` |
| billable | BOOLEAN NULL | from Meta pricing |
| pricing_category / pricing_type | VARCHAR(32) | from Meta `pricing` |
| source | VARCHAR(16) | `DASHBOARD, API, CAMPAIGN, INBOX` |
| api_key_id | FK NULL | |
| client_reference | VARCHAR(100) NULL | partner's own id |
| sent_at, delivered_at, read_at, failed_at | DATETIME(6) | |
Indexes: `idx_messages_partner_created (partner_id, created_at)`, `uk_messages_wamid (wamid)`, `idx_messages_status (status)`, `idx_messages_sender_created (phone_number_id, created_at)`, `idx_messages_conv (phone_number_id, wa_id, created_at)`, `idx_messages_campaign (campaign_id)`.

### `message_status_events`
| id PK | message_id FK | wamid | status | error_code NULL | error_title NULL | pricing JSON | event_timestamp DATETIME(6) | raw JSON | created_at | **UNIQUE(wamid, status)** |

### `message_queue`
| id PK | message_id FK UNIQUE | phone_number_id FK | priority TINYINT (1 = API/dashboard, 5 = campaign) | status (`PENDING, PROCESSING, DONE, DEAD`) | attempts INT | max_attempts INT (5) | next_attempt_at DATETIME(6) | locked_by VARCHAR(64) | locked_at | last_error VARCHAR(500) | created_at |
Index `idx_mq_poll (status, next_attempt_at, priority)`, `idx_mq_sender (phone_number_id, status)`.

### `conversation_windows`
| id PK | partner_id FK | phone_number_id FK | wa_id VARCHAR(20) | last_inbound_at DATETIME(6) | expires_at DATETIME(6) (= last_inbound_at + 24h) | unread_count INT | last_message_preview VARCHAR(255) | last_message_at | **UNIQUE(phone_number_id, wa_id)** | doubles as inbox conversation list |

### `inbound_media`
| id PK | message_id FK | partner_id FK | media_id VARCHAR(64) | mime_type | sha256 | file_size BIGINT | caption VARCHAR(1024) | filename VARCHAR(255) | file_id FK stored_files NULL | download_status (`PENDING, DONE, FAILED`) | attempts INT |

### `webhook_inbound_logs`
| id PK | source (`META`,`RAZORPAY`) | event_id VARCHAR(80) **UNIQUE** | signature_valid BOOLEAN | object_type VARCHAR(64) | waba_id VARCHAR(32) NULL | fields VARCHAR(255) | payload JSON | status (`RECEIVED, PROCESSED, FAILED, IGNORED`) | attempts INT | error VARCHAR(1000) | received_at | processed_at |
Index `idx_wil_status (status, received_at)`.

### `api_keys` [A]
| id PK | partner_id FK | name VARCHAR(100) | api_key VARCHAR(48) UNIQUE (`cbk_live_…`) | secret_hash CHAR(64) | secret_last4 CHAR(4) | ip_whitelist JSON | rate_limit_per_minute INT NULL (null → admin default) | status (`ACTIVE`,`DISABLED`) | last_used_at | last_used_ip |

### `partner_webhooks` [A]
| id PK | partner_id FK UNIQUE | url VARCHAR(500) (https only) | secret_enc | events JSON (`message.status, message.inbound, template.status, sender.status, wallet.low_balance`) | is_active BOOLEAN | consecutive_failures INT | last_success_at |

### `webhook_delivery_logs`
| id PK | partner_webhook_id FK | partner_id FK | event_id CHAR(26) | event_type VARCHAR(40) | payload JSON | attempt INT | status (`PENDING, SUCCESS, FAILED, DEAD`) | response_code INT | response_body VARCHAR(2000) | duration_ms INT | next_retry_at | created_at |
Index `idx_wdl_status_retry (status, next_retry_at)`, `idx_wdl_partner_created`.

---

## H. Platform

### `notifications`
| id PK | user_id FK NULL | partner_id FK NULL | audience (`ADMIN`,`PARTNER`) | type VARCHAR(48) | severity (`INFO,SUCCESS,WARNING,ERROR`) | title VARCHAR(150) | body VARCHAR(1000) | link VARCHAR(255) | is_read | read_at | created_at |
Index `idx_notif_user_read (user_id, is_read, created_at)`, `idx_notif_partner_read`.

### `settings` [A]
| id PK | group_code VARCHAR(32) | setting_key VARCHAR(64) | value TEXT | value_type (`STRING,NUMBER,BOOLEAN,JSON`) | is_encrypted BOOLEAN | UNIQUE(group_code, setting_key) |
Groups/keys seeded: `COMPANY(name, address, gstin, state_code, email, phone, logo_file_id)`, `GST(enabled, percent, invoice_prefix, sac_code)`, `PAYMENT(active_gateway, razorpay_key_id, razorpay_key_secret*, razorpay_webhook_secret*, min_amount, max_amount)`, `SMTP(host, port, username, password*, from_email, from_name, starttls)`, `CHARGING(charge_on, charge_free_utility_in_window, hold_timeout_hours)`, `WALLET(default_low_balance_threshold)`, `RATE_LIMIT(public_api_per_minute, login_per_minute)`, `SECURITY(admin_2fa_required, max_login_attempts, lockout_minutes)`, `STORAGE(max_upload_mb)`, `RETENTION(webhook_log_days)` (* encrypted).

### `audit_logs`
| id PK | actor_user_id FK NULL | actor_type (`ADMIN,PARTNER,API_KEY,SYSTEM`) | impersonator_user_id NULL | partner_id NULL | action VARCHAR(64) (`WALLET_ADJUST, TOPUP_APPROVE, TOPUP_REJECT, SETTINGS_UPDATE, LOGIN_AS_PARTNER, SENDER_CONNECT, SENDER_DISCONNECT, API_KEY_REGENERATE, …`) | entity_type VARCHAR(48) | entity_id VARCHAR(64) | old_value JSON (masked) | new_value JSON (masked) | ip VARCHAR(45) | user_agent VARCHAR(255) | created_at |
Index `idx_audit_created`, `idx_audit_entity (entity_type, entity_id)`, `idx_audit_actor`.

### `stored_files`
| id PK | uid CHAR(26) UNIQUE | partner_id FK NULL | category (`TOPUP_PROOF, INVOICE, MEDIA_OUTBOUND, MEDIA_INBOUND, COMPANY_LOGO, PROFILE_PHOTO, IMPORT, TEMPLATE_HEADER`) | original_name | storage_path VARCHAR(500) | mime_type | size_bytes | sha256 CHAR(64) | created_by | created_at |

### `meta_pricing_analytics`
| id PK | whatsapp_account_id FK | date DATE | phone_number VARCHAR(32) | country CHAR(2) | pricing_category VARCHAR(32) | pricing_type VARCHAR(32) | volume INT | cost DECIMAL(12,4) | currency | synced_at | UNIQUE(whatsapp_account_id, date, phone_number, country, pricing_category, pricing_type) |

**Total: 50 tables** = all 45 from Part I + 5 added by the architect: `user_otps` (forgot-password / 2FA), `number_sequences` (gap-free invoice & code numbering), `platform_fee_charges` (idempotent monthly fee), `stored_files` (all uploads), `meta_pricing_analytics` (Meta cost reconciliation).

## Seed data (Flyway `V2__seed.sql` + Java seeder for secrets)
- Roles: `SUPER_ADMIN` (all permissions), `API_PARTNER`, sample staff roles `SUPPORT` (view-only), `FINANCE` (wallet/topup/payment/report).
- Super admin: email/password from env `CBWA_SEED_ADMIN_EMAIL` / `CBWA_SEED_ADMIN_PASSWORD` (Java `ApplicationRunner`, only if no super admin exists; `must_change_password=true`). No password is hard-coded in SQL.
- Default rate plan "India Standard" (`is_default=true`): IN MARKETING / UTILITY / AUTHENTICATION / SERVICE prices, values entered by admin — **seeded with 0.0000 and status ACTIVE but flagged "prices not set"**, sending is blocked until admin saves prices (no hard-coded prices, Part L). → *see open question Q4*.
- Credit packs (inactive until admin activates, amounts editable): "Starter ₹1,000 + 10%", "Growth ₹5,000 + 12%", "Pro ₹10,000 + 15%".
- Default settings rows for every group above (charge_on=`DELIVERED`, default_low_balance_threshold=500, public_api_per_minute=600, max_login_attempts=5, lockout_minutes=15, max_upload_mb=2).
- `meta_settings` row with `graph_api_version='v25.0'`, `billing_mode='TECH_PROVIDER'`.
