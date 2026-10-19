import { AlertTriangle, CheckCircle2, Info, XCircle, X } from 'lucide-react';
import { cn } from '../../lib/cn';

const STYLES = {
  info: { cls: 'bg-blue-50 text-blue-900 border-blue-200 dark:bg-blue-500/10 dark:text-blue-200 dark:border-blue-500/30', icon: Info },
  success: { cls: 'bg-green-50 text-green-900 border-green-200 dark:bg-green-500/10 dark:text-green-200 dark:border-green-500/30', icon: CheckCircle2 },
  warning: { cls: 'bg-amber-50 text-amber-900 border-amber-200 dark:bg-amber-500/10 dark:text-amber-200 dark:border-amber-500/30', icon: AlertTriangle },
  error: { cls: 'bg-red-50 text-red-900 border-red-200 dark:bg-red-500/10 dark:text-red-200 dark:border-red-500/30', icon: XCircle },
};

/** Inline message box. `action` renders on the right (stacked on phones). */
export default function Alert({ variant = 'info', title, children, action, onClose, className }) {
  const { cls, icon: Icon } = STYLES[variant] || STYLES.info;
  return (
    <div role={variant === 'error' ? 'alert' : 'status'} className={cn('flex flex-col gap-3 rounded-xl border p-4 sm:flex-row sm:items-center', cls, className)}>
      <div className="flex flex-1 items-start gap-3">
        <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden />
        <div className="min-w-0 text-sm">
          {title && <p className="font-semibold">{title}</p>}
          {children && <div className={cn(title && 'mt-0.5')}>{children}</div>}
        </div>
      </div>
      {(action || onClose) && (
        <div className="flex items-center gap-2 sm:shrink-0">
          {action}
          {onClose && (
            <button type="button" onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-lg hover:bg-black/5" aria-label="Dismiss">
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      )}
    </div>
  );
}

/** Full-width strip at the top of a layout. */
export function Banner({ variant = 'warning', children, action, className }) {
  const { cls, icon: Icon } = STYLES[variant] || STYLES.warning;
  return (
    <div className={cn('flex flex-wrap items-center gap-x-3 gap-y-2 border-b px-4 py-2.5 text-sm', cls, className)} role="status">
      <Icon className="h-4 w-4 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">{children}</div>
      {action}
    </div>
  );
}
