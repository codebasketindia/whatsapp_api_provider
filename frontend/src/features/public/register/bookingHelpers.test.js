import { describe, expect, it } from 'vitest';
import { computeTotals, discountPercent, optionalIds, rupees, toggleId } from './bookingHelpers';

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
});
