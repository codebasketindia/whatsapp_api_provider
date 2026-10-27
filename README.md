# CB WhatsApp Gateway

WhatsApp Messaging API Provider platform by CodeBasket IT Services & Co.
Spring Boot 3.5 / Java 21 / MySQL 8 backend · React 18 / Vite / Tailwind frontend · Meta WhatsApp Cloud API ·
Razorpay · deployed on Ubuntu with systemd + Nginx (no Docker).

## Quick start (local)
```bash
cd backend && mvn spring-boot:run          # http://localhost:8080  (login: mobile 9999999999 / Admin@12345, dev only)
cd frontend && npm install && npm run dev  # http://localhost:5173
```
Full steps: [docs/local-setup.md](docs/local-setup.md) · Production: [docs/deployment-guide.md](docs/deployment-guide.md)

## Phase status
| Phase | Scope | Status | Verified by |
|---|---|---|---|
| 0 | Planning & architecture | ✅ | `docs/phase-0/` (contract v1.1 changelog lists every agreed change) |
| 1 | Foundation (both apps, layouts, component library) | ✅ | app starts on MySQL with schema validation; UI renders 360 px → desktop |
| 2 | Auth, users, roles, 2FA, lockout | ✅ | **mobile number + password login for all users**; `AuthFlowIT`; browser: forced password change, role blocking |
| 3 | Partner management, login-as-partner | ✅ | `PartnerManagementIT`; browser: admin creates partner who logs in |
| 4 | Wallet ledger, manual top-up, credit packs, low balance | ✅ | `WalletServiceIT` (50 concurrent holds), `TopupAndWalletIT`; browser: ₹1,000 request → approve → balance & ledger correct, duplicate UTR blocked |
| 5 | Razorpay UPI gateway, GST invoices, reconciliation | ✅ | `RazorpayPaymentIT` (signature, repeated webhook credits once, reconcile) — live test-mode payment needs your Razorpay keys |
| 6 | Meta setup, WABAs, senders, Embedded Signup, rate plans | ✅ | `EmbeddedSignupIT`, `SenderAdminIT`, `RatePlanIT` (Graph API stubbed) — real onboarding needs your Meta app |
| 7 | Templates, QR codes, welcome/ice breakers, block list, opt-out keywords | ✅ | `TemplateFlowIT`, `TemplateValidatorTest` |
| 8 | Messaging engine, queue, charging, webhooks, inbox | ✅ | `MessagingIT` (18 cases); browser + signed webhooks: hold → send → delivered charged once, forged webhook 401 |
| 9 | Contacts, import, bulk campaigns | ✅ | `CampaignFlowIT` (100-row campaign, refunds, report), `ContactsIT` |
| 10 | Public API, API keys, partner webhooks, developer portal | ✅ | `PublicApiIT`, `ApiKeyIT`, `PartnerWebhookIT`; cURL send with API key |
| 11 | Dashboards, reports, reconciliation, settings, audit | ✅ | `DashboardReportIT`, `MetaPricingAnalyticsJobIT`, `CleanupJobIT` |
| 12 | Hardening & go-live kit | ✅ | systemd/Nginx/SSL/backup scripts, prod-profile JAR run, Playwright crawl of 90 screens at 390 px & 1280 px with zero API/JS errors |
| 13 | AI Assistant (knowledge-only replies, photo/video attachments, handover) + scheduled messages & campaign reschedule | ✅ | `AiAssistantIT` (Claude mocked), `ScheduledMessageIT`, `CampaignFlowIT` — live AI needs your Anthropic API key |

Automated: **106 backend tests** (real MySQL 8) and **70 frontend tests**, all green.

**Needs your accounts/domain to finish go-live** (cannot be done from the build environment): a real WhatsApp
message to your phone (Meta app + number), Embedded Signup with a real partner, Razorpay test-mode payment,
HTTPS on your domain, Meta App Review. Follow `docs/deployment-guide.md` §6–§7 and `docs/qa/regression-checklist.md`.

## Documentation
- [Architecture](docs/phase-0/01-architecture.md) · [Database](docs/phase-0/02-database.md) · [API contract](docs/phase-0/03-api-contract.md) · [Screens](docs/phase-0/04-screens-routes.md) · [Conventions](docs/phase-0/05-structure-conventions.md) · [Prerequisites & QA plan](docs/phase-0/06-prerequisites-qa-dod.md)
- [Deployment guide](docs/deployment-guide.md) · [Local setup](docs/local-setup.md)
- [Admin guide](docs/admin-guide.md) · [Partner guide](docs/partner-guide.md) · [Partner API guide](docs/api-guide.md)
- [Regression checklist](docs/qa/regression-checklist.md) · [Security review](docs/qa/security-review.md)

## Repository layout
```
backend/   Spring Boot app (modules: auth, user, partner, wallet, topup, pricing, payment, invoice, meta, waba,
           sender, convtools, template, messaging, webhook, contact, campaign, publicapi, partnerhook, report, job,
           ai, scheduling)
frontend/  React SPA (admin + partner panels)
deploy/    systemd, nginx, mysql, scripts (server-setup, deploy, backup, restore, healthcheck), loadtest, e2e
docs/      phase docs, guides, QA
```
