import { describe, expect, it } from 'vitest';
import { statusTone } from './statusColors';

describe('statusTone', () => {
  it('uses green for success states', () => {
    ['APPROVED', 'DELIVERED', 'read', 'PAID', 'CONNECTED'].forEach((s) => expect(statusTone(s)).toBe('green'));
  });
  it('uses amber for pending states', () => {
    ['PENDING', 'QUEUED', 'SCHEDULED', 'YELLOW'].forEach((s) => expect(statusTone(s)).toBe('amber'));
  });
  it('uses red for failures', () => {
    ['FAILED', 'REJECTED', 'SUSPENDED', 'RED'].forEach((s) => expect(statusTone(s)).toBe('red'));
  });
  it('falls back to gray', () => {
    expect(statusTone('DRAFT')).toBe('gray');
    expect(statusTone('SOMETHING_NEW')).toBe('gray');
    expect(statusTone(null)).toBe('gray');
  });
});
