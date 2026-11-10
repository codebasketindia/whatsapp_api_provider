// Pure helpers for the public booking page.

const num = (v) => Number(v ?? 0) || 0;

/**
 * Mandatory packages are always in; returns the chosen packages and the totals. With a combo offer, choosing every
 * combo package makes them cost the combo price together (the server applies the same rule).
 */
export function computeTotals(packages = [], selectedIds = [], combo = null) {
  const chosen = new Set(selectedIds.map(Number));
  const items = packages.filter((p) => p.mandatory || chosen.has(Number(p.id)));
  const mrp = items.reduce((s, p) => s + num(p.mrp), 0);
  let total = items.reduce((s, p) => s + num(p.salePrice), 0);
  let comboDiscount = 0;
  if (comboApplies(packages, selectedIds, combo)) {
    const comboSum = items.filter((p) => combo.codes.includes(p.code)).reduce((s, p) => s + num(p.salePrice), 0);
    comboDiscount = Math.max(0, comboSum - num(combo.price));
    total -= comboDiscount;
  }
  return { items, mrp, total, comboDiscount, savings: Math.max(0, mrp - total), passes: items.filter((p) => p.issuePass !== false).length };
}

export function comboIds(packages = [], combo = null) {
  return combo ? packages.filter((p) => combo.codes.includes(p.code)).map((p) => p.id) : [];
}

export function comboApplies(packages, selectedIds, combo) {
  const ids = comboIds(packages, combo);
  return ids.length > 0 && ids.every((id) => selectedIds.map(Number).includes(Number(id)));
}

/** Tapping the combo card selects all its packages (or removes them when already all selected). */
export function toggleCombo(packages, selectedIds, combo) {
  const ids = comboIds(packages, combo);
  return comboApplies(packages, selectedIds, combo) ? selectedIds.filter((x) => !ids.includes(x)) : [...new Set([...selectedIds, ...ids])];
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
