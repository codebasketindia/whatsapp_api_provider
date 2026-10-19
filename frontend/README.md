# CB WhatsApp Gateway — Frontend

React 18 + Vite 5 + Tailwind 3 single-page app for both panels: `/admin/*` (staff) and `/partner/*` (API partners).

## Run

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 — /api is proxied to http://localhost:8080
npm run build      # production build in dist/ (served by Nginx)
npm test           # Vitest unit/component tests
```

Environment (`.env.example`, `.env.production`):

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | Backend base path (Nginx proxies `/api` to Spring Boot) |
| `VITE_APP_NAME` | `CB WhatsApp Gateway` | Shown in titles |

No secrets live in the frontend. Meta App ID / Embedded Signup config and Razorpay key id are fetched from the API at runtime.

## Structure

```
src/
  api/axios.js            axios instance, JWT header, silent refresh (single flight), unwrap(), getErrorMessage(),
                          getFieldErrors(), downloadFile(), fetchObjectUrl(), toFormData(), cleanParams()
  api/queryClient.js      TanStack Query defaults (no retry on 4xx)
  api/endpoints/*.js      one module per API area; every row of docs/phase-0/03-api-contract.md
  auth/                   AuthProvider, useAuth, ProtectedRoute, RoleRoute, PermissionGate, tokenStore, permissions
  components/ui/          reusable library — import from 'components/ui'
  components/layout/      AdminLayout, PartnerLayout, nav config, banners, notification bell
  components/whatsapp/    QualityBadge, StatusTicks
  features/admin/<area>/pages/*.jsx      admin screens
  features/partner/<area>/pages/*.jsx    partner screens
  features/common/        Profile, Notifications (both panels)
  hooks/                  useTheme, useMediaQuery/useIsDesktop, useDebounce, useDisclosure, useUrlState, usePageTitle
  lib/                    format (₹ / IST), statusColors, metaGlossary, constants, fbSdk, razorpay, storage
  router.jsx              every route (lazy-loaded pages)
```

## Conventions for engineers

**Adding a page**
1. Create `src/features/<admin|partner>/<area>/pages/MyPage.jsx` (default export).
2. Register it in `src/router.jsx` with `lazy(() => import(...))`. Admin pages are wrapped with `perm('PERMISSION_CODE', <MyPage />)`.
3. Add a menu entry in `components/layout/navConfig.js` if it needs one (admin items take a `permission`).
4. Start the page with `<PageHeader title subtitle actions backTo />` (it also sets the browser tab title).

**Data fetching** — always TanStack Query with the endpoint functions (they already unwrap the envelope):
```jsx
const q = useQuery({ queryKey: ['admin', 'partners', filters], queryFn: () => partnersApi.listPartners(filters), placeholderData: (p) => p });
const m = useMutation({ mutationFn: (body) => partnersApi.createPartner(body), onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'partners'] }), onError: (e) => toast.error(getErrorMessage(e)) });
```
Query keys: `[panel, area, ...params]`, e.g. `['partner','wallet']`, `['admin','topups',filters]`, `['me','notifications', ...]`. Invalidate by prefix.

**Lists** — keep filters in the URL with `useUrlState({ page: 0, status: '' })`, render with:
```jsx
<DataTable columns={cols} page={q.data} loading={q.isLoading} error={q.error} onRetry={q.refetch}
  onPageChange={(p) => update({ page: p })} onRowClick={(r) => navigate(...)} empty={<EmptyState .../>}
  mobileActions={(row) => <Button size="sm">…</Button>} />
```
Columns: `{ key, header, render?, primary?, align?, hideOnMobile?, sortable? }`. Tables become cards below 768px automatically.

**Forms** — React Hook Form + Zod (`zodResolver`). Map server field errors with `getFieldErrors(err)` → `setError`. Money actions and destructive actions must go through `ConfirmDialog`.

**Money / dates** — API money is a string; show with `<Money value>` or `formatMoney()` (₹1,23,456.00, `precise` for 4 decimals). Dates are UTC from the API; show with `<DateTime value>` / `formatDateTime()` (IST). Date filters are sent as `YYYY-MM-DD` (IST) — use `DateRangePicker`.

**Status colours** — always `<StatusBadge status="..."/>` (green success, amber pending, red failed, gray neutral, blue running).

**Meta terms** — add `<InfoTip term="QUALITY_RATING" />` next to labels; texts live in `lib/metaGlossary.js`.

**Sender tools** — `senderTools('admin' | 'partner')` from `api/endpoints/senderTools.js` returns the same functions for both panels (profile, QR codes, PIN, block list…).

**Embedded Signup** — `launchEmbeddedSignup({ appId, graphApiVersion, configId, featureType })` from `lib/fbSdk.js` resolves `{ code, session }`; POST it to `/partner/embedded-signup/complete` immediately (the code expires in ~30 s).

**Razorpay** — `openCheckout(options)` from `lib/razorpay.js`; then always call `/partner/payments/verify` — the browser callback is never proof of payment.

**Multipart** — `toFormData(values)` sends files as parts and objects as JSON text fields.

**Session** — access token in memory, refresh token in localStorage; 401 triggers one refresh then retries. 403 `AUTH_PASSWORD_CHANGE_REQUIRED` redirects to `/change-password`. Password change returns new tokens which the form stores. Login-as-partner keeps the admin refresh token in sessionStorage (this tab) and the purple banner's Exit restores it.

Accessibility & mobile: every interactive element is at least 44px tall, layouts are mobile-first from 360px, and dark mode uses the CSS variables in `styles/index.css`.
