import { describe, expect, it } from 'vitest';
import { comboApplies, toggleCombo, computeTotals, discountPercent, optionalIds, rupees, toggleId } from './bookingHelpers';

const P = [
  { id: 1, mandatory: true, mrp: '49.00', salePrice: '49.00' },
  { id: 2, mandatory: false, mrp: '5000.00', salePrice: '3000.00' },
  { id: 3, mandatory: false, mrp: '5000.00', salePrice: '3000.00' },
];

describe('booking helpers', () => {
  it('always includes the mandatory fee and adds chosen packages', () => {
    expect(computeTotals(P, []).total).toBe(49);
    const t = computeTotals(P, [2, 3]);
    expect(t).toMatchObject({ mrp: 10049, total: 6049, savings: 4000 });
    expect(t.items.map((i) => i.id)).toEqual([1, 2, 3]);
  });
  it('sends only optional ids and toggles', () => {
    expect(optionalIds(P, [1, 3])).toEqual([3]);
    expect(toggleId([2], 2)).toEqual([]);
    expect(toggleId([2], 3)).toEqual([2, 3]);
  });
  it('formats rupees and discounts', () => {
    expect(rupees('5000.00')).toBe('₹5,000');
    expect(rupees(49.5)).toBe('₹49.50');
    expect(discountPercent('5000', '3000')).toBe(40);
    expect(discountPercent('49', '49')).toBe(0);
  });

  it('applies the combo price when every combo pass is chosen', () => {
    const pk = [
      { id: 1, code: 'REG', mandatory: true, mrp: 49, salePrice: 49, issuePass: false },
      { id: 2, code: 'HZS', mrp: 0, salePrice: 0 },
      { id: 3, code: 'SMM', mrp: 6000, salePrice: 3000 },
      { id: 4, code: 'AMM', mrp: 6000, salePrice: 3000 },
      { id: 5, code: 'ACZ', mrp: 8000, salePrice: 4000 },
    ];
    const combo = { codes: ['SMM', 'AMM', 'ACZ'], price: '9000.00' };
    expect(computeTotals(pk, [2, 3, 4], combo).total).toBe(6049);
    const all = toggleCombo(pk, [2], combo);
    expect(comboApplies(pk, all, combo)).toBe(true);
    expect(computeTotals(pk, all, combo)).toMatchObject({ total: 9049, comboDiscount: 1000, passes: 4 });
    expect(toggleCombo(pk, all, combo)).toEqual([2]);
  });
});
