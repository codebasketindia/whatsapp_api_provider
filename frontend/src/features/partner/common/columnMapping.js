// Pure helpers for mapping spreadsheet columns (contact import and campaigns).

const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]/g, '');

const HINTS = {
  phone: ['phone', 'mobile', 'mobileno', 'mobilenumber', 'whatsapp', 'whatsappnumber', 'number', 'contact', 'contactnumber', 'msisdn', 'phonenumber'],
  name: ['name', 'fullname', 'customername', 'customer', 'firstname', 'contactname'],
  email: ['email', 'emailid', 'emailaddress', 'mail'],
  tags: ['tags', 'tag', 'labels', 'label'],
};

/** Best matching column for a field (phone/name/email/tags) or '' if none. */
export function guessColumn(columns = [], field) {
  const hints = HINTS[field] || [norm(field)];
  const exact = columns.find((c) => hints.includes(norm(c)));
  if (exact) return exact;
  return columns.find((c) => hints.some((h) => norm(c).includes(h))) || '';
}

/** Suggested attribute key for a column name: "Order ID" -> "order_id". */
export function toAttributeKey(column) {
  return String(column || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 40);
}

/**
 * C5 mapping: { phone, name, email, tags, attributes: { column: key } }.
 * extraColumns: [{ column, key, include }]
 */
export function buildContactImportMapping({ phone, name, email, tags, extraColumns = [] }) {
  const attributes = {};
  extraColumns.filter((c) => c.include && c.key).forEach((c) => {
    attributes[c.column] = c.key;
  });
  const out = { phone };
  if (name) out.name = name;
  if (email) out.email = email;
  if (tags) out.tags = tags;
  if (Object.keys(attributes).length) out.attributes = attributes;
  return out;
}

/**
 * Campaign variable source per template variable:
 *   { kind: 'column', value: 'City' } -> "City"
 *   { kind: 'attribute', value: 'city' } -> "{{city}}"
 *   { kind: 'static', value: 'Hello' } -> "=Hello"
 */
export function encodeVariableSource(src) {
  if (!src || !src.kind) return '';
  if (src.kind === 'attribute') return `{{${src.value}}}`;
  if (src.kind === 'static') return `=${src.value ?? ''}`;
  return src.value || '';
}

export function decodeVariableSource(str) {
  if (!str) return { kind: 'column', value: '' };
  if (str.startsWith('=')) return { kind: 'static', value: str.slice(1) };
  const m = /^\{\{(.+)\}\}$/.exec(str);
  if (m) return { kind: 'attribute', value: m[1] };
  return { kind: 'column', value: str };
}

/** CP2 mapping object from wizard state. variables: { varName: {kind, value} } */
export function buildCampaignMapping({ phoneColumn, variables = {}, headerMedia }) {
  const vars = {};
  Object.entries(variables).forEach(([k, v]) => {
    vars[k] = encodeVariableSource(v);
  });
  const out = { phone: phoneColumn || undefined, variables: vars };
  if (headerMedia) out.headerMedia = headerMedia;
  return out;
}

/** True when every template variable has a usable source. */
export function campaignMappingComplete({ audienceType, phoneColumn, variables = {}, required = [] }) {
  if (audienceType === 'FILE' && !phoneColumn) return false;
  return required.every((k) => {
    const v = variables[k];
    if (!v) return false;
    if (v.kind === 'static') return String(v.value ?? '').trim().length > 0;
    return Boolean(v.value);
  });
}
