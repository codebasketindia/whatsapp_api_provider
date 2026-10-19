# Partner API Guide — CB WhatsApp Gateway

Base URL: `https://<your-domain>/api/v1/public` · JSON in, JSON out · All responses:
```json
{ "success": true, "code": "OK", "message": "Message queued", "data": { }, "errors": null }
```

## 1. Authentication
Create a key in **Developer → API keys**. The secret is shown **once**.
```
X-API-KEY: cbk_live_xxxxxxxxxxxxxxxxxxxxxxxx
X-API-SECRET: cbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```
Optional: IP whitelist per key. Rate limit per key (default 600/min) — headers `X-RateLimit-Limit`,
`X-RateLimit-Remaining`, and `Retry-After` on HTTP 429.
POST requests accept `Idempotency-Key` (any unique string, kept 24 h): repeating a request with the same key returns
the first result and never sends or charges twice.

## 2. Endpoints
| Method | Path | Purpose |
|---|---|---|
| POST | `/messages/send` | Send one message (text, image, video, audio, document, sticker, location, contacts, reaction, interactive, template) |
| POST | `/messages/bulk` | One template to up to 1,000 recipients |
| GET | `/messages/{id}` | Message status and details |
| GET | `/templates` | Your templates (`status`, `language`, `search`, `page`, `size`) |
| POST | `/templates` | Create a template (`?from=<sender number>` optional) |
| GET | `/senders` | Your WhatsApp sender numbers |
| GET | `/wallet/balance` | Balance and money on hold |
| GET | `/contacts` | Contacts (`search`, `tag`, `page`, `size`) |
| POST | `/contacts` | Create a contact |

**Sender selection**: `senderId` (from `/senders`) or `from` (sender phone number digits). If neither is given your
default sender is used.

### Send a template
```json
POST /messages/send
{
  "from": "919876543210",
  "to": "919812345678",
  "type": "template",
  "template": {
    "name": "order_update",
    "language": "en",
    "variables": {
      "body": ["Ravi", "#123"],
      "header": { "type": "image", "link": "https://example.com/banner.jpg" },
      "buttons": [ { "index": 0, "subType": "url", "value": "track/123" } ]
    }
  },
  "clientReference": "INV-889"
}
```
Named-variable templates use `"namedVariables": {"customer_name": "Ravi"}` instead of `variables.body`.

Response:
```json
{ "success": true, "code": "OK", "message": "Message queued",
  "data": { "id": "01JABCD...", "status": "QUEUED", "price": "0.8600", "to": "919812345678", "clientReference": "INV-889" } }
```

### Send text (only inside the 24-hour window)
```json
{ "from": "919876543210", "to": "919812345678", "type": "text",
  "text": { "body": "Thanks! Your order is on the way.", "previewUrl": false } }
```
Media: `"type":"image","media":{"link":"https://...","caption":"Bill"}` (also `metaMediaId`).
Interactive: `"type":"interactive","interactive":{ ...Meta interactive object... }`.
Reply to a message: add `"replyToWamid": "wamid.HBg..."`.

### Bulk
```json
POST /messages/bulk
{ "from": "919876543210",
  "template": { "name": "order_update", "language": "en" },
  "recipients": [
    { "to": "919812345678", "bodyVariables": ["Ravi", "#123"], "clientReference": "A1" },
    { "to": "919812345679", "variables": { "customer_name": "Asha" } } ] }
```
→ `{ "accepted": [{to, id, price, clientReference}], "rejected": [{to, code, message, clientReference}], "totalCost": "1.7200" }`

## 3. Message statuses and charging
`QUEUED → ACCEPTED → SENT → DELIVERED → READ` or `FAILED` (with `errorCode` and a plain `errorMessage`).
The price is held from your wallet when queued, charged when delivered (or sent — set by the platform) and
refunded if the message fails. Free-form messages need the customer to have messaged you in the last 24 hours.

## 4. Error codes
| HTTP | code | meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | see `errors[]` for fields |
| 401 | `API_KEY_INVALID` | wrong key/secret |
| 403 | `API_KEY_DISABLED`, `IP_NOT_WHITELISTED`, `PARTNER_SUSPENDED` | |
| 402 | `WALLET_INSUFFICIENT_BALANCE` | `data.required`, `data.available` |
| 422 | `WINDOW_CLOSED` | 24-hour window closed — use a template |
| 422 | `TEMPLATE_NOT_APPROVED`, `RECIPIENT_OPTED_OUT`, `RECIPIENT_BLOCKED`, `SENDER_NOT_AVAILABLE`, `SENDER_PAUSED`, `RATE_NOT_CONFIGURED` | |
| 400 | `RECIPIENT_INVALID`, `TEMPLATE_VARIABLES_MISMATCH` | |
| 429 | `RATE_LIMITED`, `SENDER_DAILY_CAP_REACHED` | retry after `Retry-After` seconds |
| 502 | `META_API_ERROR` | WhatsApp error; `data.friendly` explains |

## 5. Webhooks (we call you)
Set the URL in **Developer → Webhook** (HTTPS). Events: `message.status`, `message.inbound`, `template.status`,
`sender.status`, `wallet.low_balance`, `test.ping`.
```
POST <your url>
X-CB-Event: message.status
X-CB-Event-Id: 01JABC...
X-CB-Timestamp: 1792440955
X-CB-Signature: sha256=<hex HMAC_SHA256(secret, timestamp + "." + rawBody)>
```
```json
{ "event": "message.status", "eventId": "01JABC...", "createdAt": "2026-10-19T08:30:00Z",
  "data": { "id": "01J...", "wamid": "wamid.HBg...", "to": "919812345678", "status": "delivered",
            "timestamp": "...", "error": null, "price": "0.8600", "chargeStatus": "CHARGED", "clientReference": "INV-889" } }
```
Reply with any 2xx within 10 s. Failed deliveries are retried after 1 min, 5 min, 15 min, 1 h and 6 h.
Always verify the signature and reject timestamps older than 5 minutes.

## 6. Code samples
### cURL
```bash
curl -X POST "$BASE/api/v1/public/messages/send" \
  -H "X-API-KEY: $KEY" -H "X-API-SECRET: $SECRET" -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-889" \
  -d '{"from":"919876543210","to":"919812345678","type":"template",
       "template":{"name":"order_update","language":"en","variables":{"body":["Ravi","#123"]}}}'
```
### PHP
```php
$ch = curl_init("$base/api/v1/public/messages/send");
curl_setopt_array($ch, [CURLOPT_POST => true, CURLOPT_RETURNTRANSFER => true,
  CURLOPT_HTTPHEADER => ["X-API-KEY: $key", "X-API-SECRET: $secret", "Content-Type: application/json"],
  CURLOPT_POSTFIELDS => json_encode(["from" => "919876543210", "to" => "919812345678", "type" => "template",
    "template" => ["name" => "order_update", "language" => "en", "variables" => ["body" => ["Ravi", "#123"]]]])]);
$res = json_decode(curl_exec($ch), true);
echo $res["data"]["id"];
// Webhook verification
$sig = 'sha256=' . hash_hmac('sha256', $_SERVER['HTTP_X_CB_TIMESTAMP'] . '.' . file_get_contents('php://input'), $webhookSecret);
if (!hash_equals($sig, $_SERVER['HTTP_X_CB_SIGNATURE'])) { http_response_code(401); exit; }
```
### Node.js (18+)
```js
const res = await fetch(`${BASE}/api/v1/public/messages/send`, {
  method: 'POST',
  headers: { 'X-API-KEY': KEY, 'X-API-SECRET': SECRET, 'Content-Type': 'application/json' },
  body: JSON.stringify({ from: '919876543210', to: '919812345678', type: 'template',
    template: { name: 'order_update', language: 'en', variables: { body: ['Ravi', '#123'] } } }),
});
console.log((await res.json()).data.id);
// Webhook verification (express.raw body)
const crypto = require('crypto');
const expected = 'sha256=' + crypto.createHmac('sha256', SECRET_WH).update(req.get('X-CB-Timestamp') + '.' + req.body).digest('hex');
const ok = crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(req.get('X-CB-Signature')));
```
### Java (11+)
```java
var body = """
  {"from":"919876543210","to":"919812345678","type":"template",
   "template":{"name":"order_update","language":"en","variables":{"body":["Ravi","#123"]}}}""";
var req = HttpRequest.newBuilder(URI.create(base + "/api/v1/public/messages/send"))
    .header("X-API-KEY", key).header("X-API-SECRET", secret).header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
var res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
// Webhook verification
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(whSecret.getBytes(UTF_8), "HmacSHA256"));
String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + rawBody).getBytes(UTF_8)));
boolean ok = MessageDigest.isEqual(expected.getBytes(UTF_8), signature.getBytes(UTF_8));
```
### Python
```python
import requests, hmac, hashlib
r = requests.post(f"{BASE}/api/v1/public/messages/send",
    headers={"X-API-KEY": KEY, "X-API-SECRET": SECRET},
    json={"from": "919876543210", "to": "919812345678", "type": "template",
          "template": {"name": "order_update", "language": "en", "variables": {"body": ["Ravi", "#123"]}}})
print(r.json()["data"]["id"])
# Webhook verification (Flask)
expected = "sha256=" + hmac.new(WH_SECRET.encode(), (request.headers["X-CB-Timestamp"] + "." + request.get_data(as_text=True)).encode(), hashlib.sha256).hexdigest()
ok = hmac.compare_digest(expected, request.headers["X-CB-Signature"])
```

Interactive docs: Swagger UI (`/swagger-ui.html`, group *partner-public-api*) when enabled, and the Postman
collection from **Developer → API docs**.
