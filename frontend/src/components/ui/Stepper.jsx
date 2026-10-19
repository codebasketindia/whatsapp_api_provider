import { Check, Loader2, X } from 'lucide-react';
import { cn } from '../../lib/cn';

/**
 * steps: [{ key, label, description?, status?: 'done'|'current'|'error'|'pending' }]
 * or pass `current` index to derive status. orientation: horizontal | vertical
 */
export default function Stepper({ steps, current, orientation = 'horizontal', className }) {
  const items = steps.map((s, i) => ({
    ...s,
    status: s.status || (current === undefined ? 'pending' : i < current ? 'done' : i === current ? 'current' : 'pending'),
  }));
  const dot = (status, i) => (
    <span
      className={cn(
        'flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 text-sm font-semibold',
        status === 'done' && 'border-brand-600 bg-brand-600 text-white',
        status === 'current' && 'border-brand-600 bg-surface text-brand-700 dark:text-brand-300',
        status === 'loading' && 'border-brand-600 bg-surface text-brand-700',
        status === 'error' && 'border-red-600 bg-red-600 text-white',
        status === 'pending' && 'border-line bg-surface text-muted',
      )}
    >
      {status === 'done' ? <Check className="h-4 w-4" /> : status === 'error' ? <X className="h-4 w-4" /> : status === 'loading' ? <Loader2 className="h-4 w-4 animate-spin" /> : i + 1}
    </span>
  );
  if (orientation === 'vertical') {
    return (
      <ol className={cn('space-y-4', className)}>
        {items.map((s, i) => (
          <li key={s.key || i} className="flex gap-3">
            {dot(s.status, i)}
            <div className="pt-1">
              <p className={cn('text-sm font-medium', s.status === 'pending' ? 'text-muted' : 'text-ink')}>{s.label}</p>
              {s.description && <p className={cn('text-sm', s.status === 'error' ? 'text-red-600' : 'text-muted')}>{s.description}</p>}
            </div>
          </li>
        ))}
      </ol>
    );
  }
  return (
    <ol className={cn('flex items-center gap-2', className)}>
      {items.map((s, i) => (
        <li key={s.key || i} className={cn('flex items-center gap-2', i < items.length - 1 && 'flex-1')}>
          {dot(s.status, i)}
          <span className={cn('hidden text-sm font-medium sm:inline', s.status === 'pending' ? 'text-muted' : 'text-ink')}>{s.label}</span>
          {i < items.length - 1 && <span className={cn('h-0.5 flex-1 rounded', s.status === 'done' ? 'bg-brand-600' : 'bg-line')} aria-hidden />}
        </li>
      ))}
    </ol>
  );
}
