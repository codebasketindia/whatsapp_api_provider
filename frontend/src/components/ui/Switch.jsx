import { useId } from 'react';
import { cn } from '../../lib/cn';

/** Accessible toggle switch (controlled). */
export default function Switch({ checked, onChange, label, description, disabled, className, id }) {
  const autoId = useId();
  const swId = id || autoId;
  return (
    <div className={cn('flex min-h-tap items-center justify-between gap-4', className)}>
      {(label || description) && (
        <label htmlFor={swId} className="cursor-pointer text-sm">
          {label && <span className="font-medium text-ink">{label}</span>}
          {description && <span className="block text-muted">{description}</span>}
        </label>
      )}
      <button
        id={swId}
        type="button"
        role="switch"
        aria-checked={Boolean(checked)}
        disabled={disabled}
        onClick={() => onChange?.(!checked)}
        className={cn(
          'relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition-colors disabled:opacity-50',
          checked ? 'bg-brand-600' : 'bg-gray-300 dark:bg-gray-600',
        )}
      >
        <span className={cn('inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform', checked ? 'translate-x-6' : 'translate-x-1')} />
      </button>
    </div>
  );
}
