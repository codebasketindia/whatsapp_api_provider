# CB WhatsApp Gateway — Phase 0 · 01 Architecture

Owner: [LEAD ARCHITECT] · Status: **Draft for approval** · Applies to: all phases

---

## 1. System context

```
                         ┌───────────────────────────────────────────────┐
  Partner browser        │                Ubuntu VPS                     │
  (React SPA, mobile/    │                                               │
   desktop)  ───HTTPS───►│  Nginx :443  (Let's Encrypt TLS)              │
                         │   ├─ /            → /var/www/cbwa (React dist)│
  Admin browser ─HTTPS──►│   ├─ /api/**      → 127.0.0.1:8080 (Spring)   │
                         │   └─ /actuator/health → 127.0.0.1:8080        │
  Partner servers        │                                               │
  (REST, X-API-KEY) ────►│  Spring Boot 3 / Java 21 (systemd: cbwa.service)
                         │   ├─ REST controllers (admin/partner/public)  │
  Meta Graph API ◄──────►│   ├─ Services + domain                        │
  (webhooks in, API out) │   ├─ DB-backed queue workers (@Scheduled)     │
                         │   ├─ Webhook ingest (Meta / Razorpay)         │
  Razorpay ◄────────────►│   ├─ Outbound partner-webhook dispatcher      │
  (orders, webhooks)     │   └─ Jobs: reconciliation, token health, fees │
                         │                                               │
  SMTP server ◄──────────│  MySQL 8 (InnoDB, utf8mb4) :3306 localhost    │
                         │  Local disk: /var/lib/cbwa/storage (uploads)  │
                         └───────────────────────────────────────────────┘
```

Single deployable JAR (modular monolith) + one React SPA. No Docker. One MySQL schema `cbwa`.

## 2. Backend logical layers

```
controller (REST, DTO in/out, @PreAuthorize, Bean Validation)
   │
service (business rules, @Transactional, wallet locks)
   │                    │
repository (JPA)     integration clients (WebClient)
   │                    ├─ meta   (Graph API: messages, templates, phone numbers, WABA, media, uploads)
MySQL                   ├─ payment (PaymentGateway interface → RazorpayGateway)
                        ├─ mail   (Spring Mail)
                        └─ partnerhook (signed outbound callbacks)
```

Cross-cutting: `common` (ApiResponse wrapper, exceptions, paging, crypto, audit, file storage, IST/UTC clock, request-id MDC logging).

## 3. Modules (Java packages under `in.codebasket.cbwa`)

| Module | Responsibility | Phase |
|---|---|---|
| `common` | Response wrapper, errors, base entities, AES-GCM `CryptoService`, file storage, sequences, time utils | 1 |
| `auth` | Login, JWT access/refresh rotation, 2FA OTP, forgot/reset, login attempt limiting | 2 |
| `user` | Users, staff, roles, permissions, profile | 2 |
| `partner` | Partner CRUD, KYC, status, settings, login-as-partner | 3 |
| `wallet` | Wallet, ledger, HOLD/RELEASE/DEBIT/REFUND/CREDIT, manual adjust, low-balance alerts | 4 |
| `topup` | Manual top-up requests | 4 |
| `pricing` | Credit packs, rate plans, price resolution | 4 / 6 |
| `payment` | `PaymentGateway` interface, Razorpay impl, orders, verification, webhooks, reconciliation | 5 |
| `invoice` | GST invoices, PDF (OpenPDF), numbering | 5 |
| `meta` | Graph API client, Meta settings, error-code mapping | 6 |
| `waba` | WhatsApp accounts, Embedded Signup, manual connect, token health | 6 |
| `sender` | Phone numbers, assignment, verification, PIN, business profile, health | 6 |
| `convtools` | QR codes, conversational components, block list, opt-out keywords | 7 |
| `template` | Templates, components, review queue, sync, analytics, library | 7 |
| `messaging` | Send service, queue, worker, throttling, charging, 24h window, media, inbox | 8 |
| `webhook` | Meta webhook ingest/verify/route/process | 8 |
| `contact` | Contacts, groups, tags, import, opt-outs | 9 |
| `campaign` | Campaign wizard, recipients, dispatcher, report | 9 |
| `publicapi` | `/api/v1/public`, API-key auth filter, Bucket4j limits | 10 |
| `partnerhook` | Partner webhook config, signed delivery, retries, logs | 10 |
| `report` | Dashboards, reports, Meta reconciliation, exports (CSV/XLSX/PDF) | 11 |
| `settings` | Key-value settings, company profile, SMTP, charging rule | 1 → 11 |
| `audit` | Audit log write/read | 2 → 11 |
| `notification` | In-app notifications + email | 4 → 11 |

## 4. Key runtime flows

### 4.1 Send a message (dashboard, API or campaign)
```
validate request ─► resolve sender (assigned/own, active, not paused, daily cap)
  ─► 24h window check (free-form needs open window; else template required)
  ─► opt-out / block check (marketing templates skip opted-out contacts)
  ─► price = RatePlanResolver(partner, senderType, category, country)
  ─► TX1 [wallet row PESSIMISTIC_WRITE]: balance ≥ price ? HOLD : 402 WALLET_INSUFFICIENT_BALANCE
     insert messages(status=QUEUED, charge_status=HELD) + message_queue(PENDING)
  ─► Worker (FOR UPDATE SKIP LOCKED, per-sender token bucket) → POST /{PHONE_NUMBER_ID}/messages
       ├─ 200 → wamid saved, status=ACCEPTED (hold stays open)
       ├─ 4xx permanent → status=FAILED, friendly error, RELEASE hold
       └─ 429 / 5xx / timeout → retry with exponential backoff (max 5) → finally FAILED + RELEASE
Webhook statuses (admin setting charge_on = SENT | DELIVERED):
   sent      → charge_on=SENT      → finalize (DEBIT from hold)
   delivered → charge_on=DELIVERED → finalize (DEBIT from hold)
   read      → finalize if still HELD (read implies delivered)
   failed    → hold still open (charge_on=DELIVERED, or SENT never arrived) → REFUND (hold returned)
               already finalized (charge_on=SENT) → no refund
   pricing.billable=false (free message per Meta) → partner charge per admin setting (default: no charge → REFUND hold)
```

### 4.2 Wallet semantics (D1 + D5)
- `wallets.balance` = **available** balance (spendable). `wallets.held_amount` = sum of open holds.
- `HOLD`   : balance −= p, held += p
- `DEBIT`  : held −= p (finalize charge; balance unchanged) — also used for manual admin debit and monthly platform fee (balance −= p)
- `RELEASE`: held −= p, balance += p (Meta rejected immediately / never sent)
- `REFUND` : balance += p for money already debited (e.g. a charge that was finalized and later must be returned, admin goodwill refunds)
- `CREDIT` : balance += p (top-up approve, gateway payment, admin credit, bonus)
- Every row stores `balance_before/after` and `held_before/after`. All inside `@Transactional` with `SELECT … FOR UPDATE` on the wallet row. A DB `CHECK (balance >= 0 AND held_amount >= 0)` is a last line of defence.

> With "charge on DELIVERED", a FAILED status arrives while the money is still on HOLD, so the code path is RELEASE (money returns to available). It is shown to the partner as **"Refund – message failed"** and ledger type `REFUND` with `reference_type=MESSAGE` so the business wording in D5 ("FAILED → REFUND") holds. Rule: *hold still open → REFUND row reverses the hold (held −= p, balance += p); hold already finalized → REFUND row credits balance*.

### 4.3 Meta webhook
```
POST /api/webhooks/whatsapp
  verify X-Hub-Signature-256 = "sha256=" + HMAC_SHA256(app_secret, raw_body)  (constant-time compare)
  invalid → 401 (logged, not processed)
  event_id = SHA-256(raw_body)   ← Meta sends no event id; dedupe on payload hash
  INSERT webhook_inbound_logs (UNIQUE event_id) → duplicate → 200 and skip
  return 200 immediately
  async processor: for entry[] (entry.id = WABA ID) → changes[] by field →
     route by WABA ID / metadata.phone_number_id → partner
     statuses: idempotent per (wamid, status) via message_status_events UNIQUE
     messages: idempotent per inbound wamid (messages.wamid UNIQUE)
```

### 4.4 Payment (Razorpay)
```
partner picks amount/pack → POST /api/partner/payments/orders
  → Razorpay POST /v1/orders (amount in paise, receipt = our order uid, notes.partner_id)
  → Checkout (UPI intent/QR) → handler(payment_id, order_id, signature)
  → POST /api/partner/payments/verify : HMAC_SHA256(order_id|payment_id, key_secret) == signature
      → fetch payment from Razorpay (status captured) → credit wallet (idempotent on gateway_payment_id)
  → webhook payment.captured / order.paid (X-Razorpay-Signature over raw body, webhook secret)
      → same idempotent credit path (UNIQUE gateway_payment_id, row lock on payment_orders)
  → reconciliation job every 10 min: orders CREATED/ATTEMPTED > 15 min old → fetch order payments → settle/expire
```

## 5. Scheduled jobs

| Job | Schedule (IST) | Purpose |
|---|---|---|
| `MessageQueueWorker` | every 1 s (fixed delay) | Dispatch PENDING queue rows, per-sender throttling |
| `CampaignDispatcher` | every 5 s | Start scheduled campaigns, feed recipients into queue in batches |
| `WebhookInboundProcessor` | every 2 s + async on receipt | Process RECEIVED webhook logs (retry FAILED up to 5) |
| `PartnerWebhookDispatcher` | every 5 s | Deliver/retry partner callbacks (5 retries: 1m, 5m, 15m, 1h, 6h) |
| `PaymentReconciliationJob` | every 10 min | Settle stuck Razorpay orders |
| `TokenHealthJob` | every 6 h | `GET /debug_token` + `GET /{WABA_ID}` per account; mark INVALID, notify, show Reconnect |
| `SenderSyncJob` | every 1 h | Refresh quality rating, tier, name status, health for all phone numbers |
| `HoldTimeoutJob` | every 15 min | Holds older than 72 h w/o final status → finalize if wamid exists & rule=SENT, else RELEASE |
| `LowBalanceAlertJob` | on wallet change + daily 09:00 | Banner + email (max 1 email / 24 h per partner) |
| `MonthlyPlatformFeeJob` | 1st of month 00:30 | Charge monthly per-number platform fee (TECH_PROVIDER mode) |
| `MetaPricingAnalyticsJob` | daily 03:00 | Pull `pricing_analytics` per WABA for reconciliation |
| `TemplateAnalyticsJob` | daily 03:30 | Pull `template_analytics` |
| `CleanupJob` | daily 04:00 | Expired refresh tokens/OTPs, old webhook logs (> 90 days, configurable) |

All jobs are single-instance (one JAR). Queue rows still use `FOR UPDATE SKIP LOCKED` so a second instance later is safe.

## 6. Security architecture
- Spring Security stateless; filters: `RequestIdFilter` → `JwtAuthFilter` (`/api/admin/**`, `/api/partner/**`, `/api/me/**`) → `ApiKeyAuthFilter` (`/api/v1/public/**`) → Bucket4j rate-limit filter (login + public API).
- JWT HS512, secret from env `CBWA_JWT_SECRET` (≥ 64 bytes). Access 15 min. Refresh 7 days, opaque random 256-bit, stored as SHA-256 hash, rotated on every use; reuse of a rotated token revokes the whole family.
- Impersonation (login-as-partner): access token carries `imp=<adminUserId>`; every write while impersonating is audited with `impersonator_user_id`; banner in UI.
- AES-256-GCM (`CryptoService`), key from env `CBWA_ENCRYPTION_KEY` (base64 32 bytes), random 12-byte IV per value, stored as `v1:<base64(iv|ciphertext|tag)>` for key rotation.
- Partner API secret: shown once, stored as `HMAC-SHA256(pepper, secret)` (fast constant-time check per request; high-entropy secrets do not need BCrypt).
- File uploads: extension + magic-byte sniffing (Apache Tika core), size limits, stored outside web root with random names, served only through authorized `/api/files/{uid}`.

## 7. Meta documentation notes — where the prompt was adjusted to match Meta docs

| # | Prompt said | Meta docs say / what we do |
|---|---|---|
| M1 | Non-template messages in the 24h window are free | ✅ Correct. **Also**: since 1 Jul 2025 (per-message pricing), **UTILITY templates sent inside an open customer-service window are free** too. Our charging engine reads `pricing.billable` + `pricing.type` from the status webhook and charges the partner per the admin setting `charge_free_utility_in_window` (default **false** = don't charge). |
| M2 | Categories MARKETING / UTILITY / AUTHENTICATION | Meta also has **AUTHENTICATION_INTERNATIONAL** (auth templates to some countries from businesses outside them) and **SERVICE**. Rate plan items support all five. |
| M3 | Webhook "idempotent by event" | Meta webhook payloads have **no event id**. We dedupe on SHA-256 of the raw body + per-item keys (`wamid`+`status`, inbound `wamid`, template `message_template_id`+`event`). |
| M4 | Register number after Embedded Signup | Not for **coexistence** numbers (WhatsApp Business App). For those we skip `/register` and call `POST /{PHONE_NUMBER_ID}/smb_app_data` (contacts + history sync) within 24 h of onboarding, and subscribe to `smb_message_echoes`, `smb_app_state_sync`, `history` webhook fields. |
| M5 | Exchange code | Embedded Signup `code` lives **30 seconds** — frontend posts it to backend immediately; backend exchanges it before anything else. |
| M6 | Typing indicator | Sent together with mark-as-read (`status: "read"`, `message_id`, `typing_indicator: {type:"text"}`); it auto-dismisses after 25 s or when we reply. |
| M7 | Analytics | `conversation_analytics` is legacy after per-message pricing → we use `pricing_analytics` and `template_analytics` (template insights must be enabled on the WABA once: `POST /{WABA_ID}?is_enabled_for_insights=true`). |
| M8 | Block users | Only users who messaged the business in the last 24 h can be blocked (`POST /{PHONE_NUMBER_ID}/block_users`). UI explains this. |
| M9 | Short links | Meta's short-link management endpoints are no longer offered; QR codes (`/{PHONE_NUMBER_ID}/message_qrdls`) remain. We generate short links locally as `https://wa.me/<number>?text=<encoded>` (always works) and show them beside the QR code. **Please confirm** (see open questions). |
| M10 | Messaging limit "tier" per number | Meta moved messaging limits to the **business portfolio** level. We store and display whatever `messaging_limit_tier` / `whatsapp_business_manager_messaging_limit` the API returns and do not hard-code tiers. |
| M11 | Template "PAUSED/DISABLED" | Also handled: `REJECTED`, `IN_APPEAL`, `PENDING_DELETION`, `DELETED`, `LIMIT_EXCEEDED`, `ARCHIVED`, plus `message_template_components_update`. |
| M12 | Solution Partner billing | Credit line is shared per onboarded WABA via `POST /{EXTENDED_CREDIT_LINE_ID}/whatsapp_credit_sharing_and_attach?waba_currency=INR&waba_id=...`. Requires us to be an approved Solution Partner with a credit line; until then only TECH_PROVIDER mode can be enabled. |
| M13 | Marketing to opted-out users | Meta additionally returns error **131050** when a user stopped marketing messages from the business, and **131049** for per-user marketing limits. Both mapped to friendly errors, no retry, hold released. |

## 8. Error mapping (Meta → partner) — initial catalogue (expanded in Phase 8)

| Meta code | Friendly message | Retry? |
|---|---|---|
| 131026 | Recipient is not on WhatsApp or can't receive this message | No |
| 131047 | 24-hour window closed — use an approved template | No |
| 132015 | Template is paused by Meta due to low quality | No |
| 132016 | Template is disabled by Meta | No |
| 132000/132001 | Template variables don't match / template not found in this language | No |
| 131050 | Recipient has stopped marketing messages from you | No |
| 131049 | Meta limited marketing messages to this user (healthy ecosystem) | No |
| 131056 | Too many messages to the same user too fast | Yes (backoff) |
| 130429 / 80007 / 4 | Throughput/rate limit reached | Yes |
| 131042 | Business payment issue — add a payment method in Meta | No |
| 131031 | WhatsApp account locked | No |
| 133010 | Phone number not registered | No |
| 190 | Access token expired/invalid — reconnect your number | No |
| 368 / 131048 | Restricted for policy violation / spam rate limit | No |
| 1, 2, 131000, 5xx | Temporary Meta error | Yes |
