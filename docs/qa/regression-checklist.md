# Regression Checklist (Phase 12) — [QA & DEVOPS ENGINEER]

Run before every release on staging (Razorpay test mode, a Meta test number). Tick each line; any ✗ blocks the release.

## Automated
- [ ] `cd backend && CBWA_TEST_DB_URL=jdbc:mysql://127.0.0.1:3306/cbwa_test mvn verify` — all green
- [ ] `cd frontend && npm run build && npx vitest run` — all green
- [ ] `mvn org.owasp:dependency-check-maven:check` and `npm audit --omit=dev` — no HIGH/CRITICAL without a note
- [ ] OWASP ZAP baseline scan of `https://staging/` — no High alerts
- [ ] k6 `deploy/loadtest/send-messages.k6.js` — accepted > 99 %, p95 < 800 ms, queue drains (no PENDING > 5 min)

## Auth & roles
- [ ] Admin login, partner login land on their dashboards; partner opening `/admin` sees 403
- [ ] 5 wrong passwords → account locked for 15 min (configurable)
- [ ] Forgot password → email code → reset → old sessions logged out
- [ ] Admin 2FA on → login asks for email code
- [ ] Access token expiry (15 min) refreshes silently; refresh token reuse logs out
- [ ] Staff with Support role cannot approve top-ups (button hidden + API 403)

## Partners & wallet
- [ ] Create partner → welcome email → first login forces password change
- [ ] Manual credit ₹1,000 and debit ₹100 with remarks → passbook rows with correct before/after
- [ ] Debit more than balance → error, nothing changed
- [ ] Partner top-up request ₹1,000 (UTR X) → admin approves → balance +₹1,000, ledger row, email
- [ ] Same UTR again → "already used"; approving twice → "already processed"
- [ ] Suspend partner → cannot log in / API 403 / campaigns paused; activate → works again
- [ ] Login as partner → purple banner → actions audited with admin name → exit returns to admin

## Payments
- [ ] Buy pack with Razorpay test UPI `success@razorpay` → credited once (+bonus), invoice PDF correct (CGST/SGST vs IGST)
- [ ] Re-send the same Razorpay webhook (dashboard → Resend) → no second credit
- [ ] Payment failure (`failure@razorpay`) → FAILED, wallet unchanged
- [ ] Close checkout after paying → reconciliation job/"Reconcile" credits it

## WhatsApp setup
- [ ] Platform settings Test connection OK; webhook verified in Meta
- [ ] Add our WABA → numbers imported → assign to partner A; assigning same number to partner B blocked
- [ ] Partner B connects own number via Embedded Signup → CONNECTED with quality rating + next-steps list
- [ ] Coexistence onboarding (WhatsApp Business App number) → connected, history sync requested
- [ ] Business profile edit + photo shows on the phone
- [ ] QR code opens WhatsApp chat with the pre-filled text; short link works
- [ ] Welcome message / ice breakers visible on a fresh chat

## Templates
- [ ] Partner template on own number → PENDING → APPROVED by Meta (webhook updates status, partner notified)
- [ ] Template on our number → review queue → admin approve → submitted → APPROVED
- [ ] Invalid template (bad name, missing samples) → clear field errors

## Messaging & charging
- [ ] Send approved template → delivered to phone; ticks sent → delivered → read
- [ ] Wallet: HOLD on send, DEBIT on delivered (charge on DELIVERED); passbook shows them
- [ ] Message to a non-WhatsApp number → FAILED "Recipient is not on WhatsApp", hold returned
- [ ] Free-form text outside 24 h → blocked with "use an approved template"; after customer replies → allowed
- [ ] Insufficient balance → 402, nothing queued
- [ ] Customer replies STOP → opted out; marketing template to them blocked
- [ ] Inbox shows inbound photo/document; reply works; live update without refresh

## Campaigns
- [ ] 100-row CSV with 3 invalid, 2 duplicates, 1 opted-out → counts correct in preview, cost = valid × rate
- [ ] Send now → progress updates live; pause/resume/cancel work
- [ ] Failed rows refunded; report Excel matches screen numbers and passbook

## API & webhooks
- [ ] Create API key (secret shown once) → cURL send works; wrong secret 401; IP not in whitelist 403
- [ ] Rate limit → 429 with Retry-After
- [ ] Partner webhook receives `message.status` with valid `X-CB-Signature`; failing endpoint retried 5× then DEAD
- [ ] "Send test event" shows the response

## Reports & settings
- [ ] Dashboard counts equal message log counts for today; revenue equals ledger
- [ ] Reports export Excel/PDF open correctly; Meta reconciliation shows cost vs charge
- [ ] Every settings change appears in the Audit log with masked secrets

## Mobile (Android Chrome + iPhone Safari, 360–430 px)
- [ ] Login, dashboard, Add money (UPI intent opens UPI app), send message, inbox chat, campaign wizard,
      template builder preview — no horizontal scroll, buttons ≥ 44 px, bottom nav works, dark mode readable

## Operations
- [ ] `systemctl restart cbwa` → healthy < 60 s; `healthcheck.sh` restarts a stopped service
- [ ] Backup file created; restore on a scratch DB works
- [ ] Certificate valid; HTTP → HTTPS redirect; security headers present (securityheaders.com ≥ A)
