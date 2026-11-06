# CB WhatsApp Gateway — Phase 0 · 05 Folder Structure, Conventions, Env, Git

Owner: [LEAD ARCHITECT]

## 1. Repository layout (mono-repo)
```
whatsapp_api_provider/
├── backend/                     Spring Boot 3 / Java 21 / Maven
├── frontend/                    React 18 / Vite / Tailwind
├── deploy/                      systemd, nginx, mysql, backup, logrotate scripts (NO Docker)
├── docs/                        phase docs, guides, API docs, Postman collection
├── .env.example                 all variables, no real values
├── .gitignore  .editorconfig
└── README.md
```

## 2. Backend structure (role-based, v1.6)
```
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/in/codebasket/cbwa/
    │   │   ├── CbwaApplication.java
    │   │   │
    │   │   ├── common/                     shared by every role
    │   │   │   ├── entity/                 all JPA entities (60) + BaseEntity, AuditableEntity
    │   │   │   ├── repository/             all Spring Data repositories (69)
    │   │   │   ├── constants/              enums and fixed keys (statuses, types, SettingKeys)
    │   │   │   ├── utils/                  static helpers: PhoneUtil, MoneyUtil, TimeUtil, JsonUtil, Ulids, HashUtil, Paging…
    │   │   │   ├── dto/                    API envelope (ApiResponse, PageResponse, FieldErrorDto) + DTOs/events used by several roles
    │   │   │   ├── service/<domain>/       business logic used by more than one role, one folder per domain:
    │   │   │   │                           ai, audit, campaign, contact, convtools, crypto, invoice, mail, messaging, meta,
    │   │   │   │                           notification, payment, pricing, registration, report, scheduling, sender,
    │   │   │   │                           sequence, settings, storage, template, topup, travel, waba, wallet
    │   │   │   ├── job/                    @Scheduled background jobs (queue worker, campaign dispatcher, reconciliation…)
    │   │   │   └── controller/             endpoints for every logged-in user (FileController: /api/files)
    │   │   │
    │   │   ├── admin/                      platform admin panel (/api/admin/**)
    │   │   │   ├── controller/             Admin*Controller, RatePlan, MetaSettings, Waba, Settings, Audit
    │   │   │   ├── service/                admin-only logic (partners, rate plans, WABAs, Meta settings)
    │   │   │   └── dto/
    │   │   │
    │   │   ├── partner/                    partner dashboard + partner API (/api/partner/**, /api/v1/**, public pages)
    │   │   │   ├── controller/             messaging, inbox, campaigns, contacts, templates, AI, registrations, travel…
    │   │   │   │   └── webhook/            partner's own outgoing webhooks + developer portal
    │   │   │   ├── service/                partner-only logic (AI replies, inbox, public API, API keys)
    │   │   │   │   └── webhook/            delivery of partner webhooks (dispatcher, sender, URL validator)
    │   │   │   └── dto/
    │   │   │
    │   │   ├── staff/                      staff users, roles and permissions (/api/admin/staff, /roles)
    │   │   │   ├── controller/
    │   │   │   ├── service/
    │   │   │   └── dto/
    │   │   │
    │   │   ├── webhook/                    incoming webhooks (/api/webhooks/**): Meta WhatsApp, Razorpay, partner Razorpay
    │   │   │   ├── controller/
    │   │   │   ├── service/                inbound messages, statuses, charging on delivery, account events
    │   │   │   └── dto/
    │   │   │
    │   │   ├── config/                     Spring configuration, AppProperties, filters, logging, seeder
    │   │   ├── security/                   SecurityConfig, JWT, API-key auth, login/OTP, /api/auth + /api/me
    │   │   └── exception/                  AppException, ErrorCode, GlobalExceptionHandler, Meta/secret exceptions
    │   │
    │   └── resources/
    │       ├── application.properties          shared settings (secrets only from environment variables)
    │       ├── application-local.properties    default profile for developers
    │       ├── application-prod.properties     server profile (env file /etc/cbwa/cbwa.env)
    │       ├── db/migration/                   Flyway V1__…, V2__… (never edit an applied migration)
    │       ├── templates/  fonts/  pass-designs/  logback-spring.xml
    └── test/
        ├── java/in/codebasket/cbwa/        integration tests by feature (real MySQL 8) + unit tests
        └── resources/application-test.properties
```

### Where does a new class go?
| It is… | Put it in |
|---|---|
| a JPA `@Entity` | `common/entity` |
| a Spring Data repository | `common/repository` |
| an enum / fixed key | `common/constants` |
| a static helper with no Spring bean | `common/utils` |
| a controller | by URL: `/api/admin/**` → `admin/controller`, `/api/partner/**`, `/api/v1/**`, partner public pages → `partner/controller`, `/api/webhooks/**` → `webhook/controller`, staff & roles → `staff/controller`, `/api/auth`, `/api/me` → `security` |
| a service used by one role only | that role's `service/` |
| a service used by several roles | `common/service/<domain>/` |
| a scheduled job | `common/job` |
| a request/response record | that role's `dto/` (several roles → `common/dto`) |

A controller serves **one** role: an admin endpoint for a partner feature gets its own `Admin…Controller` (e.g.
`AdminTemplateController` + `TemplateController`). Classes used across packages are `public`.

## 3. Frontend structure
```
frontend/
├── index.html  vite.config.js  tailwind.config.js  postcss.config.js  package.json  .env.example
└── src/
    ├── main.jsx  App.jsx  router.jsx
    ├── api/            axios.js (JWT interceptor + silent refresh queue), endpoints/*.js (one file per module), queryClient.js
    ├── auth/           AuthProvider.jsx, useAuth.js, ProtectedRoute.jsx, RoleRoute.jsx, PermissionGate.jsx, tokenStore.js
    ├── components/
    │   ├── ui/         (reusable library listed in 04-screens-routes.md)
    │   ├── layout/     AdminLayout, PartnerLayout, Sidebar, TopBar, BottomNav, ImpersonationBanner, LowBalanceBanner
    │   └── whatsapp/   WhatsAppPreview, TemplateBuilder parts, StatusTicks, QualityBadge
    ├── features/       admin/{dashboard, partners, topups, payments, pricing, whatsapp, templates, messages, campaigns,
    │                   reports, settings, staff, audit}, partner/{dashboard, wallet, senders, send, campaigns, contacts,
    │                   templates, inbox, reports, developer, profile}, auth/
    │                   — each feature: pages/, components/, hooks/ (TanStack Query hooks), schemas.js (Zod)
    ├── hooks/          useMediaQuery, useDebounce, useTheme, useDisclosure
    ├── lib/            format.js (₹ en-IN, IST dates), constants.js, statusColors.js, metaGlossary.js, fbSdk.js, razorpay.js
    ├── styles/         index.css (Tailwind layers, CSS variables for light/dark)
    └── pages/          NotFound, Forbidden, ServerError
```

## 4. Naming conventions
| Item | Convention | Example |
|---|---|---|
| Java packages | lowercase, by role then layer | `in.codebasket.cbwa.partner.controller`, `in.codebasket.cbwa.common.service.wallet` |
| Classes | PascalCase + role suffix | `WalletService`, `WalletController`, `WalletTransactionRepository`, `WalletTxnDto`, `WalletMapper` |
| Request/response DTOs | `XxxRequest`, `XxxResponse` / `XxxDto` | `CreatePartnerRequest` |
| Enums | PascalCase type, UPPER_SNAKE values | `ChargeStatus.HELD` |
| DB tables / columns | snake_case plural tables | `wallet_transactions.balance_after` |
| Flyway | `V{n}__{phase}_{description}.sql` | `V4__phase4_wallet_topups.sql` |
| REST paths | kebab-case plural nouns, actions as sub-resource verbs | `/api/admin/topup-requests/{id}/approve` |
| JSON fields | camelCase | `balanceAfter` |
| Error codes | UPPER_SNAKE, module-prefixed | `TOPUP_DUPLICATE_UTR` |
| React components | PascalCase files `.jsx` | `DataTable.jsx` |
| Hooks | `useXxx` | `useWallet()` |
| Query keys | arrays `[module, entity, params]` | `['partner','wallet']` |
| Env vars | `CBWA_` prefix (backend), `VITE_` prefix (frontend) | `CBWA_DB_URL` |
| Git branches | see §6 | |
| Commits | Conventional Commits | `feat(wallet): add hold/release` |

Code standards: Java — constructor injection (`@RequiredArgsConstructor`), no field injection, services return DTOs, `@Transactional(readOnly=true)` on reads, no `Optional.get()` without check, no business logic in controllers. Logs: JSON in prod (logstash encoder), MDC `requestId`, `userId`, `partnerId`; `MaskingConverter` masks tokens/PIN/secrets/UPI. Frontend — ESLint + Prettier, no inline secrets, all forms via RHF + Zod, every query handles loading/error/empty.

## 5. Environment variables (`.env.example`, loaded by systemd `EnvironmentFile=/etc/cbwa/cbwa.env`)
```
# ---- Backend core ----
SPRING_PROFILES_ACTIVE=prod
CBWA_SERVER_PORT=8080
CBWA_PUBLIC_BASE_URL=https://wa.example.com            # used for webhook URLs, links in emails
CBWA_FRONTEND_URL=https://wa.example.com
CBWA_CORS_ALLOWED_ORIGINS=https://wa.example.com

# ---- Database ----
CBWA_DB_URL=jdbc:mysql://127.0.0.1:3306/cbwa?useSSL=false&serverTimezone=UTC&connectionTimeZone=UTC&characterEncoding=utf8
CBWA_DB_USERNAME=cbwa_app
CBWA_DB_PASSWORD=
CBWA_DB_POOL_MAX=20

# ---- Security ----
CBWA_JWT_SECRET=                    # base64, >= 64 bytes:  openssl rand -base64 64
CBWA_JWT_ACCESS_TTL_MINUTES=15
CBWA_JWT_REFRESH_TTL_DAYS=7
CBWA_ENCRYPTION_KEY=                # base64 32 bytes:      openssl rand -base64 32
CBWA_API_SECRET_PEPPER=             # base64 32 bytes
CBWA_SEED_ADMIN_EMAIL=admin@example.com
CBWA_SEED_ADMIN_PASSWORD=           # used only on first start, must be changed at first login
CBWA_SWAGGER_ENABLED=false

# ---- Storage ----
CBWA_STORAGE_PATH=/var/lib/cbwa/storage
CBWA_MAX_UPLOAD_MB=16               # absolute ceiling; per-type limits in Settings

# ---- Mail bootstrap (overridden by SMTP settings once saved) ----
CBWA_MAIL_HOST=
CBWA_MAIL_PORT=587
CBWA_MAIL_USERNAME=
CBWA_MAIL_PASSWORD=
CBWA_MAIL_FROM=no-reply@example.com

# ---- Workers ----
CBWA_QUEUE_WORKER_THREADS=8
CBWA_QUEUE_BATCH_SIZE=50

# ---- Logging ----
CBWA_LOG_PATH=/var/log/cbwa
CBWA_LOG_LEVEL=INFO

# ---- Frontend (frontend/.env.production) ----
VITE_API_BASE_URL=/api
VITE_APP_NAME=CB WhatsApp Gateway
```
Meta App ID/Secret, Embedded Signup config ID, verify token, Razorpay keys, GST %, SMTP and all prices are **not** env variables — they are entered by admin in Settings and stored encrypted in DB (Part L).

## 6. Git branching
- `main` — production; tagged releases `v1.0.0`; deploy only from tags.
- `develop` — integration branch.
- `feature/phase-<n>-<short>` (e.g. `feature/phase-4-wallet`) → PR into `develop` (CI: `mvn verify`, `npm run lint && npm run build`).
- `release/x.y` from `develop` for stabilisation → merged to `main` + back to `develop`.
- `hotfix/<short>` from `main` → `main` + `develop`.
- One PR per phase minimum; PR must include DoD checklist from the phase doc.
- (This session works on `claude/keen-shannon-baaobn` as instructed; it can be renamed/merged into `develop` when you set up the remote branches.)
