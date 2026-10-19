import { cn } from '../../lib/cn';
import InfoTip from './InfoTip';

/** Label + control + hint/error wrapper. Works with any input; `error` may be an RHF FieldError. */
export default function FormField({ label, htmlFor, error, hint, required, info, className, children }) {
  const message = typeof error === 'string' ? error : error?.message;
  return (
    <div className={cn('w-full', className)}>
      {label && (
        <label htmlFor={htmlFor} className="label-base flex items-center gap-1">
          <span>
            {label}
            {required && <span className="ml-0.5 text-red-600" aria-hidden>*</span>}
          </span>
          {info && <InfoTip text={info} />}
        </label>
      )}
      {children}
      {message ? (
        <p id={htmlFor ? `${htmlFor}-error` : undefined} role="alert" className="mt-1 text-xs text-red-600 dark:text-red-400">
          {message}
        </p>
      ) : hint ? (
        <p className="mt-1 text-xs text-muted">{hint}</p>
      ) : null}
    </div>
  );
}
