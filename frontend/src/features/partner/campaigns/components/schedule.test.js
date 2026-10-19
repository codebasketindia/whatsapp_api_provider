import { describe, expect, it } from 'vitest';
import { istLocalToIso } from './schedule';

describe('istLocalToIso', () => {
  it('treats the picker value as India time', () => {
    expect(istLocalToIso('2026-10-20T10:30')).toBe('2026-10-20T05:00:00.000Z');
  });
  it('returns null for empty/invalid input', () => {
    expect(istLocalToIso('')).toBeNull();
    expect(istLocalToIso('nope')).toBeNull();
  });
});
