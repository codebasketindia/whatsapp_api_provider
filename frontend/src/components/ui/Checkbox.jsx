import { forwardRef, useId } from 'react';
import { cn } from '../../lib/cn';

const Checkbox = forwardRef(function Checkbox({ label, description, error, className, id, ...rest }, ref) {
  const autoId = useId();
  const cbId = id || autoId;
  return (
    <div className={className}>
      <label htmlFor={cbId} className="flex min-h-tap cursor-pointer items-start gap-3 py-2">
        <input
          ref={ref}
          id={cbId}
          type="checkbox"
          className="mt-0.5 h-5 w-5 shrink-0 cursor-pointer rounded border-line text-brand-600 accent-brand-600 focus:ring-brand-500"
          {...rest}
        />
        <span className="text-sm">
          <span className="font-medium text-ink">{label}</span>
          {description && <span className="block text-muted">{description}</span>}
        </span>
      </label>
      {error && <p className={cn('text-xs text-red-600')}>{typeof error === 'string' ? error : error.message}</p>}
    </div>
  );
});

export default Checkbox;
