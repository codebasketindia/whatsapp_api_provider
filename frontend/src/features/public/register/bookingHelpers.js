// Pure helpers for the public booking page.

const num = (v) => Number(v ?? 0) || 0;

/** Mandatory packages are always in; returns the chosen packages and the totals. */
export function computeTotals(packages = [], selectedIds = []) {
  const chosen = new Set(selectedIds.map(Number));
  const items = packages.filter((p) => p.mandatory || chosen.has(Number(p.id)));
  const mrp = items.reduce((s, p) => s + num(p.mrp), 0);
  const total = items.reduce((s, p) => s + num(p.salePrice), 0);
  return { items, mrp, total, savings: Math.max(0, mrp - total) };
}

/** Optional ids to send (mandatory ones are added by the server anyway). */
export const optionalIds = (packages = [], selectedIds = []) =>
  packages.filter((p) => !p.mandatory && selectedIds.map(Number).includes(Number(p.id))).map((p) => p.id);

export function toggleId(selectedIds, id) {
  return selectedIds.includes(id) ? selectedIds.filter((x) => x !== id) : [...selectedIds, id];
}

/** "₹5,000" without decimals when whole. */
export function rupees(v) {
  const n = num(v);
  return `₹${n.toLocaleString('en-IN', { minimumFractionDigits: n % 1 ? 2 : 0, maximumFractionDigits: 2 })}`;
}

export const discountPercent = (mrp, sale) => (num(mrp) > num(sale) && num(mrp) > 0 ? Math.round(((num(mrp) - num(sale)) / num(mrp)) * 100) : 0);
