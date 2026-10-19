import { describe, expect, it } from 'vitest';
import { flatten, flattenForDiff } from './diff';

describe('audit diff', () => {
  it('flattens nested objects and primitive arrays', () => {
    expect(flatten({ a: 1, b: { c: 'x', d: [1, 2] } })).toEqual({ a: '1', 'b.c': 'x', 'b.d': '1, 2' });
  });
  it('marks changed fields first', () => {
    const rows = flattenForDiff({ status: 'ACTIVE', name: 'A' }, { status: 'SUSPENDED', name: 'A' });
    expect(rows[0]).toEqual({ path: 'status', before: 'ACTIVE', after: 'SUSPENDED', changed: true });
    expect(rows[1].changed).toBe(false);
  });
  it('handles null before (create)', () => {
    const rows = flattenForDiff(null, { amount: '10.00' });
    expect(rows).toEqual([{ path: 'amount', before: '—', after: '10.00', changed: true }]);
  });
});
