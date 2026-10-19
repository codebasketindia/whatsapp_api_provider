# CB WhatsApp Gateway — Phase 0 · 06 Prerequisites, QA plan, Open questions, DoD

## 1. Meta prerequisites checklist (owner: you / CodeBasket) — [LEAD ARCHITECT]
| # | Item | Needed by |
|---|---|---|
| 1 | Meta Business Portfolio for CodeBasket, **business verification completed** | Phase 6 |
| 2 | Meta Developer account; App type **Business**; WhatsApp product added | Phase 6 |
| 3 | App Settings → Basic: App domain, Privacy Policy URL, Terms URL, Data deletion URL, app icon, category | Phase 6 / App Review |
| 4 | Become a **Tech Provider** (accept Tech Provider terms in App Dashboard); later optionally apply as **Solution Partner** (needed for SOLUTION_PARTNER billing / credit line) | Phase 6 |
| 5 | **Facebook Login for Business** product → create **Embedded Signup configuration** (WhatsApp Embedded Signup, permissions `whatsapp_business_management`, `whatsapp_business_messaging`, `business_management` if needed) → copy **Configuration ID** | Phase 6 |
| 6 | Allowed domains for JS SDK + Valid OAuth redirect URIs = your HTTPS domain | Phase 6 |
| 7 | **System user** (admin) in our Business Portfolio with full control of our WABA + app; generate **permanent token** with `whatsapp_business_management`, `whatsapp_business_messaging` | Phase 6 |
| 8 | Our own WABA with at least one phone number (platform sender) + payment method in WhatsApp Manager | Phase 6 / 8 |
| 9 | Webhook: callback `https://<domain>/api/webhooks/whatsapp`, verify token (same as Settings), subscribe fields: `messages, message_template_status_update, message_template_quality_update, message_template_components_update, template_category_update, phone_number_quality_update, phone_number_name_update, account_update, account_review_update, account_alerts, business_capability_update, security, smb_message_echoes, smb_app_state_sync, history` | Phase 8 |
| 10 | **App Review / Advanced Access** for `whatsapp_business_management` and `whatsapp_business_messaging` (screencast of Embedded Signup + sending a message + template creation) — required before onboarding real partners | Phase 12 |
| 11 | App switched to **Live** mode | Phase 12 |
| 12 | A real test phone (not already on WhatsApp/WA Business app for "new number" flow) + one WA Business App number for coexistence test | Phase 6 |

## 2. Razorpay prerequisites
| # | Item |
|---|---|
| 1 | Razorpay account, KYC activated (live mode) — test mode works immediately |
| 2 | API keys (Key ID + Key Secret) for **test** and later **live** |
| 3 | Webhook `https://<domain>/api/webhooks/payments/razorpay`, secret set, events: `payment.captured`, `payment.failed`, `order.paid` |
| 4 | Payment capture: **automatic** (Settings → Payment capture) |
| 5 | UPI enabled on account (default on); for UPI intent on mobile web, checkout `method` config allowed |
| 6 | Company GSTIN, state, invoice prefix, logo ready for invoices |

## 3. Server prerequisites
Ubuntu 22.04/24.04 VPS (2 vCPU / 4 GB RAM minimum), domain A-record → VPS IP, ports 80/443 open, SMTP credentials (or SES/Zoho/Gmail SMTP), outbound HTTPS to `graph.facebook.com`, `api.razorpay.com`.

## 4. [QA & DEVOPS ENGINEER] Phase 0 test strategy
| Level | Tool | Scope / rule |
|---|---|---|
| Unit | JUnit 5 + Mockito + AssertJ | services, price resolver, crypto, signature verifiers, Meta error mapper — ≥ 80 % line coverage on `wallet`, `payment`, `messaging.charging`, `webhook` |
| Integration | Spring Boot Test + **Testcontainers MySQL 8** | repositories, Flyway migrations, row locks, concurrent wallet debits (ExecutorService 50 threads), idempotency (duplicate webhooks) |
| HTTP stubs | WireMock | Meta Graph API & Razorpay responses (success, 4xx, 429, 5xx, timeouts) |
| API | MockMvc + Postman collection (Newman) | every contract row: auth, role, validation, error codes |
| Frontend | Vitest + React Testing Library (components, Zod schemas), manual responsive checks at 360/390/768/1024/1440 px | |
| Security | OWASP ZAP baseline scan, dependency check (`mvn org.owasp:dependency-check-maven:check`, `npm audit`), manual IDOR tests (partner A cannot read partner B by id) | |
| Load | k6 script for `/api/v1/public/messages/send` + queue throughput (Phase 12) | |
| E2E manual | phase checklist on real Meta test number + Razorpay test mode | |

Cross-phase invariants tested every phase (regression):
1. Ledger chain is unbroken: each row's `balance_before/held_before` == previous row's `balance_after/held_after`; the latest row equals `wallets.balance` / `held_amount`; `held_amount` == Σ prices of messages with `charge_status=HELD`.
2. No wallet balance < 0 ever (DB CHECK + test).
3. No endpoint reachable without the right role/permission (automated matrix test from contract).
4. No secret/token/PIN appears in logs or API responses (log grep test).
5. All list APIs paginated, max size 100.

## 5. Open questions for you (Part L: "ask before assuming") — proposed default in **bold**
| # | Question | Proposed default |
|---|---|---|
| Q1 | GST on top-ups: partner pays amount **+ GST** and wallet gets the full amount (e.g. pay ₹1,180 → ₹1,000 credit), or GST included in the amount (pay ₹1,000 → ₹847.46 credit)? | **GST added on top; wallet credited the pre-GST amount + bonus** |
| Q2 | Invoices for **manual top-ups** too (after approval), or only gateway payments? | **Both** (when GST enabled) |
| Q3 | Default charging rule: charge on SENT or on DELIVERED? | **DELIVERED** |
| Q4 | Do you want me to seed real India prices in the default rate plan, or keep ₹0 and block sending until admin enters prices? | **Seed ₹0, block until set** (no hard-coded prices) |
| Q5 | Free UTILITY templates inside an open 24 h window (Meta doesn't charge) — should partners still be charged our rate? | **No charge** (admin can switch on) |
| Q6 | Monthly platform fee when wallet balance is insufficient on the 1st: pause the number, or let wallet go to zero and retry daily? | **Retry daily for 7 days, notify; after 7 days pause the sender** (wallet never negative) |
| Q7 | Can a platform number be shared by several partners at once? (Inbound replies must route to one partner) | **No — one partner per number at a time** |
| Q8 | Short links: Meta's short-link API is no longer offered; OK to use `wa.me/<number>?text=` links + Meta QR codes? | **Yes** |
| Q9 | Wallet withdrawal/refund of unused balance to partner bank — needed? | **Not in scope** (admin can manual-debit with remark) |
| Q10 | Minimum / maximum gateway top-up amount | **₹100 / ₹2,00,000** (editable in Settings) |
| Q11 | Data retention for message content & inbound media | **Content 180 days, media 90 days, metadata forever** (editable) |
| Q12 | Partner self-registration (sign-up page), or admin creates all partners? | **Admin creates partners only** (as per Phase 3) |
| Q13 | Domain name & company details for invoices (legal name, GSTIN, state, address) | needed before Phase 5 |

## 6. ✅ Phase 0 Definition of Done
- [x] Architecture diagram, modules, key flows, jobs, security model — `01-architecture.md`
- [x] Meta-docs deviations listed with what changed — `01-architecture.md §7`
- [x] ER diagram + all 50 tables with columns, keys, indexes, seed data — `02-database.md`
- [x] Full API contract: method, URL, role, request, response, error codes, public API, webhooks — `03-api-contract.md`
- [x] Every screen with route, permission and phase — `04-screens-routes.md`
- [x] Backend & frontend folder structures, naming conventions, env variables, Git branching — `05-structure-conventions.md`
- [x] Meta & Razorpay & server prerequisites checklist, QA strategy, open questions — this file
- [ ] **Your sign-off** on open questions Q1–Q13 (defaults will be used if you just type NEXT)
