# Browser smoke tests (Playwright)

Used for the Phase 12 regression run. Requires the backend on :8080 and `npx vite preview --port 4173` in `frontend/`.

- `crawl.mjs <mobile> <password> admin|partner <width>` — opens every admin/partner screen, reports any API 4xx/5xx,
  JavaScript error, error state or horizontal scroll. Run at 390 (phone) and 1280 (desktop).
- `metastub.mjs` — a tiny local stand-in for graph.facebook.com (port 9900) for UI testing without real Meta
  credentials. Start the backend with `CBWA_META_GRAPH_BASE_URL=http://localhost:9900`. **Never use in production.**

```bash
npm i playwright@1.56.1   # uses the Chromium already installed for Playwright
node crawl.mjs 9876500099 '<password>' admin 390
```

## API & security checks
- **`backend/.../security/ApiTortureIT`** (runs with `mvn test`): discovers every route from Spring MVC and calls it
  as anonymous, API key, partner A, partner B, Support staff, Finance staff and super admin, with real / other-tenant
  / non-existent / hostile ids and empty, malformed, wrongly typed, oversized and hostile bodies and query params
  (~17,000 requests). Fails on any 5xx, stack trace, non-envelope error, missing 401/403, cross-tenant access or
  leaked secret. Report: `backend/target/api-torture-report.txt`.
- **`backend/.../security/SecurityAttacksIT`**: forged / alg-none / expired JWTs, disabled users, impersonation
  limits, password-change gate, login rate limit, OTP brute force, API key whitelist/disable/rate limit, malicious
  uploads, Meta and Razorpay webhook abuse, secret hygiene of settings responses.
- **`raw-http-checks.sh`**: real Tomcat checks (content types, chunked, HEAD/OPTIONS/TRACE, CORS, firewall,
  oversized URL/header/upload, actuator/API-docs exposure, security headers) against a running prod-profile JAR:
  `BASE=http://localhost:8080 ADMIN_MOBILE=... ADMIN_PASSWORD=... ./raw-http-checks.sh`
