import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '../../lib/cn';

/**
 * trigger: ({ open, toggle }) => node, or a node (wrapped in a button).
 * items: [{ label, icon, onClick, to, danger, disabled, hidden, divider }]
 */
export default function DropdownMenu({ trigger, items = [], align = 'right', className, menuClassName, children }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    if (!open) return undefined;
    const onDoc = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('touchstart', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('touchstart', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);
  const toggle = () => setOpen((v) => !v);
  return (
    <div ref={ref} className={cn('relative inline-block', className)}>
      {typeof trigger === 'function' ? (
        trigger({ open, toggle })
      ) : (
        <button type="button" onClick={toggle} aria-haspopup="menu" aria-expanded={open}>
          {trigger}
        </button>
      )}
      {open && (
        <div
          role="menu"
          className={cn(
            'absolute z-40 mt-2 min-w-[200px] overflow-hidden rounded-xl border border-line bg-surface py-1 shadow-lg',
            align === 'right' ? 'right-0' : 'left-0',
            menuClassName,
          )}
          onClick={() => setOpen(false)}
        >
          {children}
          {items
            .filter((i) => !i.hidden)
            .map((item, idx) =>
              item.divider ? (
                <div key={`d-${idx}`} className="my-1 border-t border-line" />
              ) : item.to ? (
                <Link
                  key={item.label}
                  to={item.to}
                  role="menuitem"
                  className={cn('flex min-h-tap items-center gap-3 px-4 text-sm hover:bg-surface-2', item.danger ? 'text-red-600' : 'text-ink')}
                >
                  {item.icon && <item.icon className="h-4 w-4" aria-hidden />}
                  {item.label}
                </Link>
              ) : (
                <button
                  key={item.label}
                  type="button"
                  role="menuitem"
                  disabled={item.disabled}
                  onClick={item.onClick}
                  className={cn(
                    'flex min-h-tap w-full items-center gap-3 px-4 text-left text-sm hover:bg-surface-2 disabled:opacity-50',
                    item.danger ? 'text-red-600' : 'text-ink',
                  )}
                >
                  {item.icon && <item.icon className="h-4 w-4" aria-hidden />}
                  {item.label}
                </button>
              ),
            )}
        </div>
      )}
    </div>
  );
}
