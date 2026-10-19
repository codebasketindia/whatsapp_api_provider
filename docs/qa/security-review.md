# Security Review (OWASP Top 10 basics) — [QA & DEVOPS ENGINEER]

| OWASP 2021 | Control in CB WhatsApp Gateway | Where |
|---|---|---|
| A01 Broken access control | Every endpoint behind Spring Security role rules (`/api/admin/**` ROLE_ADMIN, `/api/partner/**` ROLE_PARTNER, `/api/v1/public/**` API key) + `@PreAuthorize` permission per action; partner queries always filtered by the caller's partner id (other partners' ids → 404); files served only through `/api/files/{uid}` with owner check | `SecurityConfig`, controllers, `FileController` |
| A02 Cryptographic failures | TLS only (HSTS); BCrypt(12) passwords; AES-256-GCM for Meta tokens, PINs, gateway keys, SMTP password, webhook secrets (key from env); API secrets stored as keyed HMAC; refresh tokens stored as SHA-256 | `CryptoService`, `ApiSecretHasher`, `TokenService` |
| A03 Injection | JPA/Spring Data parameter binding, Criteria API specifications, native SQL only with bound parameters; React escapes output; email templates escape dynamic text | repositories, `EmailTemplates.esc` |
| A04 Insecure design | Ledger-based wallet with row locks and DB CHECKs; idempotency keys (UTR, gateway payment id, webhook event id, wamid+status); server-side payment verification; confirm dialogs for money actions | `WalletService`, payment & webhook modules |
| A05 Security misconfiguration | Prod profile disables Swagger, no stack traces in errors, security headers (Nginx + Spring), MySQL bound to localhost, firewall 22/80/443 only, systemd hardening | `application-prod.yml`, `deploy/` |
| A06 Vulnerable components | Pinned versions; `dependency-check-maven` and `npm audit` in the release checklist | `docs/qa/regression-checklist.md` |
| A07 Identification & auth failures | Login attempt limiting + lockout, per-IP rate limit, optional admin 2FA (email OTP, 5 attempts, 10 min), rotating refresh tokens with reuse detection, password policy, forced change of temporary passwords, sessions revoked on password change/suspension | `AuthService`, `OtpService`, `LoginRateLimiter` |
| A08 Software & data integrity | Webhook signatures verified (Meta `X-Hub-Signature-256`, Razorpay `X-Razorpay-Signature`) with constant-time compare before processing; outbound partner webhooks signed (`X-CB-Signature`) | webhook modules |
| A09 Logging & monitoring failures | Structured JSON logs with request id; masking of tokens/secrets/PINs/UPI ids; audit log for wallet adjustments, approvals, settings, login-as, sender connect/disconnect, API key changes; health check + restart cron | `MaskingConverter`, `AuditService` |
| A10 SSRF | Partner webhook URLs must be HTTPS and must not resolve to private/loopback/link-local ranges; the server only calls Meta, Razorpay and the partner's validated URL | partnerhook module |

Additional: file uploads validated by magic bytes (Apache Tika) and size, stored outside the web root with random
names; CORS restricted to the dashboard origin (public API allows any origin but needs key+secret); CSRF not
applicable (no cookies, bearer tokens only); SQL `CHECK (balance >= 0)` as last line of defence.
