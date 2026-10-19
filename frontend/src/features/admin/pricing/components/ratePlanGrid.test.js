import { describe, expect, it } from 'vitest';
import { gridToItems, itemsToGrid, validateGrid } from './ratePlanGrid';

describe('rate plan grid', () => {
  it('groups items by country with India first and * last', () => {
    const rows = itemsToGrid([
      { countryCode: '*', category: 'MARKETING', price: '1.5000' },
      { countryCode: 'AE', category: 'UTILITY', price: '0.3000' },
      { countryCode: 'IN', category: 'MARKETING', price: '0.8600' },
      { countryCode: 'IN', category: 'SERVICE', price: '0.1' },
    ]);
    expect(rows.map((r) => r.countryCode)).toEqual(['IN', 'AE', '*']);
    expect(rows[0].prices).toEqual({ MARKETING: '0.86' });
  });
  it('always contains an India row', () => {
    expect(itemsToGrid([])).toEqual([{ countryCode: 'IN', prices: {} }]);
  });
  it('converts back to items skipping empty cells', () => {
    expect(gridToItems([{ countryCode: 'IN', prices: { MARKETING: '0.86', UTILITY: '' } }])).toEqual([
      { countryCode: 'IN', category: 'MARKETING', price: '0.86' },
    ]);
  });
  it('validates codes, duplicates and prices', () => {
    const errs = validateGrid([
      { countryCode: 'IN', prices: { MARKETING: 'abc' } },
      { countryCode: 'IN', prices: {} },
      { countryCode: 'IND', prices: {} },
    ]);
    expect(errs).toHaveLength(3);
    expect(validateGrid([{ countryCode: '*', prices: { UTILITY: '0.1234' } }])).toEqual([]);
  });
});
