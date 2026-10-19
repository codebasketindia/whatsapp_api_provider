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

## 2. Backend structure
```
backend/
├── pom.xml
└── src/
    ├── main/java/in/codebasket/cbwa/
    │   ├── CbwaApplication.java
    │   ├── config/                    SecurityConfig, WebClientConfig, AsyncConfig, SchedulingConfig,
    │   │                              OpenApiConfig, JacksonConfig, CorsProperties, AppProperties
    │   ├── common/
    │   │   ├── api/                   ApiResponse, PageResponse, ErrorCode, FieldErrorDto
    │   │   ├── exception/             AppException, NotFoundException, BusinessException, GlobalExceptionHandler
    │   │   ├── entity/                BaseEntity, AuditableEntity (JPA auditing)
    │   │   ├── crypto/                CryptoService (AES-GCM), HmacUtil, TokenGenerator
    │   │   ├── storage/               FileStorageService, StoredFile*, FileTypeValidator
    │   │   ├── sequence/              NumberSequenceService
    │   │   ├── util/                  TimeUtil (UTC/IST), PhoneUtil (libphonenumber), MoneyUtil, Ulid
    │   │   └── web/                   RequestIdFilter, ClientIpResolver
    │   ├── auth/      {controller, service, dto, security(JwtService, JwtAuthFilter, CurrentUser), entity, repository}
    │   ├── user/      {controller, service, dto, mapper, entity, repository}
    │   ├── partner/   …same sub-packages…
    │   ├── wallet/  topup/  pricing/  payment/{gateway/razorpay}  invoice/
    │   ├── meta/      {client(MetaGraphClient), dto, error(MetaErrorMapper), settings}
    │   ├── waba/  sender/  convtools/  template/  messaging/{queue, worker, charging, window}
    │   ├── webhook/  contact/  campaign/  publicapi/{filter, ratelimit}  partnerhook/
    │   ├── report/  settings/  audit/  notification/{email}
    │   └── job/                       (thin @Scheduled wrappers calling services)
    ├── main/resources/
    │   ├── application.yml  application-dev.yml  application-prod.yml
    │   ├── db/migration/              V1__init_schema.sql, V2__seed.sql, V3__… (one per phase)
    │   ├── templates/email/           *.html (Thymeleaf-free simple placeholders)
    │   ├── templates/pdf/             invoice layout constants
    │   └── logback-spring.xml
    └── test/java/in/codebasket/cbwa/  unit (Mockito) + integration (Testcontainers MySQL)
```
Each module: `controller/ service/ dto/ mapper/ entity/ repository/` (only what it needs).

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
| Java packages | lowercase | `in.codebasket.cbwa.wallet` |
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
