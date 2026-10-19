/** Categories priced per country in the grid (SERVICE uses the plan's session fee). */
export const GRID_CATEGORIES = ['MARKETING', 'UTILITY', 'AUTHENTICATION', 'AUTHENTICATION_INTERNATIONAL'];
export const ALL_COUNTRIES = '*';

const PRICE_RE = /^\d+(\.\d{1,4})?$/;

/** items [{countryCode, category, price}] → rows [{ countryCode, prices: { CATEGORY: '0.8600' } }], India first, "*" last. */
export function itemsToGrid(items = []) {
  const map = new Map();
  items.forEach((i) => {
    if (!GRID_CATEGORIES.includes(i.category)) return;
    const row = map.get(i.countryCode) || { countryCode: i.countryCode, prices: {} };
    row.prices[i.category] = i.price === null || i.price === undefined ? '' : String(Number(i.price));
    map.set(i.countryCode, row);
  });
  if (!map.has('IN')) map.set('IN', { countryCode: 'IN', prices: {} });
  return sortRows([...map.values()]);
}

export function sortRows(rows) {
  const rank = (c) => (c === 'IN' ? 0 : c === ALL_COUNTRIES ? 2 : 1);
  return [...rows].sort((a, b) => rank(a.countryCode) - rank(b.countryCode) || a.countryCode.localeCompare(b.countryCode));
}

/** rows → items; empty cells are skipped (no price = that category cannot be sent to that country). */
export function gridToItems(rows) {
  const items = [];
  rows.forEach((r) => {
    GRID_CATEGORIES.forEach((cat) => {
      const v = r.prices?.[cat];
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        items.push({ countryCode: r.countryCode, category: cat, price: String(v).trim() });
      }
    });
  });
  return items;
}

/** Returns a list of human readable problems; empty when valid. */
export function validateGrid(rows) {
  const errors = [];
  const seen = new Set();
  rows.forEach((r) => {
    const c = r.countryCode;
    if (!(c === ALL_COUNTRIES || /^[A-Z]{2}$/.test(c))) errors.push(`"${c}" is not a 2-letter country code`);
    if (seen.has(c)) errors.push(`${c} is listed twice`);
    seen.add(c);
    GRID_CATEGORIES.forEach((cat) => {
      const v = r.prices?.[cat];
      if (v !== undefined && v !== null && String(v).trim() !== '' && !PRICE_RE.test(String(v).trim())) {
        errors.push(`${c} ${cat.toLowerCase().replace(/_/g, ' ')}: "${v}" is not a valid price`);
      }
    });
  });
  return errors;
}
