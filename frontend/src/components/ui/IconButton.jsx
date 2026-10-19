import { forwardRef } from 'react';
import { cn } from '../../lib/cn';
import Spinner from './Spinner';

/** Square icon-only button; `label` is required for screen readers. */
const IconButton = forwardRef(function IconButton(
  { icon: Icon, label, variant = 'ghost', loading, className, size = 'md', type = 'button', ...rest },
  ref,
) {
  const variants = {
    ghost: 'text-muted hover:text-ink hover:bg-surface-2',
    primary: 'bg-brand-600 text-white hover:bg-brand-700',
    danger: 'text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10',
    outline: 'border border-line text-ink hover:bg-surface-2',
  };
  const sizes = { sm: 'h-9 w-9', md: 'h-11 w-11' };
  return (
    <button
      ref={ref}
      type={type}
      aria-label={label}
      title={label}
      disabled={loading || rest.disabled}
      className={cn('inline-flex shrink-0 items-center justify-center rounded-lg transition-colors disabled:opacity-50', variants[variant], sizes[size], className)}
      {...rest}
    >
      {loading ? <Spinner size="sm" className="text-current" /> : <Icon className="h-5 w-5" aria-hidden />}
    </button>
  );
});

export default IconButton;
