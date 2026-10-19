import { forwardRef } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '../../lib/cn';
import Spinner from './Spinner';

const VARIANTS = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 active:bg-brand-800 shadow-sm',
  secondary: 'bg-surface-2 text-ink hover:bg-line/60 border border-line',
  outline: 'border border-brand-600 text-brand-700 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/10',
  ghost: 'text-ink hover:bg-surface-2',
  danger: 'bg-red-600 text-white hover:bg-red-700 active:bg-red-800 shadow-sm',
  'danger-outline': 'border border-red-500 text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10',
  link: 'text-brand-700 hover:underline dark:text-brand-300 px-0 min-h-0',
};

const SIZES = {
  sm: 'min-h-[36px] px-3 text-sm gap-1.5',
  md: 'min-h-tap px-4 text-sm gap-2',
  lg: 'min-h-[52px] px-6 text-base gap-2',
};

/**
 * Button with variants, sizes and loading state. Pass `to` to render a router Link,
 * `href` for an external link.
 */
const Button = forwardRef(function Button(
  { variant = 'primary', size = 'md', loading = false, disabled, leftIcon: Left, rightIcon: Right, fullWidth, className, children, to, href, type = 'button', ...rest },
  ref,
) {
  const classes = cn(
    'inline-flex select-none items-center justify-center rounded-lg font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60',
    VARIANTS[variant],
    SIZES[size],
    fullWidth && 'w-full',
    className,
  );
  const content = (
    <>
      {loading ? <Spinner size="sm" className="text-current" /> : Left ? <Left className="h-4 w-4 shrink-0" aria-hidden /> : null}
      {children}
      {Right && !loading ? <Right className="h-4 w-4 shrink-0" aria-hidden /> : null}
    </>
  );
  if (to && !disabled) {
    return (
      <Link ref={ref} to={to} className={classes} {...rest}>
        {content}
      </Link>
    );
  }
  if (href && !disabled) {
    return (
      <a ref={ref} href={href} className={classes} target="_blank" rel="noreferrer noopener" {...rest}>
        {content}
      </a>
    );
  }
  return (
    <button ref={ref} type={type} disabled={disabled || loading} aria-busy={loading || undefined} className={classes} {...rest}>
      {content}
    </button>
  );
});

export default Button;
