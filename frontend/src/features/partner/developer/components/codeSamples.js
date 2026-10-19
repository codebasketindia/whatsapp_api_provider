// Copy-paste examples for the public API (contract §4.16) and webhook signature checks (§4.17).

const SEND_TEXT = { to: '919876543210', type: 'text', text: { body: 'Hello from our store!' }, clientReference: 'ORDER-1001' };
const SEND_TEMPLATE = {
  to: '919876543210',
  type: 'template',
  template: { name: 'order_update', language: 'en', variables: { body: ['Ravi', 'ORDER-1001'] } },
  clientReference: 'ORDER-1001',
};

const json = (o) => JSON.stringify(o, null, 2);

export const EXAMPLES = [
  { key: 'text', label: 'Send text', method: 'POST', path: '/messages/send', body: SEND_TEXT },
  { key: 'template', label: 'Send template', method: 'POST', path: '/messages/send', body: SEND_TEMPLATE },
  { key: 'status', label: 'Message status', method: 'GET', path: '/messages/{id}' },
  { key: 'balance', label: 'Wallet balance', method: 'GET', path: '/wallet/balance' },
];

export const LANGUAGES = ['curl', 'php', 'node', 'java', 'python'];
export const LANGUAGE_LABEL = { curl: 'cURL', php: 'PHP', node: 'Node.js', java: 'Java', python: 'Python' };

/** Returns the code snippet for an example in a language. */
export function codeSample(lang, example, baseUrl) {
  const url = `${baseUrl}${example.path.replace('{id}', 'MESSAGE_ID')}`;
  const body = example.body ? json(example.body) : null;
  switch (lang) {
    case 'curl':
      return [
        `curl -X ${example.method} "${url}" \\`,
        '  -H "X-API-KEY: YOUR_API_KEY" \\',
        `  -H "X-API-SECRET: YOUR_API_SECRET"${body ? ' \\' : ''}`,
        ...(body ? ['  -H "Content-Type: application/json" \\', `  -d '${JSON.stringify(example.body)}'`] : []),
      ].join('\n');
    case 'php':
      return `<?php
$ch = curl_init("${url}");
curl_setopt_array($ch, [
  CURLOPT_RETURNTRANSFER => true,
  CURLOPT_CUSTOMREQUEST => "${example.method}",
  CURLOPT_HTTPHEADER => [
    "X-API-KEY: YOUR_API_KEY",
    "X-API-SECRET: YOUR_API_SECRET",
    "Content-Type: application/json",
  ],${body ? `\n  CURLOPT_POSTFIELDS => json_encode(${phpArray(example.body, 2)}),` : ''}
]);
$response = json_decode(curl_exec($ch), true);
curl_close($ch);
print_r($response);`;
    case 'node':
      return `const res = await fetch("${url}", {
  method: "${example.method}",
  headers: {
    "X-API-KEY": process.env.CB_API_KEY,
    "X-API-SECRET": process.env.CB_API_SECRET,
    "Content-Type": "application/json",
  },${body ? `\n  body: JSON.stringify(${body.replace(/\n/g, '\n  ')}),` : ''}
});
const data = await res.json();
console.log(data);`;
    case 'java':
      return `import java.net.URI;
import java.net.http.*;

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder(URI.create("${url}"))
    .header("X-API-KEY", System.getenv("CB_API_KEY"))
    .header("X-API-SECRET", System.getenv("CB_API_SECRET"))
    .header("Content-Type", "application/json")
    .method("${example.method}", ${body ? `HttpRequest.BodyPublishers.ofString("""
        ${body.replace(/\n/g, '\n        ')}
        """)` : 'HttpRequest.BodyPublishers.noBody()'})
    .build();
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.body());`;
    case 'python':
      return `import os, requests

resp = requests.request(
    "${example.method}",
    "${url}",
    headers={
        "X-API-KEY": os.environ["CB_API_KEY"],
        "X-API-SECRET": os.environ["CB_API_SECRET"],
    },${body ? `\n    json=${pyLiteral(example.body)},` : ''}
    timeout=30,
)
print(resp.json())`;
    default:
      return '';
  }
}

function phpArray(v, indent = 0) {
  const pad = ' '.repeat(indent);
  if (Array.isArray(v)) return `[${v.map((x) => phpArray(x, indent)).join(', ')}]`;
  if (v && typeof v === 'object') {
    const inner = Object.entries(v).map(([k, x]) => `${pad}  "${k}" => ${phpArray(x, indent + 2)}`).join(',\n');
    return `[\n${inner}\n${pad}]`;
  }
  return typeof v === 'string' ? `"${v}"` : String(v);
}

function pyLiteral(v) {
  return JSON.stringify(v).replace(/\btrue\b/g, 'True').replace(/\bfalse\b/g, 'False').replace(/\bnull\b/g, 'None');
}

/** Webhook signature: X-CB-Signature = "sha256=" + HMAC_SHA256(secret, timestamp + "." + rawBody). */
export const SIGNATURE_SAMPLES = {
  node: `const crypto = require("crypto");
// rawBody must be the exact request body string
function isValid(rawBody, headers, secret) {
  const ts = headers["x-cb-timestamp"];
  const expected = "sha256=" + crypto.createHmac("sha256", secret).update(ts + "." + rawBody).digest("hex");
  const given = headers["x-cb-signature"] || "";
  return given.length === expected.length && crypto.timingSafeEqual(Buffer.from(given), Buffer.from(expected))
    && Math.abs(Date.now() / 1000 - Number(ts)) < 300;
}`,
  php: `<?php
$raw = file_get_contents("php://input");
$ts = $_SERVER["HTTP_X_CB_TIMESTAMP"] ?? "";
$given = $_SERVER["HTTP_X_CB_SIGNATURE"] ?? "";
$expected = "sha256=" . hash_hmac("sha256", $ts . "." . $raw, getenv("CB_WEBHOOK_SECRET"));
if (!hash_equals($expected, $given) || abs(time() - (int)$ts) > 300) { http_response_code(401); exit; }
http_response_code(200);`,
  python: `import hmac, hashlib, time

def is_valid(raw_body: bytes, headers, secret: str) -> bool:
    ts = headers.get("X-CB-Timestamp", "")
    expected = "sha256=" + hmac.new(secret.encode(), (ts + ".").encode() + raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, headers.get("X-CB-Signature", "")) and abs(time.time() - int(ts)) < 300`,
  java: `import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;

boolean isValid(String rawBody, String timestamp, String signature, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
    String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + rawBody).getBytes("UTF-8")));
    return MessageDigest.isEqual(expected.getBytes(), signature.getBytes())
        && Math.abs(System.currentTimeMillis() / 1000 - Long.parseLong(timestamp)) < 300;
}`,
  curl: `# Signatures are checked on your server. Example header values we send:
# X-CB-Event: message.status
# X-CB-Event-Id: 01J...
# X-CB-Timestamp: 1792440000
# X-CB-Signature: sha256=<hex HMAC_SHA256(secret, timestamp + "." + body)>`,
};

export const SAMPLE_RESPONSE = {
  send: { success: true, code: 'OK', message: 'Message queued', data: { id: '01JABCDEF...', status: 'QUEUED', price: '0.8600', to: '919876543210', clientReference: 'ORDER-1001' } },
  status: { event: 'message.status', eventId: '01J...', createdAt: '2026-10-19T08:30:00Z', data: { id: '01JABCDEF...', wamid: 'wamid.HBg...', to: '919876543210', status: 'delivered', timestamp: '1792440000', error: null, price: '0.8600', clientReference: 'ORDER-1001' } },
};

export const PUBLIC_ENDPOINTS = [
  ['POST', '/messages/send', 'Send one message (text, media, interactive or template)'],
  ['POST', '/messages/bulk', 'Send one template to up to 1,000 people'],
  ['GET', '/messages/{id}', 'Status and price of a message'],
  ['GET', '/templates', 'Your templates'],
  ['POST', '/templates', 'Create a template'],
  ['GET', '/senders', 'Your WhatsApp numbers'],
  ['GET', '/wallet/balance', 'Wallet balance'],
  ['GET', '/contacts', 'Your contacts'],
  ['POST', '/contacts', 'Add a contact'],
];

export const ERROR_CODES = [
  ['API_KEY_INVALID', 401, 'Wrong key or secret'],
  ['API_KEY_DISABLED', 403, 'Key is turned off'],
  ['IP_NOT_WHITELISTED', 403, 'Request came from an IP that is not allowed'],
  ['RATE_LIMITED', 429, 'Too many requests — wait for Retry-After seconds'],
  ['WALLET_INSUFFICIENT_BALANCE', 402, 'Add money to your wallet'],
  ['WINDOW_CLOSED', 422, 'Customer has not written in 24 hours — use a template'],
  ['TEMPLATE_NOT_APPROVED', 422, 'Template is not approved or is paused'],
  ['RECIPIENT_OPTED_OUT', 422, 'Contact opted out of marketing'],
  ['RECIPIENT_INVALID', 400, 'Phone number is not valid'],
  ['SENDER_NOT_AVAILABLE', 422, 'Number not connected or not yours'],
  ['VALIDATION_FAILED', 400, 'Check the errors list in the response'],
];
