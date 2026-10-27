import { describe, expect, it } from 'vitest';
import { isoToIstLocal, scheduleTimeError, windowAtTime } from './scheduleTime';

const now = new Date('2026-10-20T05:00:00.000Z'); // 10:30 IST

describe('scheduleTimeError', () => {
  it('accepts a time between 1 minute and 90 days ahead (IST)', () => {
    expect(scheduleTimeError('2026-10-20T10:32', now)).toBeNull();
    expect(scheduleTimeError('2027-01-17T10:30', now)).toBeNull();
  });
  it('rejects past, too-soon and too-far times', () => {
    expect(scheduleTimeError('2026-10-20T10:00', now)).toMatch(/1 minute/);
    expect(scheduleTimeError('2026-10-20T10:30', now)).toMatch(/1 minute/);
    expect(scheduleTimeError('2027-01-19T10:30', now)).toMatch(/90 days/);
  });
  it('rejects empty input', () => {
    expect(scheduleTimeError('', now)).toMatch(/Choose/);
  });
});

describe('isoToIstLocal', () => {
  it('shows a UTC instant as India time', () => {
    expect(isoToIstLocal('2026-10-20T05:00:00.000Z')).toBe('2026-10-20T10:30');
    expect(isoToIstLocal(null)).toBe('');
  });
});

describe('windowAtTime', () => {
  const win = { open: true, expiresAt: '2026-10-20T12:00:00.000Z' };
  it('is open before the window expires and closed after', () => {
    expect(windowAtTime(win, '2026-10-20T11:00:00.000Z')).toBe('open');
    expect(windowAtTime(win, '2026-10-20T13:00:00.000Z')).toBe('closed');
  });
  it('is closed when the window is already closed', () => {
    expect(windowAtTime({ open: false, expiresAt: null }, '2026-10-20T11:00:00.000Z')).toBe('closed');
  });
});
