import { toNumber } from '../../../../lib/format';

const round2 = (n) => Math.round(n * 100) / 100;

/** Bonus and total wallet credit for a pack: { price, bonus, credit }. */
export function packCredit({ price, bonusType, bonusValue }) {
  const p = toNumber(price);
  const v = toNumber(bonusValue);
  const bonus = round2(bonusType === 'PERCENT' ? (p * v) / 100 : v);
  return { price: p, bonus, credit: round2(p + bonus) };
}
