// Pure helpers for registration links, follow-ups and pass layouts.

export const UNITS = [
  { value: 'minutes', label: 'minutes', factor: 1 },
  { value: 'hours', label: 'hours', factor: 60 },
  { value: 'days', label: 'days', factor: 1440 },
];

/** 4320 → { amount: 3, unit: 'days' } (largest unit that divides evenly). */
export function minutesToDelay(minutes) {
  const m = Number(minutes) || 0;
  if (m > 0 && m % 1440 === 0) return { amount: m / 1440, unit: 'days' };
  if (m > 0 && m % 60 === 0) return { amount: m / 60, unit: 'hours' };
  return { amount: m, unit: 'minutes' };
}

export function delayToMinutes(amount, unit) {
  const f = UNITS.find((u) => u.value === unit)?.factor || 1;
  return Math.round(Number(amount) * f);
}

/** "3 days after registering" / "on 30 Nov 2026, 10:00". */
export function followUpTiming(u, formatDateTime) {
  if (u.sendAt) return `On ${formatDateTime(u.sendAt)}`;
  const d = minutesToDelay(u.delayMinutes);
  const unit = d.amount === 1 ? d.unit.replace(/s$/, '') : d.unit;
  return `${d.amount} ${unit} after registering`;
}

/** Short text describing a SendMessageRequest body. */
export function messageSummary(m) {
  if (!m) return '';
  const type = String(m.type || '').toLowerCase();
  if (type === 'template') return `Template: ${m.template?.name || m.template?.templateId || ''}`;
  if (type === 'text') return m.text?.body || '';
  if (m.media?.caption) return `${type}: ${m.media.caption}`;
  return type ? type.charAt(0).toUpperCase() + type.slice(1) : '';
}

/** Default boxes of the built-in "Harmain Ziyarat Pass" design (1599 × 2000). */
export const HARMAIN_LAYOUT = {
  name: { x: 536 / 1599, y: 1194 / 2000, w: 737 / 1599, h: 61 / 2000 },
  mobile: { x: 536 / 1599, y: 1278 / 2000, w: 737 / 1599, h: 60 / 2000 },
  photo: { x: 694 / 1599, y: 951 / 2000, w: 208 / 1599, h: 168 / 2000 },
  passNo: null,
  textColor: '#0B5A2E',
};

/** Box (fractions) ↔ percentages with one decimal, for the layout inputs. */
export const toPct = (box) => (box ? { x: round1(box.x * 100), y: round1(box.y * 100), w: round1(box.w * 100), h: round1(box.h * 100) } : null);
export const fromPct = (p) => (p ? { x: Number(p.x) / 100, y: Number(p.y) / 100, w: Number(p.w) / 100, h: Number(p.h) / 100 } : null);
const round1 = (n) => Math.round(n * 10) / 10;

export function boxValid(p) {
  if (!p) return true;
  const n = ['x', 'y', 'w', 'h'].map((k) => Number(p[k]));
  return n.every((v) => Number.isFinite(v) && v >= 0) && n[2] > 0 && n[3] > 0 && n[0] + n[2] <= 100.01 && n[1] + n[3] <= 100.01;
}

/** "Free Harmain Pass 2027" → "free-harmain-pass-2027". */
export function slugify(title) {
  return String(title || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 50)
    .replace(/-+$/, '');
}

export const PASS_STATUS_LABEL = {
  PENDING: 'Sending',
  SENT: 'Sent on WhatsApp',
  WAITING_FOR_CHAT: 'Waiting for customer to message',
  FAILED: 'Not sent',
  DISABLED: 'No pass',
};
