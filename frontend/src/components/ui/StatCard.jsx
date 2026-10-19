import { cn } from '../../lib/cn';
import Skeleton from './Skeleton';

/** KPI tile: label, big value, optional icon, hint and tone. */
export default function StatCard({ label, value, icon: Icon, hint, tone = 'default', loading, onClick, className }) {
  const tones = {
    default: 'text-brand-700 bg-brand-50 dark:bg-brand-500/15 dark:text-brand-300',
    green: 'text-green-700 bg-green-50 dark:bg-green-500/15 dark:text-green-300',
    amber: 'text-amber-700 bg-amber-50 dark:bg-amber-500/15 dark:text-amber-300',
    red: 'text-red-700 bg-red-50 dark:bg-red-500/15 dark:text-red-300',
    blue: 'text-blue-700 bg-blue-50 dark:bg-blue-500/15 dark:text-blue-300',
  };
  const Comp = onClick ? 'button' : 'div';
  return (
    <Comp
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={cn('card flex w-full items-start gap-3 p-4 text-left', onClick && 'transition-colors hover:bg-surface-2', className)}
    >
      {Icon && (
        <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', tones[tone])}>
          <Icon className="h-5 w-5" aria-hidden />
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm text-muted">{label}</span>
        {loading ? <Skeleton className="mt-1 h-7 w-24" /> : <span className="mt-0.5 block truncate text-2xl font-semibold text-ink">{value}</span>}
        {hint && !loading && <span className="mt-0.5 block truncate text-xs text-muted">{hint}</span>}
      </span>
    </Comp>
  );
}
