import { describe, expect, it } from 'vitest';
import { addDaysToDateParam, formatDate, formatDateTime, formatMoney, formatPhone, formatRelative, humanize, toIstDateParam, toNumber } from './format';

describe('format', () => {
  it('formats rupees in Indian grouping with 2 decimals', () => {
    expect(formatMoney('123456')).toBe('₹1,23,456.00');
    expect(formatMoney('1000.0000')).toBe('₹1,000.00');
    expect(formatMoney(0)).toBe('₹0.00');
    expect(formatMoney(null)).toBe('—');
  });

  it('shows up to 4 decimals when precise', () => {
    expect(formatMoney('0.8615', { precise: true })).toBe('₹0.8615');
  });

  it('parses money strings safely', () => {
    expect(toNumber('1,234.50')).toBe(1234.5);
    expect(toNumber('abc')).toBe(0);
  });

  it('converts UTC instants to IST dates', () => {
    // 20:00 UTC on 19 Oct is 01:30 IST on 20 Oct
    expect(toIstDateParam('2026-10-19T20:00:00Z')).toBe('2026-10-20');
    expect(formatDate('2026-10-19T20:00:00Z')).toMatch(/20 Oct 2026/);
    expect(formatDateTime('2026-10-19T08:30:00Z').toLowerCase()).toMatch(/2:00\s?pm/);
  });

  it('shifts date params across month ends', () => {
    expect(addDaysToDateParam('2026-10-01', -1)).toBe('2026-09-30');
    expect(addDaysToDateParam('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('formats relative time', () => {
    const now = new Date('2026-10-19T10:00:00Z');
    expect(formatRelative('2026-10-19T09:59:50Z', now)).toBe('just now');
    expect(formatRelative('2026-10-19T09:55:00Z', now)).toMatch(/5 minutes ago/);
  });

  it('formats indian phone numbers and enums', () => {
    expect(formatPhone('919876543210')).toBe('+91 98765 43210');
    expect(humanize('PENDING_REVIEW')).toBe('Pending review');
  });
});
