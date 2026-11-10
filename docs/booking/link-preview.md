# Booking link preview (WhatsApp / Facebook / Telegram)

Link-preview bots do not run JavaScript, so they only see `index.html` ("CB WhatsApp Gateway").
The backend serves a per-link preview page at `GET /api/public/share/{slug}` with Open Graph tags
from the link's **Link preview** settings (title, description, image; editor → Booking page).

The web server must send bots to that page for `/r/{slug}`. People keep getting the app.

## nginx

Already in `deploy/nginx/cbwa.conf`. In the `http {}` block:

```nginx
map $http_user_agent $cbwa_preview_bot {
    default 0;
    ~*(whatsapp|facebookexternalhit|facebot|meta-externalagent|twitterbot|telegrambot|slackbot|linkedinbot|discordbot|skypeuripreview|googlebot|bingbot|pinterest|embedly|vkshare) 1;
}
```

In the site's `server {}` block, before `location /`:

```nginx
location ~ ^/r/([a-z0-9][a-z0-9-]+)/?$ {
    if ($cbwa_preview_bot) {
        rewrite ^/r/([a-z0-9-]+)/?$ /api/public/share/$1 last;   # backend under /v.1/ → /v.1/api/public/share/$1
    }
    try_files $uri /index.html;
}
```

The `/api/` (or `/v.1/`) proxy block must pass `Host` and `X-Forwarded-Proto`, so the preview's links and
image use the domain the link was shared from.

## Apache (.htaccess next to index.html)

```apache
RewriteEngine On
RewriteCond %{HTTP_USER_AGENT} (whatsapp|facebookexternalhit|facebot|meta-externalagent|twitterbot|telegrambot|slackbot|linkedinbot|discordbot|googlebot|bingbot) [NC]
RewriteRule ^r/([a-z0-9-]+)/?$ /v.1/api/public/share/$1 [L,PT]
```

## Check

```bash
curl -s -A "WhatsApp/2.23" https://YOUR-DOMAIN/r/madinah-ziyarat-2027 | grep og:title
```

WhatsApp caches previews: after the change, share the link with `?v=2` at the end once to see the new preview.
