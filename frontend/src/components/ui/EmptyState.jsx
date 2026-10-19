import { Inbox } from 'lucide-react';
import { cn } from '../../lib/cn';

/** Friendly empty/zero state with the next action. */
export default function EmptyState({ icon: Icon = Inbox, title, text, action, className, compact }) {
  return (
    <div className={cn('flex flex-col items-center justify-center text-center', compact ? 'py-8' : 'py-14', className)}>
      <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
        <Icon className="h-7 w-7" aria-hidden />
      </span>
      {title && <h3 className="text-base font-semibold text-ink">{title}</h3>}
      {text && <p className="mt-1 max-w-sm text-sm text-muted">{text}</p>}
      {action && <div className="mt-4 flex flex-wrap justify-center gap-2">{action}</div>}
    </div>
  );
}
