import { describe, expect, it } from 'vitest';
import { calcTopup } from './gst';

describe('calcTopup (GST added on top)', () => {
  it('adds GST to a custom amount and credits the base', () => {
    expect(calcTopup({ amount: 1000, gstEnabled: true, gstPercent: 18 })).toEqual({ base: 1000, bonus: 0, gstPercent: 18, gst: 180, total: 1180, credit: 1000 });
  });

  it('ignores GST when disabled', () => {
    expect(calcTopup({ amount: '500.50', gstEnabled: false, gstPercent: '18' })).toMatchObject({ gst: 0, total: 500.5, credit: 500.5 });
  });

  it('adds pack bonus to the wallet credit only', () => {
    const r = calcTopup({ pack: { price: '1000.0000', creditAmount: '1100.0000', bonusAmount: '100.0000' }, gstEnabled: true, gstPercent: 18 });
    expect(r).toMatchObject({ base: 1000, bonus: 100, total: 1180, credit: 1100 });
  });

  it('rounds GST to paise', () => {
    expect(calcTopup({ amount: 333.33, gstEnabled: true, gstPercent: 18 }).gst).toBe(60);
  });
});
