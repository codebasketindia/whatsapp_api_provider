import { forwardRef, useId } from 'react';
import { cn } from '../../lib/cn';
import FormField from './FormField';

/** Text input with label, hint, error and optional prefix/suffix (e.g. ₹). */
const Input = forwardRef(function Input(
  { label, error, hint, required, info, prefix, suffix, className, inputClassName, id, ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id || autoId;
  const hasError = Boolean(error);
  return (
    <FormField label={label} htmlFor={inputId} error={error} hint={hint} required={required} info={info} className={className}>
      <div className="relative flex items-center">
        {prefix && <span className="pointer-events-none absolute left-3 text-sm text-muted">{prefix}</span>}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={hasError || undefined}
          aria-describedby={hasError ? `${inputId}-error` : undefined}
          className={cn('field-base', hasError && 'field-error', prefix && 'pl-8', suffix && 'pr-10', inputClassName)}
          {...rest}
        />
        {suffix && <span className="absolute right-3 text-sm text-muted">{suffix}</span>}
      </div>
    </FormField>
  );
});

export default Input;
