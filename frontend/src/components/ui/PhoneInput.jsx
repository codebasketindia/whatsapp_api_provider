import { forwardRef, useId } from 'react';
import { cn } from '../../lib/cn';
import FormField from './FormField';

/**
 * Phone number with country code. Value is digits only incl. country code (e.g. "919876543210"),
 * matching the API (E.164 without "+").
 */
const PhoneInput = forwardRef(function PhoneInput(
  { label, error, hint, required, value = '', onChange, countryCode = '91', className, id, placeholder = '98765 43210', ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id || autoId;
  const digits = String(value || '').replace(/\D/g, '');
  const local = digits.startsWith(countryCode) ? digits.slice(countryCode.length) : digits;
  return (
    <FormField label={label} htmlFor={inputId} error={error} hint={hint} required={required} className={className}>
      <div className={cn('flex items-stretch overflow-hidden rounded-lg border border-line bg-surface focus-within:border-brand-500 focus-within:ring-2 focus-within:ring-brand-500/30', error && 'border-red-500')}>
        <span className="flex items-center border-r border-line bg-surface-2 px-3 text-sm text-muted">+{countryCode}</span>
        <input
          ref={ref}
          id={inputId}
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          placeholder={placeholder}
          value={local}
          aria-invalid={Boolean(error) || undefined}
          onChange={(e) => {
            const d = e.target.value.replace(/\D/g, '').slice(0, 15);
            onChange?.(d ? `${countryCode}${d}` : '');
          }}
          className="min-h-tap w-full bg-transparent px-3 py-2 text-sm text-ink placeholder:text-muted focus:outline-none"
          {...rest}
        />
      </div>
    </FormField>
  );
});

export default PhoneInput;

/** Indian mobile validator for Zod refinements: 91 + 10 digits starting 6-9. */
export const isIndianMobile = (v) => /^91[6-9]\d{9}$/.test(String(v || ''));
/** Generic E.164 digits (8–15). */
export const isE164Digits = (v) => /^[1-9]\d{7,14}$/.test(String(v || ''));
