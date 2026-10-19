/** "2026-10-20T10:30" typed in IST → ISO UTC string. */
export function istLocalToIso(local) {
  if (!local) return null;
  const withSeconds = local.length === 16 ? `${local}:00` : local;
  const d = new Date(`${withSeconds}+05:30`);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

/** Current IST time as a datetime-local value (min for the picker). */
export function nowIstLocal(addMinutes = 0) {
  const d = new Date(Date.now() + addMinutes * 60000 + 5.5 * 3600000);
  return d.toISOString().slice(0, 16);
}

export const CAMPAIGN_STATUS_LABEL = {
  DRAFT: 'Draft',
  SCHEDULED: 'Scheduled',
  RUNNING: 'Sending',
  PAUSED: 'Paused',
  COMPLETED: 'Finished',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed',
};

export const PAUSE_REASON_LABEL = {
  USER: 'Paused by you',
  ADMIN: 'Paused by our team',
  INSUFFICIENT_BALANCE: 'Wallet balance ran out',
  SENDER_QUALITY: 'Number quality dropped or number unavailable',
  TEMPLATE_PAUSED: 'Template was paused by Meta',
};

export const SKIP_REASON_LABEL = {
  INVALID_NUMBER: 'Invalid number',
  DUPLICATE: 'Duplicate',
  OPTED_OUT: 'Opted out',
  BLOCKED: 'Blocked',
  MISSING_VARIABLE: 'Missing value',
  CANCELLED: 'Campaign cancelled',
};
