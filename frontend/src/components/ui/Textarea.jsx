import { forwardRef, useId } from 'react';
import { cn } from '../../lib/cn';
import FormField from './FormField';

const Textarea = forwardRef(function Textarea(
  { label, error, hint, required, info, className, rows = 4, maxLength, value, showCount, id, ...rest },
  ref,
) {
  const autoId = useId();
  const taId = id || autoId;
  const count = typeof value === 'string' ? value.length : undefined;
  return (
    <FormField
      label={label}
      htmlFor={taId}
      error={error}
      hint={showCount && maxLength && count !== undefined ? `${hint ? `${hint} · ` : ''}${count}/${maxLength}` : hint}
      required={required}
      info={info}
      className={className}
    >
      <textarea
        ref={ref}
        id={taId}
        rows={rows}
        maxLength={maxLength}
        value={value}
        aria-invalid={Boolean(error) || undefined}
        className={cn('field-base min-h-[88px] resize-y', error && 'field-error')}
        {...rest}
      />
    </FormField>
  );
});

export default Textarea;
