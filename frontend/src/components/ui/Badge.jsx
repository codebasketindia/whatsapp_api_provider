import { cn } from '../../lib/cn';
import { TONES, statusClasses } from '../../lib/statusColors';
import { humanize } from '../../lib/format';

export default function Badge({ tone = 'gray', className, children, dot }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset', TONES[tone] || TONES.gray, className)}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden />}
      {children}
    </span>
  );
}

/** Badge coloured by status (green success / amber pending / red failed …). */
export function StatusBadge({ status, label, className }) {
  if (!status) return <span className="text-muted">—</span>;
  return (
    <span className={cn('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset', statusClasses(status), className)}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden />
      {label || humanize(status)}
    </span>
  );
}
