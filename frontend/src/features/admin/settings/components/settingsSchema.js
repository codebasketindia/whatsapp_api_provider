import { z } from 'zod';
import { INDIAN_STATES } from '../../shared/indianStates';

/**
 * Field definitions per settings group (keys are the camelCase keys returned by ST1).
 * type: text | email | number | switch | select | secret | textarea | radio
 */
export const SETTINGS_FIELDS = {
  company: [
    { key: 'name', label: 'Company name (shown in the app)', type: 'text', required: true },
    { key: 'legalName', label: 'Legal name (on invoices)', type: 'text' },
    { key: 'address', label: 'Registered address', type: 'textarea' },
    { key: 'gstin', label: 'GSTIN', type: 'text', pattern: /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, patternMessage: 'Enter a valid GSTIN', upper: true },
    { key: 'stateCode', label: 'State (place of supply)', type: 'select', options: INDIAN_STATES.map((s) => ({ value: s.code, label: s.name })), hint: 'Decides CGST+SGST (same state) or IGST.' },
    { key: 'email', label: 'Company email', type: 'email' },
    { key: 'phone', label: 'Company phone', type: 'text' },
    { key: 'supportEmail', label: 'Support email (shown to partners)', type: 'email' },
    { key: 'supportPhone', label: 'Support phone / WhatsApp', type: 'text' },
  ],
  'payment-gateway': [
    { key: 'activeGateway', label: 'Online payments', type: 'radio', options: [{ value: 'NONE', label: 'Off', description: 'Partners can only send manual top-up requests.' }, { value: 'RAZORPAY', label: 'Razorpay', description: 'UPI, cards and net banking through Razorpay Checkout.' }] },
    { key: 'razorpayKeyId', label: 'Razorpay Key ID', type: 'text', hint: 'Starts with rzp_test_ (test mode) or rzp_live_.' },
    { key: 'razorpayKeySecret', label: 'Razorpay Key Secret', type: 'secret' },
    { key: 'razorpayWebhookSecret', label: 'Razorpay webhook secret', type: 'secret' },
    { key: 'minAmount', label: 'Minimum top-up (₹)', type: 'number' },
    { key: 'maxAmount', label: 'Maximum top-up (₹)', type: 'number' },
  ],
  gst: [
    { key: 'enabled', label: 'Charge GST and create invoices', type: 'switch', hint: 'GST is added on top of the top-up amount at checkout.' },
    { key: 'percent', label: 'GST rate (%)', type: 'number', max: 100 },
    { key: 'invoicePrefix', label: 'Invoice number prefix', type: 'text', hint: 'e.g. CB → CB/2026-27/000123' },
    { key: 'sacCode', label: 'SAC code', type: 'text' },
    { key: 'invoiceManualTopups', label: 'Also create invoices for approved manual top-ups', type: 'switch' },
  ],
  smtp: [
    { key: 'host', label: 'SMTP host', type: 'text', placeholder: 'smtp.example.com' },
    { key: 'port', label: 'Port', type: 'number', hint: '587 (STARTTLS) or 465 (SSL).' },
    { key: 'username', label: 'Username', type: 'text' },
    { key: 'password', label: 'Password', type: 'secret' },
    { key: 'fromEmail', label: 'From email', type: 'email' },
    { key: 'fromName', label: 'From name', type: 'text' },
    { key: 'starttls', label: 'Use STARTTLS', type: 'switch' },
  ],
  charging: [
    {
      key: 'chargeOn',
      label: 'When is a message charged?',
      type: 'radio',
      options: [
        { value: 'DELIVERED', label: 'When delivered (recommended)', description: 'The price is held when sending and charged only after WhatsApp confirms delivery. Failed messages are refunded automatically.' },
        { value: 'SENT', label: 'When sent', description: 'Charged as soon as WhatsApp accepts the message for sending. No refund if delivery fails later.' },
      ],
    },
    { key: 'chargeFreeUtilityInWindow', label: 'Charge utility templates that Meta gives for free', type: 'switch', hint: 'Meta does not charge utility templates sent inside an open 24-hour window. Off = partners are not charged either.' },
    { key: 'holdTimeoutHours', label: 'Release holds without a final status after (hours)', type: 'number' },
    { key: 'platformFeeGraceDays', label: 'Days to retry the monthly platform fee before pausing a number', type: 'number' },
  ],
  wallet: [{ key: 'defaultLowBalanceThreshold', label: 'Default low-balance alert (₹)', type: 'number', hint: 'Used for partners who did not set their own.' }],
  'rate-limit': [
    { key: 'publicApiPerMinute', label: 'Public API requests per minute per key', type: 'number', min: 1, hint: 'Can be changed per key on the partner page.' },
    { key: 'loginPerMinute', label: 'Login attempts per minute per IP', type: 'number', min: 1 },
  ],
  security: [
    { key: 'admin2faRequired', label: 'Require email code (2-step login) for all staff', type: 'switch' },
    { key: 'maxLoginAttempts', label: 'Wrong password attempts before lock', type: 'number', min: 1 },
    { key: 'lockoutMinutes', label: 'Lock duration (minutes)', type: 'number', min: 1 },
  ],
  ai: [
    { key: 'enabled', label: 'Let partners use the AI Assistant', type: 'switch', hint: 'Off = no AI replies are sent for any partner.' },
    { key: 'anthropicApiKey', label: 'Anthropic API key', type: 'secret' },
    { key: 'model', label: 'AI model', type: 'text', placeholder: 'claude-haiku-5-5', hint: 'claude-haiku-5-5 is the cheapest and fast enough for customer replies.' },
    { key: 'feePerReply', label: 'Fee per AI reply (₹)', type: 'number', hint: 'Debited from the partner wallet for every AI reply sent.' },
    { key: 'maxOutputTokens', label: 'Longest reply (output tokens)', type: 'number', min: 50, hint: 'Lower = shorter and cheaper replies. 400 ≈ 300 words.' },
    { key: 'historyMessages', label: 'Previous messages the AI reads', type: 'number', min: 0, max: 30, hint: 'Fewer = cheaper. 6 is enough for most chats.' },
    { key: 'maxRepliesPerChatPerDay', label: 'Max AI replies per chat per day', type: 'number', min: 1, hint: 'Partners can set a lower limit.' },
  ],
  retention: [
    { key: 'messageContentDays', label: 'Keep message content for (days)', type: 'number', min: 1, hint: 'Older message text is removed; delivery records and charges stay.' },
    { key: 'mediaDays', label: 'Keep received media for (days)', type: 'number', min: 1 },
    { key: 'webhookLogDays', label: 'Keep webhook logs for (days)', type: 'number', min: 1 },
  ],
};

/** Zod schema for a group. Secrets are optional (empty = keep current). */
export function buildSchema(fields) {
  const shape = {};
  fields.forEach((f) => {
    let s;
    if (f.type === 'switch') s = z.boolean();
    else if (f.type === 'number') {
      s = z
        .string()
        .trim()
        .refine((v) => v === '' || (/^\d+(\.\d+)?$/.test(v) && Number(v) >= (f.min ?? 0) && (f.max === undefined || Number(v) <= f.max)), f.max !== undefined ? `Between ${f.min ?? 0} and ${f.max}` : `Enter a number${f.min ? ` (at least ${f.min})` : ''}`);
    } else {
      s = z.string().trim();
      if (f.type === 'email') s = s.refine((v) => v === '' || /^\S+@\S+\.\S+$/.test(v), 'Enter a valid email');
      if (f.pattern) s = s.refine((v) => v === '' || f.pattern.test(v.toUpperCase()), f.patternMessage);
      if (f.required) s = s.refine((v) => v !== '', 'Required');
    }
    shape[f.key] = s;
  });
  return z.object(shape);
}

/** API values → form values. */
export function toFormValues(fields, data = {}) {
  const out = {};
  fields.forEach((f) => {
    const v = data[f.key];
    if (f.type === 'switch') out[f.key] = Boolean(v);
    else if (f.type === 'secret') out[f.key] = '';
    else out[f.key] = v === null || v === undefined ? '' : String(v);
  });
  return out;
}

/** Form values → PUT body: only changed non-secret fields, secrets only when typed. */
export function toBody(fields, values, original = {}) {
  const body = {};
  fields.forEach((f) => {
    const v = values[f.key];
    if (f.type === 'secret') {
      if (v) body[f.key] = v;
      return;
    }
    let out = v;
    if (f.type === 'number') out = v === '' ? null : Number(v);
    if (f.upper && typeof out === 'string') out = out.toUpperCase();
    if (typeof out === 'string' && out === '') out = null;
    const orig = original[f.key] ?? null;
    if (String(out ?? '') !== String(orig ?? '')) body[f.key] = out;
  });
  return body;
}
