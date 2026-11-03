import { describe, expect, it } from 'vitest';
import { boxValid, delayToMinutes, followUpTiming, fromPct, messageSummary, minutesToDelay, slugify, toPct, HARMAIN_LAYOUT } from './regHelpers';

describe('registration helpers', () => {
  it('converts delays both ways', () => {
    expect(minutesToDelay(4320)).toEqual({ amount: 3, unit: 'days' });
    expect(minutesToDelay(120)).toEqual({ amount: 2, unit: 'hours' });
    expect(minutesToDelay(45)).toEqual({ amount: 45, unit: 'minutes' });
    expect(delayToMinutes(2, 'days')).toBe(2880);
    expect(delayToMinutes(1.5, 'hours')).toBe(90);
  });

  it('describes timing and messages', () => {
    expect(followUpTiming({ delayMinutes: 1440 }, () => '')).toBe('1 day after registering');
    expect(followUpTiming({ delayMinutes: 60 * 5 }, () => '')).toBe('5 hours after registering');
    expect(followUpTiming({ sendAt: '2026-11-30T04:30:00Z' }, () => '30 Nov')).toBe('On 30 Nov');
    expect(messageSummary({ type: 'text', text: { body: 'Hi {name}' } })).toBe('Hi {name}');
    expect(messageSummary({ type: 'template', template: { name: 'reminder' } })).toBe('Template: reminder');
    expect(messageSummary({ type: 'image', media: { caption: 'Brochure' } })).toBe('image: Brochure');
  });

  it('round-trips layout boxes and validates them', () => {
    const pct = toPct(HARMAIN_LAYOUT.name);
    expect(pct.x).toBeCloseTo(33.5, 1);
    expect(fromPct(pct).x).toBeCloseTo(HARMAIN_LAYOUT.name.x, 2);
    expect(boxValid({ x: 10, y: 10, w: 50, h: 5 })).toBe(true);
    expect(boxValid({ x: 80, y: 10, w: 50, h: 5 })).toBe(false);
    expect(boxValid({ x: 10, y: 10, w: 0, h: 5 })).toBe(false);
  });

  it('makes link names', () => {
    expect(slugify('Free Harmain Ziyarat Pass 2027!')).toBe('free-harmain-ziyarat-pass-2027');
  });
});
