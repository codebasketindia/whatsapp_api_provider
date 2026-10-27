import { z } from 'zod';
import { istLocalToIso } from '../../campaigns/components/schedule';

export const MIN_AHEAD_MS = 60_000;
export const MAX_AHEAD_DAYS = 90;

/** ISO instant → "2026-10-20T10:30" as seen in IST (value for a datetime-local picker). */
export function isoToIstLocal(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return new Date(d.getTime() + 5.5 * 3600000).toISOString().slice(0, 16);
}

/** Error text for an IST picker value, or null when it is between now+1 min and now+90 days. */
export function scheduleTimeError(local, now = new Date()) {
  const iso = istLocalToIso(local);
  if (!iso) return 'Choose a date and time';
  const t = new Date(iso).getTime();
  if (t < now.getTime() + MIN_AHEAD_MS) return 'Choose a time at least 1 minute from now';
  if (t > now.getTime() + MAX_AHEAD_DAYS * 86400000) return `Choose a time within ${MAX_AHEAD_DAYS} days`;
  return null;
}

/** Zod field for an IST datetime-local value (checked against the clock at validation time). */
export const scheduleTimeSchema = z.string().superRefine((v, ctx) => {
  const err = scheduleTimeError(v);
  if (err) ctx.addIssue({ code: z.ZodIssueCode.custom, message: err });
});

/**
 * Will the 24-hour window still be open at the chosen time?
 * Returns 'open' | 'closed' | 'unknown' (no window info yet).
 */
export function windowAtTime({ open, expiresAt }, scheduledIso) {
  if (!scheduledIso) return 'unknown';
  if (!open || !expiresAt) return open === false ? 'closed' : 'unknown';
  return new Date(scheduledIso).getTime() < new Date(expiresAt).getTime() ? 'open' : 'closed';
}

export const SCHEDULED_STATUS_LABEL = {
  SCHEDULED: 'Upcoming',
  SENDING: 'Sending',
  SENT: 'Sent',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};
