# CB WhatsApp Gateway — Phase 0 · 03 API Contract (v1.0)

Owner: [LEAD ARCHITECT]. Backend implements exactly; frontend consumes exactly. Changes only via a new contract version in this file + changelog at the bottom.

## 1. Global rules

**Base URL**: `https://<domain>/api`. JSON UTF-8. Dates in responses are ISO-8601 UTC (`2026-10-19T08:30:00Z`); dates in query params are `YYYY-MM-DD` interpreted in **IST**. Money is a JSON **string** with 2–4 decimals (`"1000.0000"`) to avoid float errors.

**Envelope (every response, incl. errors)**
```json
{ "success": true, "code": "OK", "message": "Partner created", "data": { }, "errors": null }
```
```json
{ "success": false, "code": "VALIDATION_FAILED", "message": "Please fix the highlighted fields",
  "data": null, "errors": [ { "field": "amount", "message": "Minimum ₹100" } ] }
```
Response header `X-Request-Id` on every response (log correlation).

**Pagination** (every list): query `page` (0-based, default 0), `size` (default 20, max 100), `sort=field,asc|desc`. `data`:
```json
{ "content": [ ], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }
```

**Auth**: `Authorization: Bearer <accessToken>` for `/api/admin/**`, `/api/partner/**`, `/api/me/**`, `/api/files/**`. Public API uses `X-API-KEY` + `X-API-SECRET`.

**Role column legend**: `PUB` = no auth · `AUTH` = any logged-in user · `ADM:<PERM>` = admin user with that permission (SUPER_ADMIN has all) · `PTR` = API partner (also allowed for admin impersonating) · `KEY` = public API key.

## 2. Error codes

| code | HTTP | meaning |
|---|---|---|
| OK / CREATED | 200 / 201 | success |
| VALIDATION_FAILED | 400 | bean validation; `errors[]` filled |
| BAD_REQUEST | 400 | malformed JSON / params |
| AUTH_INVALID_CREDENTIALS | 401 | wrong email/password |
| AUTH_ACCOUNT_LOCKED | 423 | too many attempts; `data.lockedUntil` |
| AUTH_ACCOUNT_DISABLED | 403 | user disabled / partner suspended |
| AUTH_TOKEN_EXPIRED | 401 | access token expired → frontend refreshes |
| AUTH_TOKEN_INVALID | 401 | |
| AUTH_REFRESH_INVALID | 401 | refresh revoked/expired/reused → logout |
| AUTH_2FA_REQUIRED | 200 | login step 1 OK, OTP needed (`success:true`) |
| AUTH_OTP_INVALID / AUTH_OTP_EXPIRED | 400 | |
| AUTH_PASSWORD_POLICY | 400 | min 8, upper, lower, digit |
| AUTH_PASSWORD_CHANGE_REQUIRED | 403 | must change before other calls |
| FORBIDDEN | 403 | role/permission |
| NOT_FOUND | 404 | |
| CONFLICT | 409 | generic unique/version conflict |
| RATE_LIMITED | 429 | `Retry-After` header |
| FILE_TOO_LARGE / FILE_TYPE_NOT_ALLOWED | 400 | |
| WALLET_INSUFFICIENT_BALANCE | 402 | `data: {required, available}` |
| WALLET_NEGATIVE_NOT_ALLOWED | 400 | admin debit > balance |
| TOPUP_DUPLICATE_UTR | 409 | |
| TOPUP_ALREADY_PROCESSED | 409 | |
| PAYMENT_GATEWAY_NOT_CONFIGURED | 503 | |
| PAYMENT_SIGNATURE_INVALID | 400 | |
| PAYMENT_AMOUNT_OUT_OF_RANGE | 400 | |
| RATE_NOT_CONFIGURED | 422 | no price for category/country |
| META_NOT_CONFIGURED | 503 | |
| META_API_ERROR | 502 | `data: {metaCode, metaSubcode, friendly}` |
| META_TOKEN_INVALID | 409 | reconnect needed |
| OWN_NUMBER_NOT_ALLOWED | 403 | |
| SENDER_NOT_AVAILABLE | 422 | not assigned / disconnected |
| SENDER_PAUSED | 422 | |
| SENDER_DAILY_CAP_REACHED | 429 | |
| TEMPLATE_NOT_APPROVED | 422 | |
| TEMPLATE_INVALID_STATE | 409 | edit/delete not allowed in status |
| TEMPLATE_VARIABLES_MISMATCH | 400 | |
| WINDOW_CLOSED | 422 | 24h window closed → template required |
| RECIPIENT_INVALID | 400 | not a valid phone |
| RECIPIENT_OPTED_OUT | 422 | marketing to opted-out contact |
| RECIPIENT_BLOCKED | 422 | |
| CAMPAIGN_INVALID_STATE | 409 | |
| IMPORT_INVALID_FILE | 400 | |
| API_KEY_INVALID | 401 | |
| API_KEY_DISABLED | 403 | |
| IP_NOT_WHITELISTED | 403 | |
| PARTNER_SUSPENDED | 403 | |
| WEBHOOK_SIGNATURE_INVALID | 401 | inbound webhooks |
| INTERNAL_ERROR | 500 | never leaks stack traces |

## 3. Common DTOs (referenced below)

- **UserDto** `{ uid, name, email, mobile, userType: ADMIN|PARTNER, role:{code,name}, permissions:[..], isSuperAdmin, twoFactorEnabled, mustChangePassword, theme, partner?:{uid, partnerCode, businessName, status}, impersonatedBy?:{uid,name} }`
- **TokenDto** `{ accessToken, refreshToken, accessTokenExpiresIn: 900, refreshTokenExpiresIn: 604800, user: UserDto }`
- **PartnerDto** `{ id, uid, partnerCode, businessName, contactName, email, mobile, gstin, pan, address:{line1,line2,city,state,stateCode,pincode,country}, website, status, suspendReason, kycVerified, allowOwnNumber, ratePlan:{id,name}, wallet:{balance, held}, createdAt }`
- **WalletDto** `{ balance, held, lowBalanceThreshold, isLow, currency }`
- **WalletTxnDto** `{ uid, type, amount, balanceBefore, balanceAfter, heldBefore, heldAfter, referenceType, referenceId, remarks, createdBy?:{name}, createdAt }`
- **TopupRequestDto** `{ id, requestNo, partner:{id,businessName}, amount, paymentMode, utr, paymentDate, screenshotUrl, note, status, rejectReason, reviewedBy?:{name}, reviewedAt, createdAt }`
- **CreditPackDto** `{ id, name, description, price, bonusType, bonusValue, bonusAmount, creditAmount, isPopular, isActive, sortOrder }`
- **RatePlanDto** `{ id, name, description, isDefault, isCustom, partnerId, sessionMessageFee, platformFeePerMessage, platformFeeMonthlyPerNumber, status, items:[{countryCode, category, price}] }`
- **PaymentOrderDto** `{ uid, gateway, gatewayOrderId, gatewayPaymentId, creditPack?, baseAmount, bonusAmount, gstPercent, gstAmount, totalAmount, creditAmount, status, method, failureReason, verifiedVia, paidAt, invoice?:{id, invoiceNo}, createdAt }`
- **InvoiceDto** `{ id, invoiceNo, invoiceDate, partner:{id,businessName,gstin}, taxableAmount, cgst, sgst, igst, total, pdfUrl }`
- **WabaDto** `{ id, wabaId, ownerType, partner?, name, businessId, accountReviewStatus, businessVerificationStatus, paymentMethodAttached, tokenStatus, tokenCheckedAt, webhookSubscribed, onboardingType, billingMode, creditLineShared, status, connectedAt }`
- **SenderDto** `{ id, phoneNumberId, displayPhoneNumber, verifiedName, nameStatus, newNameStatus, qualityRating, messagingLimitTier, throughputLevel, maxMps, metaStatus, health:{canSendMessage, errors[]}, senderType, isCoexistence, isRegistered, isPaused, pauseReason, dailySendCap, sentToday, status, waba:{id, wabaId, name, tokenStatus}, partner?:{id,businessName}, isDefault, connectedAt, lastSyncedAt }`
- **ChecklistDto** `{ items:[{ key: PAYMENT_METHOD|BUSINESS_VERIFICATION|DISPLAY_NAME|NUMBER_REGISTERED|WEBHOOK|TWO_STEP_PIN, status: DONE|PENDING|ACTION_REQUIRED, title, help, actionUrl? }] }`
- **TemplateDto** `{ id, uid, name, language, category, previousCategory, parameterFormat, status, rejectedReason, qualityScore, waba:{id,wabaId}, partner?, libraryTemplateName, components:[ComponentDto], adminReviewNote, submittedAt, lastSyncedAt, createdAt }`
- **ComponentDto** `{ type: HEADER|BODY|FOOTER|BUTTONS, format?, text?, example?, buttons?:[{type: QUICK_REPLY|URL|PHONE_NUMBER|COPY_CODE|OTP, text, url?, phoneNumber?, example?, otpType?: COPY_CODE|ONE_TAP|ZERO_TAP, packageName?, signatureHash?}], headerMediaFileUid?, addSecurityRecommendation?, codeExpirationMinutes? }`
- **MessageDto** `{ uid, direction, sender:{id, displayPhoneNumber}, to|from (waId), contact?:{id,name}, type, category, template?:{name,language}, preview, payload, contextUid?, wamid, status, errorCode, errorMessage, price, chargeStatus, billable, source, clientReference, campaign?:{uid,name}, createdAt, sentAt, deliveredAt, readAt, failedAt, statusHistory?:[{status, at, errorCode}] }`
- **SendMessageRequest** (dashboard & public API, same shape)
```json
{ "senderId": 12,              // public API: "from": "919876543210" (phone) or senderId
  "to": "919812345678",
  "type": "text|image|video|audio|document|sticker|location|contacts|reaction|interactive|template",
  "replyToUid": null,           // context (dashboard) / "replyToWamid" (public API)
  "text": { "body": "Hi", "previewUrl": true },
  "media": { "fileUid": "...", "metaMediaId": null, "link": null, "caption": "", "filename": "" },
  "location": { "latitude": 0, "longitude": 0, "name": "", "address": "" },
  "contacts": [ { /* Meta contacts object */ } ],
  "reaction": { "messageUid": "...", "emoji": "👍" },
  "interactive": { /* Meta interactive object: button | list | cta_url | location_request_message */ },
  "template": { "templateId": 5, "name": "order_update", "language": "en",
                "variables": { "body": ["Ravi","#123"], "header": {"type":"image","fileUid":"..."},
                               "buttons": [{"index":0,"subType":"url","value":"abc"}] },
                "namedVariables": { "customer_name": "Ravi" } },
  "clientReference": "INV-889" }
```
- **CostEstimateDto** `{ category, countryBreakdown:[{countryCode, count, unitPrice, subtotal}], recipients, total, available, balanceAfter, sufficient }`
- **CampaignDto** `{ id, uid, name, sender, template, audienceType, status, pauseReason, scheduledAt, startedAt, completedAt, counts:{total, valid, invalid, skipped, queued, sent, delivered, read, failed}, unitPrice, estimatedCost, chargedAmount, refundedAmount, createdAt }`
- **ContactDto** `{ id, phone, name, email, countryCode, tags[], attributes{}, optedOut, isBlocked, lastInboundAt, source, groups:[{id,name}], createdAt }`
- **NotificationDto** `{ id, type, severity, title, body, link, isRead, createdAt }`
- **ApiKeyDto** `{ id, name, apiKey, secretLast4, ipWhitelist[], rateLimitPerMinute, status, lastUsedAt, lastUsedIp, createdAt }` (+ `apiSecret` only in create/regenerate response)

---

## 4. Endpoints

### 4.1 Auth & profile
| # | Method | URL | Role | Request | Response `data` | Errors |
|---|---|---|---|---|---|---|
| A1 | POST | /auth/login | PUB (rate-limited 10/min/IP) | `{email, password}` | `TokenDto` **or** `{twoFactorRequired:true, challengeId, maskedEmail}` | AUTH_INVALID_CREDENTIALS, AUTH_ACCOUNT_LOCKED, AUTH_ACCOUNT_DISABLED, RATE_LIMITED |
| A2 | POST | /auth/login/2fa | PUB | `{challengeId, otp}` | `TokenDto` | AUTH_OTP_INVALID, AUTH_OTP_EXPIRED |
| A3 | POST | /auth/otp/resend | PUB | `{challengeId}` | `{resendAfterSeconds}` | RATE_LIMITED |
| A4 | POST | /auth/refresh | PUB | `{refreshToken}` | `TokenDto` (rotated) | AUTH_REFRESH_INVALID |
| A5 | POST | /auth/logout | AUTH | `{refreshToken}` | null | — |
| A6 | POST | /auth/forgot-password | PUB | `{email}` | `{challengeId}` (always 200, no user enumeration) | RATE_LIMITED |
| A7 | POST | /auth/reset-password | PUB | `{challengeId, otp, newPassword}` | null (all sessions revoked) | AUTH_OTP_INVALID, AUTH_PASSWORD_POLICY |
| A8 | GET | /me | AUTH | — | `UserDto` | |
| A9 | PUT | /me | AUTH | `{name, mobile, theme}` | `UserDto` | VALIDATION_FAILED |
| A10 | POST | /me/change-password | AUTH | `{currentPassword, newPassword}` | null (other sessions revoked) | AUTH_INVALID_CREDENTIALS, AUTH_PASSWORD_POLICY |
| A11 | PUT | /me/two-factor | ADM:any | `{enabled, password}` | `UserDto` | AUTH_INVALID_CREDENTIALS |
| A12 | GET | /me/notifications | AUTH | `?unreadOnly&page&size` | Page<`NotificationDto`> | |
| A13 | GET | /me/notifications/unread-count | AUTH | — | `{count}` | |
| A14 | POST | /me/notifications/{id}/read | AUTH | — | null | NOT_FOUND |
| A15 | POST | /me/notifications/read-all | AUTH | — | `{updated}` | |
| A16 | GET | /files/{uid} | AUTH (owner partner or admin) | — | binary stream (`Content-Disposition`) | NOT_FOUND, FORBIDDEN |

### 4.2 Admin — staff, roles, audit
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| S1 | GET | /admin/permissions | ADM:STAFF_MANAGE | — | `[{code, module, description}]` | |
| S2 | GET | /admin/roles | ADM:STAFF_MANAGE | — | `[{id, code, name, isSystem, permissions[], userCount}]` | |
| S3 | POST | /admin/roles | ADM:STAFF_MANAGE | `{code, name, description, permissions[]}` | role | CONFLICT |
| S4 | PUT | /admin/roles/{id} | ADM:STAFF_MANAGE | same | role | FORBIDDEN (system role) |
| S5 | DELETE | /admin/roles/{id} | ADM:STAFF_MANAGE | — | null | CONFLICT (in use) |
| S6 | GET | /admin/staff | ADM:STAFF_MANAGE | `?search&status&page` | Page<UserDto> | |
| S7 | POST | /admin/staff | ADM:STAFF_MANAGE | `{name, email, mobile, roleId, password?}` (no password → emailed temp password) | UserDto | CONFLICT |
| S8 | PUT | /admin/staff/{uid} | ADM:STAFF_MANAGE | `{name, mobile, roleId}` | UserDto | |
| S9 | PATCH | /admin/staff/{uid}/status | ADM:STAFF_MANAGE | `{status}` | UserDto | FORBIDDEN (self/super admin) |
| S10 | POST | /admin/staff/{uid}/reset-password | ADM:STAFF_MANAGE | — | null (temp password emailed) | |
| S11 | GET | /admin/audit-logs | ADM:AUDIT_VIEW | `?actorUid&partnerId&action&entityType&entityId&from&to&page` | Page<`{id, actor, impersonator, action, entityType, entityId, oldValue, newValue, ip, createdAt}`> | |

### 4.3 Admin — partners
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| P1 | GET | /admin/partners | ADM:PARTNER_VIEW | `?search&status&ratePlanId&lowBalance&page&size&sort` | Page<PartnerDto> | |
| P2 | POST | /admin/partners | ADM:PARTNER_MANAGE | `{businessName, contactName, email, mobile, gstin?, pan?, address{}, website?, ratePlanId, allowOwnNumber, lowBalanceThreshold?, password?, sendWelcomeEmail}` | PartnerDto (+wallet created) | CONFLICT (email), VALIDATION_FAILED |
| P3 | GET | /admin/partners/{id} | ADM:PARTNER_VIEW | — | PartnerDto + `{settings, stats:{senders, messagesToday, messagesMonth, spendMonth}}` | NOT_FOUND |
| P4 | PUT | /admin/partners/{id} | ADM:PARTNER_MANAGE | P2 fields minus password | PartnerDto | |
| P5 | PATCH | /admin/partners/{id}/status | ADM:PARTNER_MANAGE | `{status: ACTIVE|SUSPENDED, reason}` | PartnerDto (suspend revokes sessions, pauses campaigns, disables keys) | |
| P6 | PATCH | /admin/partners/{id}/rate-plan | ADM:PRICING_MANAGE | `{ratePlanId}` | PartnerDto | |
| P7 | PATCH | /admin/partners/{id}/own-number | ADM:PARTNER_MANAGE | `{allowed}` | PartnerDto | |
| P8 | PATCH | /admin/partners/{id}/kyc | ADM:PARTNER_MANAGE | `{kycVerified}` | PartnerDto | |
| P9 | POST | /admin/partners/{id}/reset-password | ADM:PARTNER_MANAGE | — | null | |
| P10 | POST | /admin/partners/{id}/login-as | ADM:LOGIN_AS_PARTNER | `{reason}` | TokenDto (partner user, `impersonatedBy` set; refresh 1 h only) — audited | PARTNER_SUSPENDED |
| P11 | GET | /admin/partners/{id}/senders | ADM:PARTNER_VIEW | — | `[SenderDto]` | |
| P12 | POST | /admin/partners/{id}/senders | ADM:WHATSAPP_MANAGE | `{phoneNumberDbId, isDefault}` | SenderDto | CONFLICT (assigned elsewhere), SENDER_NOT_AVAILABLE |
| P13 | DELETE | /admin/partners/{id}/senders/{senderId} | ADM:WHATSAPP_MANAGE | — | null (history kept) | |
| P14 | GET | /admin/partners/{id}/usage | ADM:PARTNER_VIEW | `?from&to&groupBy=day|category|country|sender` | `[{key, sent, delivered, read, failed, charged}]` | |
| P15 | GET | /admin/partners/{id}/activity | ADM:AUDIT_VIEW | `?page` | Page<audit> | |
| P16 | GET | /admin/partners/{id}/api-keys | ADM:PARTNER_VIEW | — | `[ApiKeyDto]` (no secrets) | |
| P17 | PATCH | /admin/partners/{id}/api-keys/{keyId} | ADM:PARTNER_MANAGE | `{status?, rateLimitPerMinute?}` | ApiKeyDto | |

### 4.4 Wallet, top-ups, credit packs, payments, invoices
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| W1 | GET | /admin/partners/{id}/wallet | ADM:WALLET_VIEW | — | WalletDto | |
| W2 | GET | /admin/partners/{id}/wallet/transactions | ADM:WALLET_VIEW | `?type&from&to&includeMessageEntries&page` | Page<WalletTxnDto> | |
| W3 | POST | /admin/partners/{id}/wallet/adjust | ADM:WALLET_ADJUST | `{type: CREDIT|DEBIT, amount, remarks (required, 5–500)}` | WalletTxnDto (audited) | WALLET_NEGATIVE_NOT_ALLOWED |
| W4 | GET | /admin/topup-requests | ADM:TOPUP_VIEW | `?status&partnerId&paymentMode&from&to&search(utr/requestNo)&page` | Page<TopupRequestDto> | |
| W5 | GET | /admin/topup-requests/{id} | ADM:TOPUP_VIEW | — | TopupRequestDto | |
| W6 | POST | /admin/topup-requests/{id}/approve | ADM:TOPUP_APPROVE | `{remarks?}` | TopupRequestDto + `walletTransaction` | TOPUP_ALREADY_PROCESSED |
| W7 | POST | /admin/topup-requests/{id}/reject | ADM:TOPUP_APPROVE | `{reason}` (required) | TopupRequestDto | TOPUP_ALREADY_PROCESSED |
| W8 | GET | /admin/credit-packs | ADM:PRICING_MANAGE | `?active` | `[CreditPackDto]` | |
| W9 | POST | /admin/credit-packs | ADM:PRICING_MANAGE | `{name, description, price, bonusType, bonusValue, isPopular, isActive, sortOrder}` | CreditPackDto | |
| W10 | PUT | /admin/credit-packs/{id} | ADM:PRICING_MANAGE | same | CreditPackDto | |
| W11 | DELETE | /admin/credit-packs/{id} | ADM:PRICING_MANAGE | — | null (soft: inactive if used) | |
| W12 | GET | /partner/wallet | PTR | — | WalletDto | |
| W13 | PUT | /partner/wallet/low-balance-threshold | PTR | `{threshold}` | WalletDto | |
| W14 | GET | /partner/wallet/transactions | PTR | `?type&from&to&includeMessageEntries(default false)&page` | Page<WalletTxnDto> | |
| W15 | GET | /partner/wallet/transactions/export | PTR | same + `format=csv|xlsx` | file | |
| W16 | GET | /partner/credit-packs | PTR | — | active `[CreditPackDto]` | |
| W17 | POST | /partner/topup-requests | PTR | multipart: `amount, paymentMode, utr, paymentDate, note?, screenshot(file ≤2MB jpg/png/pdf)` | TopupRequestDto | TOPUP_DUPLICATE_UTR, FILE_* |
| W18 | GET | /partner/topup-requests | PTR | `?status&page` | Page<TopupRequestDto> | |
| W19 | GET | /partner/topup-requests/{id} | PTR | — | TopupRequestDto | |
| PG1 | GET | /partner/payments/config | PTR | — | `{enabled, gateway, keyId, gstEnabled, gstPercent, minAmount, maxAmount, companyName, logoUrl}` | |
| PG2 | POST | /partner/payments/orders | PTR | `{creditPackId}` **or** `{amount}` | `{orderUid, gateway, gatewayOrderId, keyId, amountPaise, currency, baseAmount, bonusAmount, gstAmount, totalAmount, creditAmount, prefill:{name,email,contact}}` | PAYMENT_GATEWAY_NOT_CONFIGURED, PAYMENT_AMOUNT_OUT_OF_RANGE |
| PG3 | POST | /partner/payments/verify | PTR | `{gatewayOrderId, gatewayPaymentId, signature}` | PaymentOrderDto (status PAID or ATTEMPTED if gateway still processing) | PAYMENT_SIGNATURE_INVALID |
| PG4 | POST | /partner/payments/orders/{uid}/failed | PTR | `{code, description, reason}` (checkout failure info, informational) | PaymentOrderDto | |
| PG5 | GET | /partner/payments | PTR | `?status&from&to&page` | Page<PaymentOrderDto> | |
| PG6 | GET | /partner/payments/{uid} | PTR | — | PaymentOrderDto | |
| PG7 | GET | /partner/invoices | PTR | `?from&to&page` | Page<InvoiceDto> | |
| PG8 | GET | /partner/invoices/{id}/pdf | PTR | — | application/pdf | |
| PG9 | GET | /admin/payments | ADM:PAYMENT_VIEW | `?status&partnerId&gateway&from&to&search&page` | Page<PaymentOrderDto + partner> | |
| PG10 | GET | /admin/payments/{uid} | ADM:PAYMENT_VIEW | — | PaymentOrderDto + `logs[]` | |
| PG11 | POST | /admin/payments/{uid}/reconcile | ADM:PAYMENT_RECONCILE | — | PaymentOrderDto | |
| PG12 | POST | /admin/payments/reconcile-pending | ADM:PAYMENT_RECONCILE | — | `{checked, paid, expired, failed}` | |
| PG13 | GET | /admin/invoices | ADM:PAYMENT_VIEW | `?partnerId&from&to&page` | Page<InvoiceDto> | |
| PG14 | GET | /admin/invoices/{id}/pdf | ADM:PAYMENT_VIEW | — | application/pdf | |
| PG15 | POST | /webhooks/payments/razorpay | PUB (signature) | raw Razorpay event; header `X-Razorpay-Signature`, `X-Razorpay-Event-Id` | `200 {success:true}` (always quick) | WEBHOOK_SIGNATURE_INVALID (400) |

### 4.5 Pricing — rate plans
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| R1 | GET | /admin/rate-plans | ADM:PRICING_MANAGE | `?status&includeCustom&page` | Page<RatePlanDto + partnerCount> | |
| R2 | POST | /admin/rate-plans | ADM:PRICING_MANAGE | `{name, description, isDefault, partnerId?, sessionMessageFee, platformFeePerMessage, platformFeeMonthlyPerNumber, items:[{countryCode, category, price}]}` | RatePlanDto | CONFLICT |
| R3 | GET | /admin/rate-plans/{id} | ADM:PRICING_MANAGE | — | RatePlanDto | |
| R4 | PUT | /admin/rate-plans/{id} | ADM:PRICING_MANAGE | same as R2 (items replaced) | RatePlanDto (audited) | |
| R5 | POST | /admin/rate-plans/{id}/clone | ADM:PRICING_MANAGE | `{name, partnerId?}` | RatePlanDto | |
| R6 | PATCH | /admin/rate-plans/{id}/status | ADM:PRICING_MANAGE | `{status}` | RatePlanDto | CONFLICT (default/in use) |
| R7 | GET | /partner/rate-plan | PTR | — | `{name, items[], sessionMessageFee, platformFee…}` | |

### 4.6 Meta settings, WABAs, senders (admin)
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| M1 | GET | /admin/meta/settings | ADM:WHATSAPP_VIEW | — | `{appId, appSecretSet, configId, solutionId, businessPortfolioId, graphApiVersion, verifyTokenSet, billingMode, extendedCreditLineId, webhookUrl, lastTestAt, lastTestOk}` (secrets never returned) | |
| M2 | PUT | /admin/meta/settings | ADM:SETTINGS_MANAGE | same, plus `appSecret?`, `verifyToken?` (only when changing) | same | |
| M3 | POST | /admin/meta/settings/test | ADM:WHATSAPP_MANAGE | — | `{appOk, tokenOk, webhookSubscribed, details[]}` (calls `GET /{APP_ID}?fields=name` with app token, `GET /{APP_ID}/subscriptions`) | META_API_ERROR |
| M4 | GET | /admin/whatsapp/accounts | ADM:WHATSAPP_VIEW | `?ownerType&status&tokenStatus&partnerId&page` | Page<WabaDto> | |
| M5 | POST | /admin/whatsapp/accounts | ADM:WHATSAPP_MANAGE | `{wabaId, accessToken}` (platform WABA, system-user token) | WabaDto + senders imported | META_API_ERROR |
| M6 | GET | /admin/whatsapp/accounts/{id} | ADM:WHATSAPP_VIEW | — | WabaDto + `senders[]` + `checklist` | |
| M7 | PUT | /admin/whatsapp/accounts/{id}/token | ADM:WHATSAPP_MANAGE | `{accessToken}` | WabaDto | META_API_ERROR |
| M8 | POST | /admin/whatsapp/accounts/{id}/sync | ADM:WHATSAPP_MANAGE | — | WabaDto (WABA + phone numbers refreshed) | META_TOKEN_INVALID |
| M9 | POST | /admin/whatsapp/accounts/{id}/test | ADM:WHATSAPP_MANAGE | — | `{tokenValid, scopes[], expiresAt, subscribed}` (`/debug_token`) | |
| M10 | POST | /admin/whatsapp/accounts/{id}/subscribe | ADM:WHATSAPP_MANAGE | — | WabaDto (`POST /{WABA_ID}/subscribed_apps`) | |
| M11 | POST | /admin/whatsapp/accounts/{id}/share-credit-line | ADM:WHATSAPP_MANAGE | — | WabaDto (SOLUTION_PARTNER only) | CONFLICT |
| M12 | POST | /admin/whatsapp/manual-connect | ADM:WHATSAPP_MANAGE | `{partnerId?, wabaId, phoneNumberId, accessToken, pin?, register:boolean}` | SenderDto + ChecklistDto | META_API_ERROR, CONFLICT |
| M13 | GET | /admin/senders | ADM:WHATSAPP_VIEW | `?senderType&partnerId&quality&status&assigned&search&page` | Page<SenderDto> | |
| M14 | GET | /admin/senders/{id} | ADM:WHATSAPP_VIEW | — | SenderDto + ChecklistDto | |
| M15 | PATCH | /admin/senders/{id} | ADM:WHATSAPP_MANAGE | `{dailySendCap?, maxMps?, isPaused?, pauseReason?}` | SenderDto | |
| M16 | POST | /admin/senders/{id}/assign | ADM:WHATSAPP_MANAGE | `{partnerId, isDefault}` | SenderDto | CONFLICT |
| M17 | POST | /admin/senders/{id}/unassign | ADM:WHATSAPP_MANAGE | — | SenderDto | |
| M18 | GET | /admin/meta/health | ADM:WHATSAPP_VIEW | — | `{wabas:[{waba, tokenStatus, reviewStatus, restrictions}], senders:[{sender, quality, health}], alerts[]}` | |

### 4.7 Partner — senders & Embedded Signup
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| E1 | GET | /partner/senders | PTR | — | `[SenderDto]` | |
| E2 | GET | /partner/senders/{id} | PTR | — | SenderDto + ChecklistDto | NOT_FOUND |
| E3 | PATCH | /partner/senders/{id}/default | PTR | — | SenderDto | |
| E4 | GET | /partner/embedded-signup/config | PTR | — | `{allowed, appId, configId, graphApiVersion, solutionId?, featureTypes:["", "whatsapp_business_app_onboarding"], extras}` | OWN_NUMBER_NOT_ALLOWED, META_NOT_CONFIGURED |
| E5 | POST | /partner/embedded-signup/complete | PTR | `{code, wabaId, phoneNumberId, businessId, mode: NEW|EXISTING|COEXISTENCE, pin? (6 digits, required unless COEXISTENCE)}` | `{sender: SenderDto, checklist: ChecklistDto, steps:[{step, status, message}]}` | OWN_NUMBER_NOT_ALLOWED, META_API_ERROR, CONFLICT (number already connected) |
| E6 | POST | /partner/embedded-signup/events | PTR | `{event: CANCEL|ERROR, currentStep, errorMessage, errorCode, sessionId}` | null (logged for support) | |
| E7 | POST | /partner/senders/{id}/reconnect | PTR | `{code, wabaId, phoneNumberId, businessId}` | same as E5 | |
| E8 | POST | /partner/senders/{id}/disconnect | PTR (own only) | `{confirm:true}` | SenderDto (DISCONNECTED, read-only history) | FORBIDDEN (platform sender) |
| E9 | POST | /admin/senders/{id}/disconnect | ADM:WHATSAPP_MANAGE | `{reason}` | SenderDto | |

### 4.8 Sender tools (same contract under both prefixes)
`{base}` = `/admin/senders/{id}` (ADM:WHATSAPP_MANAGE, GET with WHATSAPP_VIEW) **or** `/partner/senders/{id}` (PTR, only own/assigned; partner cannot change PIN/registration of PLATFORM_SENDER).

| # | Method | URL | Request | Response | Errors |
|---|---|---|---|---|---|
| T1 | POST | {base}/sync | — | SenderDto | META_API_ERROR |
| T2 | POST | {base}/request-code | `{method: SMS|VOICE, language:"en"}` | `{sent:true}` | META_API_ERROR |
| T3 | POST | {base}/verify-code | `{code}` | SenderDto | META_API_ERROR |
| T4 | POST | {base}/register | `{pin}` | SenderDto | META_API_ERROR |
| T5 | POST | {base}/deregister | `{confirm:true}` | SenderDto | |
| T6 | PUT | {base}/two-step-pin | `{pin}` | null | META_API_ERROR |
| T7 | GET | {base}/profile | — | `{about, description, address, email, websites[], vertical, profilePictureUrl}` | |
| T8 | PUT | {base}/profile | same (no picture) | same | VALIDATION_FAILED |
| T9 | POST | {base}/profile/photo | multipart `file` (jpg/png, ≤5 MB, min 192×192) | same | FILE_* |
| T10 | GET | {base}/health | — | `{quality, tier, throughput, health, nameStatus, tokenStatus, alerts[]}` | |
| T11 | GET | {base}/analytics | `?from&to&granularity=DAILY|MONTHLY` | `{meta:[{date, category, country, volume, cost}], ours:[{date, sent, delivered, read, failed, charged}]}` | |
| T12 | GET | {base}/qr-codes | — | `[{id, code, prefilledMessage, deepLinkUrl, shortLink, pngUrl, svgUrl}]` | |
| T13 | POST | {base}/qr-codes | `{prefilledMessage (≤140)}` | QR dto | |
| T14 | PUT | {base}/qr-codes/{qrId} | `{prefilledMessage}` | QR dto | |
| T15 | DELETE | {base}/qr-codes/{qrId} | — | null | |
| T16 | GET | {base}/qr-codes/{qrId}/image | `?format=png|svg` | image | |
| T17 | GET | {base}/conversational-components | — | `{enableWelcomeMessage, prompts[], commands:[{name, description}]}` | |
| T18 | PUT | {base}/conversational-components | same | same | VALIDATION_FAILED |
| T19 | GET | {base}/blocked-users | `?page` | Page<`{waId, status, blockedAt}`> | |
| T20 | POST | {base}/blocked-users | `{waIds:[]}` | `{blocked:[], failed:[{waId, reason}]}` | |
| T21 | DELETE | {base}/blocked-users | `{waIds:[]}` | `{unblocked:[], failed:[]}` | |

### 4.9 Templates
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| TP1 | GET | /partner/templates | PTR | `?wabaId&senderId&status&category&language&search&page` | Page<TemplateDto> | |
| TP2 | POST | /partner/templates | PTR | `{wabaDbId, name, language, category, parameterFormat, components[ComponentDto], allowCategoryChange:true, submit:boolean}` | TemplateDto (own WABA + submit → PENDING; platform WABA → PENDING_REVIEW) | VALIDATION_FAILED, CONFLICT, META_API_ERROR |
| TP3 | GET | /partner/templates/{id} | PTR | — | TemplateDto | |
| TP4 | PUT | /partner/templates/{id} | PTR | same as TP2 (Meta edit rules: APPROVED/REJECTED/PAUSED only, category edit only if not APPROVED) | TemplateDto | TEMPLATE_INVALID_STATE |
| TP5 | DELETE | /partner/templates/{id} | PTR | — | null (deletes all languages of that name at Meta) | |
| TP6 | POST | /partner/templates/{id}/submit | PTR | — | TemplateDto | TEMPLATE_INVALID_STATE |
| TP7 | POST | /partner/templates/sync | PTR | `{wabaDbId}` | `{created, updated, deleted}` | |
| TP8 | GET | /partner/templates/{id}/analytics | PTR | `?from&to` | `{totals:{sent,delivered,read,clicked}, daily[], buttons[]}` | |
| TP9 | GET | /partner/template-library | PTR | `?search&topic&usecase&industry&language&page` | `[{name, category, topic, usecase, body, header, buttons[], language}]` | |
| TP10 | POST | /partner/template-library/use | PTR | `{wabaDbId, libraryTemplateName, name, language, buttonInputs[]}` | TemplateDto | |
| TP11 | POST | /partner/media/template-header | PTR | multipart `file`, `wabaDbId` | `{fileUid, headerHandle}` (Resumable Upload API) | FILE_* |
| TP12 | GET | /admin/templates | ADM:TEMPLATE_VIEW | `?partnerId&wabaId&status&category&search&page` | Page<TemplateDto> | |
| TP13 | GET | /admin/templates/review-queue | ADM:TEMPLATE_REVIEW | `?page` | Page<TemplateDto> (PENDING_REVIEW) | |
| TP14 | POST | /admin/templates/{id}/approve | ADM:TEMPLATE_REVIEW | `{note?}` | TemplateDto (submitted to Meta → PENDING) | TEMPLATE_INVALID_STATE, META_API_ERROR |
| TP15 | POST | /admin/templates/{id}/reject | ADM:TEMPLATE_REVIEW | `{reason}` | TemplateDto (INTERNAL_REJECTED) | |
| TP16 | POST | /admin/templates | ADM:TEMPLATE_REVIEW | TP2 body (platform template, `partnerId?`) | TemplateDto | |
| TP17 | POST | /admin/templates/sync | ADM:TEMPLATE_REVIEW | `{wabaDbId}` | `{created, updated, deleted}` | |
| TP18 | DELETE | /admin/templates/{id} | ADM:TEMPLATE_REVIEW | — | null | |

### 4.10 Messaging & inbox (partner) + logs (admin)
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| MS1 | POST | /partner/messages/estimate | PTR | `{senderId, recipients[] | count+countryCode, type, templateId?}` | CostEstimateDto | RATE_NOT_CONFIGURED |
| MS2 | POST | /partner/messages/send | PTR | SendMessageRequest | MessageDto (status QUEUED) | WALLET_INSUFFICIENT_BALANCE, WINDOW_CLOSED, TEMPLATE_NOT_APPROVED, SENDER_*, RECIPIENT_*, RATE_NOT_CONFIGURED |
| MS3 | GET | /partner/messages | PTR | `?direction&status&senderId&category&type&campaignUid&search(to/wamid/clientRef)&from&to&page` | Page<MessageDto> | |
| MS4 | GET | /partner/messages/{uid} | PTR | — | MessageDto with statusHistory | |
| MS5 | GET | /partner/messages/export | PTR | MS3 filters + `format=csv|xlsx` (max 31 days) | file | |
| MS6 | POST | /partner/media | PTR | multipart `file`, `senderId` (type/size per Meta limits) | `{fileUid, metaMediaId, mimeType, size}` | FILE_* , META_API_ERROR |
| IN1 | GET | /partner/inbox/conversations | PTR | `?senderId&search&unreadOnly&page` | Page<`{senderId, waId, contact?, lastMessagePreview, lastMessageAt, unreadCount, windowOpen, windowExpiresAt}`> | |
| IN2 | GET | /partner/inbox/conversations/{senderId}/{waId}/messages | PTR | `?beforeUid&size=30` | `{items:[MessageDto], hasMore}` | |
| IN3 | POST | /partner/inbox/conversations/{senderId}/{waId}/reply | PTR | SendMessageRequest minus senderId/to | MessageDto | WINDOW_CLOSED, … (same as MS2) |
| IN4 | POST | /partner/inbox/conversations/{senderId}/{waId}/read | PTR | `{typing:boolean}` | null (mark-as-read on latest inbound, optional typing indicator) | |
| IN5 | GET | /partner/inbox/stream | PTR | SSE `text/event-stream` (`?access_token=` short-lived ticket from IN6) | events `message.new`, `message.status` | |
| IN6 | POST | /partner/inbox/stream-ticket | PTR | — | `{ticket, expiresIn:60}` | |
| ML1 | GET | /admin/messages | ADM:MESSAGE_LOG_VIEW | MS3 filters + `partnerId` | Page<MessageDto + partner> | |
| ML2 | GET | /admin/messages/{uid} | ADM:MESSAGE_LOG_VIEW | — | MessageDto + raw status events | |
| ML3 | GET | /admin/messages/export | ADM:MESSAGE_LOG_VIEW | same + format | file | |

### 4.11 Meta webhook
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| WH1 | GET | /webhooks/whatsapp | PUB | `hub.mode=subscribe&hub.verify_token=…&hub.challenge=…` | **plain text** challenge (no envelope), 200 | 403 on token mismatch |
| WH2 | POST | /webhooks/whatsapp | PUB (HMAC) | raw Meta payload, header `X-Hub-Signature-256` | `200` empty body within < 1 s | 401 invalid signature |

### 4.12 Contacts, groups, opt-outs
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| C1 | GET | /partner/contacts | PTR | `?search&tag&groupId&optedOut&blocked&page` | Page<ContactDto> | |
| C2 | POST | /partner/contacts | PTR | `{phone, name?, email?, tags[], attributes{}, groupIds[]}` | ContactDto | CONFLICT, RECIPIENT_INVALID |
| C3 | GET/PUT/DELETE | /partner/contacts/{id} | PTR | PUT same as C2 | ContactDto / null | |
| C4 | POST | /partner/contacts/bulk-delete | PTR | `{ids[]}` | `{deleted}` | |
| C5 | POST | /partner/contacts/import | PTR | multipart `file` (csv/xlsx ≤10 MB, ≤50k rows), `mapping` JSON `{phone, name, email, tags, attributes:{col→key}}`, `groupId?`, `defaultCountry:"IN"`, `updateExisting` | `{total, created, updated, invalid, duplicates, reportFileUid}` | IMPORT_INVALID_FILE |
| C6 | POST | /partner/contacts/import/preview | PTR | multipart `file` | `{columns[], sampleRows[][], rowCount}` | IMPORT_INVALID_FILE |
| C7 | GET | /partner/contacts/import/sample | PTR | — | sample CSV | |
| C8 | GET | /partner/contacts/tags | PTR | — | `[{tag, count}]` | |
| C9 | POST | /partner/contacts/{id}/opt-out | PTR | `{reason?}` | ContactDto | |
| C10 | POST | /partner/contacts/{id}/opt-in | PTR | `{confirmConsent:true}` | ContactDto | |
| C11 | GET | /partner/opt-outs | PTR | `?search&source&page` | Page<`{phone, source, keyword, createdAt}`> | |
| C12 | GET/PUT | /partner/settings/opt-out | PTR | `{optOutKeywords[], optInKeywords[], autoReply}` | same | |
| G1 | GET | /partner/contact-groups | PTR | `?search&page` | Page<`{id, name, description, memberCount}`> | |
| G2 | POST/PUT/DELETE | /partner/contact-groups[/{id}] | PTR | `{name, description}` | group | CONFLICT |
| G3 | POST | /partner/contact-groups/{id}/members | PTR | `{contactIds[]}` | `{added}` | |
| G4 | DELETE | /partner/contact-groups/{id}/members | PTR | `{contactIds[]}` | `{removed}` | |

### 4.13 Campaigns
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| CP1 | POST | /partner/campaigns/upload | PTR | multipart `file` (csv/xlsx ≤10 MB, ≤100k rows) | `{fileUid, columns[], sampleRows[][], rowCount}` | IMPORT_INVALID_FILE |
| CP2 | POST | /partner/campaigns | PTR | `{name, senderId, templateId, audience:{type: FILE|GROUPS|CONTACTS, fileUid?, groupIds?, contactIds?}, mapping:{phone, variables:{"1":"col"|"{{attr}}"|"=static"}, headerMedia?}, defaultCountry:"IN", saveContacts:boolean}` | CampaignDto (DRAFT) | |
| CP3 | PUT | /partner/campaigns/{id} | PTR | same (DRAFT only) | CampaignDto | CAMPAIGN_INVALID_STATE |
| CP4 | POST | /partner/campaigns/{id}/prepare | PTR | — | `{counts:{total, valid, invalid, optedOut, blocked, duplicates}, preview:[{phone, renderedText}], estimate: CostEstimateDto}` | |
| CP5 | POST | /partner/campaigns/{id}/launch | PTR | `{scheduleAt? (ISO, future), confirmCost}` | CampaignDto (SCHEDULED/RUNNING) | WALLET_INSUFFICIENT_BALANCE, TEMPLATE_NOT_APPROVED, SENDER_* |
| CP6 | POST | /partner/campaigns/{id}/pause · /resume · /cancel | PTR | — | CampaignDto | CAMPAIGN_INVALID_STATE, WALLET_INSUFFICIENT_BALANCE (resume) |
| CP7 | GET | /partner/campaigns | PTR | `?status&search&from&to&page` | Page<CampaignDto> | |
| CP8 | GET | /partner/campaigns/{id} | PTR | — | CampaignDto (poll every 5 s while RUNNING) | |
| CP9 | GET | /partner/campaigns/{id}/recipients | PTR | `?status&search&page` | Page<`{rowNo, phone, status, skipReason, errorMessage, messageUid}`> | |
| CP10 | GET | /partner/campaigns/{id}/report | PTR | `?format=xlsx|csv` | file | |
| CP11 | DELETE | /partner/campaigns/{id} | PTR | — | null (DRAFT only) | CAMPAIGN_INVALID_STATE |
| CP12 | GET | /admin/campaigns | ADM:CAMPAIGN_VIEW | `?partnerId&status&from&to&page` | Page<CampaignDto + partner> | |
| CP13 | POST | /admin/campaigns/{id}/pause · /cancel | ADM:CAMPAIGN_MANAGE | `{reason}` | CampaignDto | |

### 4.14 Developer (partner)
| # | Method | URL | Role | Request | Response | Errors |
|---|---|---|---|---|---|---|
| D1 | GET | /partner/api-keys | PTR | — | `[ApiKeyDto]` | |
| D2 | POST | /partner/api-keys | PTR | `{name, ipWhitelist[]}` (max 5 keys) | ApiKeyDto + `apiSecret` (shown once) | CONFLICT |
| D3 | PATCH | /partner/api-keys/{id} | PTR | `{name?, ipWhitelist?, status?}` | ApiKeyDto | |
| D4 | POST | /partner/api-keys/{id}/regenerate | PTR | `{password}` | ApiKeyDto + `apiSecret` (audited) | AUTH_INVALID_CREDENTIALS |
| D5 | DELETE | /partner/api-keys/{id} | PTR | — | null | |
| D6 | GET | /partner/webhook | PTR | — | `{url, events[], isActive, secretSet, lastSuccessAt, consecutiveFailures}` | |
| D7 | PUT | /partner/webhook | PTR | `{url (https), events[], isActive}` | same + `secret` (only first time) | VALIDATION_FAILED |
| D8 | POST | /partner/webhook/rotate-secret | PTR | — | `{secret}` (shown once) | |
| D9 | POST | /partner/webhook/test | PTR | `{eventType}` | `{status, responseCode, durationMs, responseBody}` | |
| D10 | GET | /partner/webhook/logs | PTR | `?status&eventType&from&to&page` | Page<delivery log> | |
| D11 | POST | /partner/webhook/logs/{id}/retry | PTR | — | delivery log | |
| D12 | GET | /partner/developer/info | PTR | — | `{baseUrl, rateLimitPerMinute, swaggerUrl, postmanUrl}` | |

### 4.15 Dashboards, reports, settings, notifications (admin) & partner reports
| # | Method | URL | Role | Request | Response |
|---|---|---|---|---|---|
| DB1 | GET | /admin/dashboard | ADM:DASHBOARD_VIEW | — | `{partners:{total, active}, walletTotal, today:{sent, delivered, read, failed}, revenue:{today, month}, pendingTopups, senderAlerts[]}` |
| DB2 | GET | /admin/dashboard/chart | ADM:DASHBOARD_VIEW | `?from&to&metric=messages|revenue` | `[{date, ...series}]` |
| DB3 | GET | /partner/dashboard | PTR | — | `{wallet: WalletDto, today:{sent, delivered, read, failed, spend}, deliveryRate7d, senders:[{id, display, quality, status}], lowBalance}` |
| DB4 | GET | /partner/dashboard/chart | PTR | `?from&to` | `[{date, sent, delivered, read, failed, spend}]` |
| RP1 | GET | /admin/reports/partners | ADM:REPORT_VIEW | `?from&to&format=json|xlsx|pdf&page` | `[{partner, messages, byCategory{}, charged, refunds, topups}]` |
| RP2 | GET | /admin/reports/categories | ADM:REPORT_VIEW | `?from&to&partnerId&format` | `[{category, count, charged}]` |
| RP3 | GET | /admin/reports/countries | ADM:REPORT_VIEW | same | `[{country, count, charged}]` |
| RP4 | GET | /admin/reports/revenue | ADM:REPORT_VIEW | `?from&to&groupBy=day|month&format` | `[{period, gatewayCollections, manualTopups, gst, messageCharges, platformFees}]` |
| RP5 | GET | /admin/reports/reconciliation | ADM:REPORT_VIEW | `?from&to&wabaId&format` | `[{date, sender, country, category, metaVolume, metaCost, ourCount, ourCharge, profit}]` |
| RP6 | GET | /partner/reports/usage | PTR | `?from&to&groupBy=day|category|country|sender&format` | `[{key, sent, delivered, read, failed, charged}]` |
| ST1 | GET | /admin/settings/{group} | ADM:SETTINGS_MANAGE | group ∈ `company, gst, payment-gateway, smtp, charging, wallet, rate-limit, security` | `{...values}` (secrets as `xxxSet:true`) |
| ST2 | PUT | /admin/settings/{group} | ADM:SETTINGS_MANAGE | values | same (audited) |
| ST3 | POST | /admin/settings/company/logo | ADM:SETTINGS_MANAGE | multipart file (png/jpg/svg ≤1 MB) | `{logoUrl}` |
| ST4 | POST | /admin/settings/smtp/test | ADM:SETTINGS_MANAGE | `{to}` | `{sent:true}` |
| ST5 | POST | /admin/settings/payment-gateway/test | ADM:SETTINGS_MANAGE | — | `{ok, message}` (fetch Razorpay orders count=1) |
| ST6 | GET | /public/branding | PUB | — | `{companyName, logoUrl}` (login page) |

### 4.16 Partner public REST API — `/api/v1/public` (KEY)
Headers: `X-API-KEY`, `X-API-SECRET`, optional `Idempotency-Key` (24 h) on POSTs. Rate limit headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`.

| # | Method | URL | Request | Response `data` | Errors |
|---|---|---|---|---|---|
| PU1 | POST | /v1/public/messages/send | SendMessageRequest (`from` or `senderId`, media via `link` or `metaMediaId`) | `{id (uid), status:"QUEUED", price, to, clientReference}` | API_KEY_*, IP_NOT_WHITELISTED, RATE_LIMITED, WALLET_INSUFFICIENT_BALANCE, WINDOW_CLOSED, TEMPLATE_NOT_APPROVED, RECIPIENT_*, SENDER_* |
| PU2 | POST | /v1/public/messages/bulk | `{from|senderId, template:{name, language}, recipients:[{to, variables, clientReference}]}` (≤1000) | `{accepted:[{to, id}], rejected:[{to, code, message}], totalCost}` | same |
| PU3 | GET | /v1/public/messages/{id} | — | MessageDto (public subset) | NOT_FOUND |
| PU4 | GET | /v1/public/templates | `?status&language&page` | Page<TemplateDto subset> | |
| PU5 | POST | /v1/public/templates | TP2 body (`from|wabaId`) | TemplateDto | |
| PU6 | GET | /v1/public/senders | — | `[{id, phoneNumber, verifiedName, quality, status, isDefault}]` | |
| PU7 | GET | /v1/public/wallet/balance | — | `{balance, held, currency}` | |
| PU8 | GET | /v1/public/contacts | `?search&tag&page` | Page<ContactDto> | |
| PU9 | POST | /v1/public/contacts | C2 body | ContactDto | |

### 4.17 Partner webhook (outbound, we → partner)
`POST <partner url>` headers: `Content-Type: application/json`, `X-CB-Event`, `X-CB-Event-Id`, `X-CB-Timestamp` (unix s), `X-CB-Signature: sha256=HMAC_SHA256(secret, timestamp + "." + body)`. Success = any 2xx within 10 s. Retries: 5 (1 m, 5 m, 15 m, 1 h, 6 h).
```json
{ "event": "message.status", "eventId": "01J…", "createdAt": "2026-10-19T08:30:00Z",
  "data": { "id": "01J…", "wamid": "wamid.HBg…", "to": "9198…", "status": "delivered",
            "timestamp": "…", "error": null, "price": "0.8600", "clientReference": "INV-889" } }
```
Events: `message.status`, `message.inbound` (`{from, senderId, type, text|media{url (signed, 24 h)}, contextId}`), `template.status`, `sender.status`, `wallet.low_balance`, `test.ping`.

### 4.18 System
| GET | /actuator/health | PUB | `{status:"UP"}` (details only on localhost) |
| GET | /swagger-ui.html, /v3/api-docs | PUB in dev; prod: admin-only via Nginx basic auth or disabled by `CBWA_SWAGGER_ENABLED` |

---

## Changelog
- v1.0 (Phase 0) — initial contract.
