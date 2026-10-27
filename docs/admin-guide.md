# Admin Guide — CB WhatsApp Gateway

Plain-language guide for CodeBasket staff. Every money action asks for confirmation and is recorded in the
**Audit log**.

## Daily checklist
1. **Dashboard** — check pending top-ups, failed messages and red/yellow sender alerts.
2. **Top-up requests → Pending** — open the screenshot, match the UTR with the bank statement, **Approve** (wallet
   is credited instantly, partner gets an email) or **Reject** with a reason.
3. **WhatsApp → All senders** — anything paused or with low quality? Open it to see why.

## Partners
- **Add partner**: business name, contact, **mobile number (their login)**, email (for codes and notices), GSTIN/PAN,
  address, rate plan, "Allow own number". A temporary password is emailed; they must change it at first login.
  Each mobile number can belong to only one user (admin, staff or partner).
- **Suspend**: blocks login and API, pauses running campaigns. **Activate** to undo.
- **Wallet tab**: balance, money on hold (messages in flight), full passbook. **Credit / Debit** needs a remark.
  Debit can never make the balance negative.
- **Login as partner**: see exactly what the partner sees (purple banner on top). Everything you do is audited
  with your name. Click **Exit** in the banner to return.
- **Senders tab**: assign one of our numbers. One number belongs to one partner at a time (replies must go to
  exactly one inbox).

## Money
- **Credit packs**: e.g. "Pay ₹1,000 get 10% extra". Only active packs are shown to partners.
- **Payments**: online UPI/card payments via Razorpay. A wallet is credited only after the server verifies the
  payment; repeated webhooks never credit twice. **Reconcile** asks Razorpay again for stuck payments.
- **Invoices**: created automatically when GST is enabled (Settings → GST & invoice). CGST+SGST when the partner
  is in your state, otherwise IGST.

## Pricing (Rate plans)
- Prices per message **category** (Marketing, Utility, Authentication, Authentication-International) and
  **country** (`IN` for India, `*` for all other countries).
- **Session message fee**: price of normal replies inside the 24-hour window (Meta does not charge for these).
- **Platform fee** (Tech Provider mode, partner's own number): Meta bills the partner directly; we charge only this
  fee per message and/or per number per month.
- One plan is the **default** for new partners. Custom plans can be cloned for a single partner.
- If a price is missing, sending to that category/country is blocked with a clear message.

## Charging rule (Settings → Charging)
- **Charge on DELIVERED** (default): money is held when sending; charged when delivered; returned if failed.
- **Charge on SENT**: charged when WhatsApp accepts and sends it; no refund if delivery later fails.

## WhatsApp
- **Platform settings**: Meta App ID/Secret, Embedded Signup configuration ID, verify token, Graph version,
  billing mode. **Test connection** checks everything.
- **Our WABAs**: add our WhatsApp Business Account with a system-user token; numbers are imported.
- **Number detail**: Business profile (photo, about, website), Verification (request/verify code, register,
  2-step PIN), Health (quality rating, messaging limit), Analytics, QR codes, Welcome message & ice breakers,
  Block list.
- **Quality drop / restriction**: the number is paused automatically, its campaigns are paused and the partner
  and admins are notified. Fix the cause, then un-pause from the number page.
- **Manual connect**: connect a partner's number with WABA ID + phone number ID + permanent token when Embedded
  Signup is not possible.

## Templates
- Templates for **our numbers** go to the **Review queue** first. Check the text, then **Approve** (sent to Meta for
  approval) or **Reject** with a reason. Templates on a partner's own number go straight to Meta.
- Meta can later **pause** or **disable** a template for low quality — partners are notified automatically.

## AI replies (Settings → AI)
- Paste the **Anthropic API key** (console.anthropic.com → API keys; add billing credit there), switch **Enabled** on.
- **Model**: keep `claude-haiku-5-5` (cheapest). **Fee per reply**: what partners pay per AI answer (default ₹0.10).
  Our real cost is about ₹0.01–0.03 per reply thanks to short answers and prompt caching.
- **Max output tokens** (400), **history messages** (6) and **max replies per chat per day** (20) keep costs low.
- Usage across all partners: `GET /api/admin/ai/usage` (same data as the partner Usage tab, with partner names).

## Reports
Partner usage, category and country breakdown, revenue (gateway collections, manual top-ups, GST, message
charges, platform fees) and **Meta reconciliation** (Meta cost vs our charges = profit). Export Excel or PDF.

## Staff & security
- **Staff**: add team members and give them a role. Built-in roles: Super Admin (everything), Support (read-only),
  Finance (wallets, top-ups, payments, reports). Create your own roles with the permission matrix.
- **Settings → Security**: require 2-step login (email code) for all admins, failed-login lockout.
- **Audit log**: who changed what, when, from which IP (old vs new values; secrets are masked).
