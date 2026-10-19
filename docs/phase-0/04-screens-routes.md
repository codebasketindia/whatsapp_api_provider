# CB WhatsApp Gateway — Phase 0 · 04 Screens & Routes

Owner: [LEAD ARCHITECT] with [FRONTEND EXPERT ENGINEER]. One React SPA, one login. After login: `userType=ADMIN → /admin`, `PARTNER → /partner`. Wrong role → `/403`. `mustChangePassword` → forced `/change-password`.

## Public routes
| Route | Screen | API |
|---|---|---|
| `/login` | Login (email + password, show/hide, remember theme) | A1, ST6 |
| `/login/verify` | 2FA OTP (6 boxes, resend timer) | A2, A3 |
| `/forgot-password` | Email → OTP + new password (2 steps) | A6, A7 |
| `/change-password` | Forced change on first login | A10 |
| `/403`, `/404`, `/500` | Error pages with "Go to dashboard" | — |

## Admin — `/admin/*` (AdminLayout: collapsible sidebar, top bar with search + notifications bell + theme toggle + profile menu; mobile: hamburger drawer)
| Route | Screen | Permission | Phase |
|---|---|---|---|
| `/admin` | Dashboard (stat cards, messages & revenue charts, pending top-ups, sender alerts) | DASHBOARD_VIEW | 11 (shell in 1) |
| `/admin/partners` | Partners list (search, status filter, cards on mobile) | PARTNER_VIEW | 3 |
| `/admin/partners/new` | Add partner form | PARTNER_MANAGE | 3 |
| `/admin/partners/:id` | Partner detail — tabs **Profile · Wallet · Senders · Usage · API · Activity**; actions: Edit, Suspend/Activate, Login as partner, Credit/Debit wallet | PARTNER_VIEW | 3/4/6/10/11 |
| `/admin/partners/:id/edit` | Edit partner | PARTNER_MANAGE | 3 |
| `/admin/topups` | Top-up requests (tabs Pending/Approved/Rejected, screenshot viewer, approve/reject dialogs) | TOPUP_VIEW | 4 |
| `/admin/payments` | Gateway transactions, reconcile | PAYMENT_VIEW | 5 |
| `/admin/payments/:uid` | Payment detail + gateway logs | PAYMENT_VIEW | 5 |
| `/admin/invoices` | Invoices list + PDF | PAYMENT_VIEW | 5 |
| `/admin/pricing/credit-packs` | Credit packs (cards, drag sort) | PRICING_MANAGE | 4 |
| `/admin/pricing/rate-plans` | Rate plans list | PRICING_MANAGE | 6 |
| `/admin/pricing/rate-plans/:id` | Rate plan builder (category × country grid, fees) | PRICING_MANAGE | 6 |
| `/admin/whatsapp/settings` | Meta platform settings + Test connection + webhook URL copy | WHATSAPP_VIEW | 6 |
| `/admin/whatsapp/accounts` | Our WABAs + partner WABAs, token status | WHATSAPP_VIEW | 6 |
| `/admin/whatsapp/accounts/:id` | WABA detail (numbers, checklist, sync, subscribe) | WHATSAPP_VIEW | 6 |
| `/admin/whatsapp/connect` | Manual connect form | WHATSAPP_MANAGE | 6 |
| `/admin/whatsapp/senders` | All senders (platform + partner-own), quality badges, assign | WHATSAPP_VIEW | 6 |
| `/admin/whatsapp/senders/:id` | Number detail — tabs **Overview · Profile · Verification · Health · Analytics · QR codes · Welcome & Ice breakers · Block list** | WHATSAPP_VIEW | 6/7/11 |
| `/admin/templates` | All templates (filters by partner/status) | TEMPLATE_VIEW | 7 |
| `/admin/templates/review` | Review queue (preview + approve/reject) | TEMPLATE_REVIEW | 7 |
| `/admin/templates/new` | Template builder (platform) | TEMPLATE_REVIEW | 7 |
| `/admin/messages` | Message logs + export CSV | MESSAGE_LOG_VIEW | 8 |
| `/admin/messages/:uid` | Message detail drawer (status timeline, raw events) | MESSAGE_LOG_VIEW | 8 |
| `/admin/campaigns` | All campaigns, pause/cancel | CAMPAIGN_VIEW | 9 |
| `/admin/reports` | Reports hub — tabs **Partners · Categories · Countries · Revenue · Meta reconciliation**, export Excel/PDF | REPORT_VIEW | 11 |
| `/admin/settings` | Settings — tabs **Company · Payment gateway · GST & invoice · SMTP · Charging · Wallet · API limits · Security** | SETTINGS_MANAGE | 1→11 |
| `/admin/staff` | Staff list + add/edit | STAFF_MANAGE | 2 |
| `/admin/staff/roles` | Roles & permissions matrix | STAFF_MANAGE | 2 |
| `/admin/audit-logs` | Audit log viewer (diff view) | AUDIT_VIEW | 11 |
| `/admin/notifications` | Notifications center | any admin | 11 |
| `/admin/profile` | Profile, password, 2FA toggle | any admin | 2 |

## Partner — `/partner/*` (PartnerLayout: desktop sidebar; mobile **bottom nav** = Home · Send · Inbox · Wallet · More; sticky "Add Money" button; low-balance banner; impersonation banner when admin is logged in as partner)
| Route | Screen | Phase |
|---|---|---|
| `/partner` | Dashboard (big wallet card + Add Money, today's usage, delivery rate, sender health, 7/30-day chart) | 11 (shell in 1) |
| `/partner/wallet` | Wallet — balance card, tabs **Passbook · My requests · Payments · Invoices** | 4/5 |
| `/partner/wallet/add` | Add Money bottom-sheet/page: **Pay by UPI · Buy a pack · I already paid (manual request)** | 4/5 |
| `/partner/wallet/payment/:uid` | Payment success / failure / pending screen | 5 |
| `/partner/senders` | My senders (cards) + "Connect WhatsApp Number" | 6 |
| `/partner/senders/connect` | Embedded Signup wizard (choose New / Existing / Keep WhatsApp Business App → Facebook popup → set PIN → progress steps → "What to do next" checklist) | 6 |
| `/partner/senders/:id` | Number detail — tabs **Overview · Business profile · Verification & PIN · Health · QR codes · Welcome & Ice breakers · Block list** | 6/7 |
| `/partner/send` | Send Message (sender → recipient → template or free text → variables → live WhatsApp preview → cost → Send) | 8 |
| `/partner/campaigns` | Campaign list | 9 |
| `/partner/campaigns/new` | Wizard: **1 Upload · 2 Map columns · 3 Preview & cost · 4 Schedule / Send now** | 9 |
| `/partner/campaigns/:id` | Live progress + report download | 9 |
| `/partner/contacts` | Contacts (search, tags, opt-out badge) | 9 |
| `/partner/contacts/import` | Import wizard + validation report | 9 |
| `/partner/contacts/groups` | Groups | 9 |
| `/partner/contacts/opt-outs` | Opt-outs + keyword settings | 7/9 |
| `/partner/templates` | Templates list with status & quality badges | 7 |
| `/partner/templates/new`, `/partner/templates/:id/edit` | Template builder with live preview | 7 |
| `/partner/templates/library` | Template library | 7 |
| `/partner/templates/:id` | Template detail + analytics | 7 |
| `/partner/inbox` | Conversations list (mobile: list → chat full screen) | 8 |
| `/partner/inbox/:senderId/:waId` | Chat with window timer, reply composer, template picker when closed | 8 |
| `/partner/reports` | Message logs with filters + export, usage summary | 8/11 |
| `/partner/developer` | Developer — tabs **API keys · Webhook · Delivery logs · API docs (cURL/PHP/Node/Java/Python)** | 10 |
| `/partner/profile` | Profile & password | 2 |
| `/partner/notifications` | Notifications & preferences | 11 |
| `/partner/support` | Support contact info + FAQ | 11 |
| `/partner/more` | Mobile "More" menu | 1 |

## Reusable component library (Phase 1, `src/components/ui`)
Button, IconButton, Input, PasswordInput, Select, Textarea, Checkbox, Switch, Radio cards, Modal, Drawer/BottomSheet (auto by breakpoint), DataTable (→ cards < 768 px), Badge/StatusBadge, StatCard, EmptyState, Skeleton, ConfirmDialog (money/destructive, optional typed confirmation), FileUpload (drag-drop, size/type checks, preview), Pagination, Tabs (scrollable on mobile), DateRangePicker (IST presets: Today, 7d, 30d, This month), CopyButton, Money (₹1,23,456.00), DateTime (IST), Tooltip/InfoTip (Meta terms glossary), WhatsAppPreview (chat bubble: header media, body with variables, footer, buttons, list, CTA), PhoneInput (+91 default), SearchInput (debounced), Spinner, PageHeader, Card, Alert/Banner, Stepper, ProgressBar, Avatar, Dropdown menu.

Status badge colours (global): green = APPROVED/DELIVERED/READ/PAID/CONNECTED/ACTIVE/GREEN; amber = PENDING/QUEUED/SENT/SCHEDULED/YELLOW/PAUSED; red = FAILED/REJECTED/SUSPENDED/RED/DISABLED; gray = DRAFT/CANCELLED/UNKNOWN; blue = RUNNING/INFO.
