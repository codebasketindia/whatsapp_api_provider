# Browser smoke tests (Playwright)

Used for the Phase 12 regression run. Requires the backend on :8080 and `npx vite preview --port 4173` in `frontend/`.

- `crawl.mjs <email> <password> admin|partner <width>` — opens every admin/partner screen, reports any API 4xx/5xx,
  JavaScript error, error state or horizontal scroll. Run at 390 (phone) and 1280 (desktop).
- `metastub.mjs` — a tiny local stand-in for graph.facebook.com (port 9900) for UI testing without real Meta
  credentials. Start the backend with `CBWA_META_GRAPH_BASE_URL=http://localhost:9900`. **Never use in production.**

```bash
npm i playwright@1.56.1   # uses the Chromium already installed for Playwright
node crawl.mjs owner@example.com '<password>' admin 390
```
