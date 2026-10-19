import { cn } from '../../lib/cn';

/**
 * Large tappable radio options. options: [{ value, label, description, icon, badge, disabled }]
 */
export default function RadioCards({ name, value, onChange, options = [], columns = 1, label, error, className }) {
  const cols = { 1: 'grid-cols-1', 2: 'grid-cols-1 sm:grid-cols-2', 3: 'grid-cols-1 sm:grid-cols-3' };
  return (
    <fieldset className={className}>
      {label && <legend className="label-base">{label}</legend>}
      <div role="radiogroup" className={cn('grid gap-3', cols[columns])}>
        {options.map((o) => {
          const selected = String(value) === String(o.value);
          const Icon = o.icon;
          return (
            <label
              key={o.value}
              className={cn(
                'relative flex min-h-tap cursor-pointer items-start gap-3 rounded-xl border p-4 transition-colors',
                selected ? 'border-brand-600 bg-brand-50 ring-1 ring-brand-600 dark:bg-brand-500/10' : 'border-line bg-surface hover:bg-surface-2',
                o.disabled && 'cursor-not-allowed opacity-50',
              )}
            >
              <input
                type="radio"
                name={name}
                value={o.value}
                checked={selected}
                disabled={o.disabled}
                onChange={() => onChange?.(o.value)}
                className="sr-only"
              />
              {Icon && <Icon className={cn('mt-0.5 h-5 w-5 shrink-0', selected ? 'text-brand-700 dark:text-brand-300' : 'text-muted')} aria-hidden />}
              <span className="flex-1">
                <span className="flex items-center gap-2 text-sm font-semibold text-ink">
                  {o.label}
                  {o.badge && <span className="rounded-full bg-brand-600 px-2 py-0.5 text-[10px] font-semibold uppercase text-white">{o.badge}</span>}
                </span>
                {o.description && <span className="mt-0.5 block text-sm text-muted">{o.description}</span>}
              </span>
              <span
                aria-hidden
                className={cn('mt-0.5 h-5 w-5 shrink-0 rounded-full border-2', selected ? 'border-brand-600 bg-brand-600 shadow-[inset_0_0_0_3px_white] dark:shadow-[inset_0_0_0_3px_rgb(21,28,34)]' : 'border-line')}
              />
            </label>
          );
        })}
      </div>
      {error && <p className="mt-1 text-xs text-red-600">{typeof error === 'string' ? error : error.message}</p>}
    </fieldset>
  );
}
