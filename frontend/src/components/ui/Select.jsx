import { forwardRef, useId } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '../../lib/cn';
import FormField from './FormField';

/**
 * Native select (best on mobile). options: [{ value, label, disabled }] or strings.
 * `placeholder` adds an empty first option.
 */
const Select = forwardRef(function Select(
  { label, error, hint, required, info, options = [], placeholder, className, selectClassName, id, ...rest },
  ref,
) {
  const autoId = useId();
  const selectId = id || autoId;
  return (
    <FormField label={label} htmlFor={selectId} error={error} hint={hint} required={required} info={info} className={className}>
      <div className="relative">
        <select
          ref={ref}
          id={selectId}
          aria-invalid={Boolean(error) || undefined}
          className={cn('field-base appearance-none pr-9', error && 'field-error', selectClassName)}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((o) => {
            const opt = typeof o === 'object' ? o : { value: o, label: o };
            return (
              <option key={opt.value} value={opt.value} disabled={opt.disabled}>
                {opt.label}
              </option>
            );
          })}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
      </div>
    </FormField>
  );
});

export default Select;
