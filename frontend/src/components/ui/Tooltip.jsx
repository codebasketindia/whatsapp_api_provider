import { useId, useState } from 'react';
import { cn } from '../../lib/cn';

/** Hover/focus/tap tooltip. Tap toggles on touch devices. */
export default function Tooltip({ content, children, className, side = 'top' }) {
  const [open, setOpen] = useState(false);
  const id = useId();
  if (!content) return children;
  return (
    <span
      className={cn('relative inline-flex', className)}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
    >
      <span aria-describedby={open ? id : undefined} onClick={() => setOpen((v) => !v)}>
        {children}
      </span>
      {open && (
        <span
          role="tooltip"
          id={id}
          className={cn(
            'absolute left-1/2 z-50 w-max max-w-[260px] -translate-x-1/2 rounded-lg bg-gray-900 px-3 py-2 text-xs font-normal leading-snug text-white shadow-lg dark:bg-gray-700',
            side === 'top' ? 'bottom-full mb-2' : 'top-full mt-2',
          )}
        >
          {content}
        </span>
      )}
    </span>
  );
}
