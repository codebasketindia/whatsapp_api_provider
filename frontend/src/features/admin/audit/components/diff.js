/** Flattens nested JSON into { path: displayValue }. Arrays of primitives are joined. */
export function flatten(value, prefix = '', out = {}) {
  if (value === null || value === undefined) {
    if (prefix) out[prefix] = value === null ? 'null' : '';
    return out;
  }
  if (Array.isArray(value)) {
    if (value.every((v) => v === null || typeof v !== 'object')) {
      out[prefix || '(value)'] = value.join(', ');
      return out;
    }
    value.forEach((v, i) => flatten(v, `${prefix}[${i}]`, out));
    return out;
  }
  if (typeof value === 'object') {
    const keys = Object.keys(value);
    if (keys.length === 0 && prefix) out[prefix] = '{}';
    keys.forEach((k) => flatten(value[k], prefix ? `${prefix}.${k}` : k, out));
    return out;
  }
  out[prefix || '(value)'] = String(value);
  return out;
}

/** Rows for a before/after table: [{ path, before, after, changed }] sorted with changes first. */
export function flattenForDiff(before, after) {
  const a = flatten(before);
  const b = flatten(after);
  const paths = Array.from(new Set([...Object.keys(a), ...Object.keys(b)]));
  return paths
    .map((path) => ({ path, before: a[path] ?? '—', after: b[path] ?? '—', changed: (a[path] ?? null) !== (b[path] ?? null) }))
    .sort((x, y) => Number(y.changed) - Number(x.changed) || x.path.localeCompare(y.path));
}
