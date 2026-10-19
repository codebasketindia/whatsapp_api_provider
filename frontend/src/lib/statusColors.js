// One colour language for every status badge (see docs/phase-0/04-screens-routes.md).
const GREEN = [
  'APPROVED', 'DELIVERED', 'READ', 'PAID', 'CONNECTED', 'ACTIVE', 'GREEN', 'SUCCESS', 'COMPLETED',
  'CREDIT', 'VALID', 'DONE', 'PROCESSED', 'CHARGED', 'SYNCED', 'VERIFIED', 'RECEIVED', 'UP', 'HIGH',
];
const AMBER = [
  'PENDING', 'QUEUED', 'SENT', 'SCHEDULED', 'YELLOW', 'PAUSED', 'ACCEPTED', 'HELD', 'HOLD', 'ATTEMPTED',
  'CREATED', 'PENDING_REVIEW', 'IN_APPEAL', 'PENDING_DELETION', 'PENDING_SETUP', 'WARNING', 'MEDIUM',
  'LIMIT_EXCEEDED', 'FLAGGED', 'RESTRICTED', 'ACTION_REQUIRED', 'PROCESSING', 'EXPIRING',
];
const RED = [
  'FAILED', 'REJECTED', 'SUSPENDED', 'RED', 'DISABLED', 'INTERNAL_REJECTED', 'ERROR', 'INVALID', 'EXPIRED',
  'BANNED', 'DEAD', 'DEBIT', 'DISCONNECTED', 'BLOCKED', 'OPTED_OUT', 'LOW', 'DOWN', 'DECLINED',
];
const GRAY = ['DRAFT', 'CANCELLED', 'UNKNOWN', 'DELETED', 'ARCHIVED', 'NONE', 'SKIPPED', 'UNBLOCKED', 'IGNORED', 'OFFLINE'];
const BLUE = ['RUNNING', 'INFO', 'RELEASE', 'REFUND', 'REFUNDED', 'RELEASED'];

export const TONES = {
  green: 'bg-green-100 text-green-800 ring-green-600/20 dark:bg-green-500/15 dark:text-green-300 dark:ring-green-400/30',
  amber: 'bg-amber-100 text-amber-800 ring-amber-600/20 dark:bg-amber-500/15 dark:text-amber-300 dark:ring-amber-400/30',
  red: 'bg-red-100 text-red-800 ring-red-600/20 dark:bg-red-500/15 dark:text-red-300 dark:ring-red-400/30',
  gray: 'bg-gray-100 text-gray-700 ring-gray-500/20 dark:bg-gray-500/15 dark:text-gray-300 dark:ring-gray-400/30',
  blue: 'bg-blue-100 text-blue-800 ring-blue-600/20 dark:bg-blue-500/15 dark:text-blue-300 dark:ring-blue-400/30',
  brand: 'bg-brand-100 text-brand-900 ring-brand-600/20 dark:bg-brand-500/15 dark:text-brand-300 dark:ring-brand-400/30',
};

/** Returns tone name (green/amber/red/gray/blue) for a status string. */
export function statusTone(status) {
  if (!status) return 'gray';
  const s = String(status).toUpperCase();
  if (GREEN.includes(s)) return 'green';
  if (AMBER.includes(s)) return 'amber';
  if (RED.includes(s)) return 'red';
  if (BLUE.includes(s)) return 'blue';
  if (GRAY.includes(s)) return 'gray';
  return 'gray';
}

/** Tailwind classes for a status badge. */
export function statusClasses(status) {
  return TONES[statusTone(status)];
}
