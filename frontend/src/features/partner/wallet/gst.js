import { toNumber } from '../../../lib/format';

const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;

/**
 * Gateway top-up breakdown (GST is added on top; the wallet gets the base amount + bonus).
 * config: PG1 { gstEnabled, gstPercent }. pack: optional credit pack (price, creditAmount, bonusAmount).
 */
export function calcTopup({ amount, pack, gstEnabled, gstPercent }) {
  const base = pack ? toNumber(pack.price) : toNumber(amount);
  const bonus = pack ? toNumber(pack.bonusAmount ?? toNumber(pack.creditAmount) - toNumber(pack.price)) : 0;
  const pct = gstEnabled ? toNumber(gstPercent) : 0;
  const gst = round2((base * pct) / 100);
  return { base: round2(base), bonus: round2(bonus), gstPercent: pct, gst, total: round2(base + gst), credit: round2(base + bonus) };
}
