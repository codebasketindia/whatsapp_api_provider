import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useIsDesktop } from '../../hooks/useMediaQuery';

function useLockBody(open) {
  useEffect(() => {
    if (!open) return undefined;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);
}

function useEscape(open, onClose) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);
}

function useFocusFirst(open, ref) {
  useEffect(() => {
    if (!open || !ref.current) return;
    const el = ref.current.querySelector('input, select, textarea, button:not([data-close]), [tabindex]:not([tabindex="-1"])');
    (el || ref.current).focus?.();
  }, [open, ref]);
}

/**
 * Centered dialog on desktop; slides up as a bottom sheet on phones.
 * size: sm | md | lg | xl. `footer` renders a sticky action row.
 */
export default function Modal({ open, onClose, title, description, children, footer, size = 'md', dismissible = true, className }) {
  const panelRef = useRef(null);
  const isDesktop = useIsDesktop();
  useLockBody(open);
  useEscape(open && dismissible, onClose);
  useFocusFirst(open, panelRef);
  if (!open) return null;
  const widths = { sm: 'md:max-w-sm', md: 'md:max-w-lg', lg: 'md:max-w-2xl', xl: 'md:max-w-4xl' };
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center md:items-center md:p-4" role="presentation">
      <div className="absolute inset-0 animate-fade-in bg-black/50" onClick={dismissible ? onClose : undefined} aria-hidden />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === 'string' ? title : undefined}
        tabIndex={-1}
        className={cn(
          'relative flex max-h-[92dvh] w-full flex-col bg-surface shadow-xl outline-none',
          isDesktop ? 'animate-fade-in rounded-2xl' : 'animate-slide-up rounded-t-2xl safe-bottom',
          widths[size],
          className,
        )}
      >
        {!isDesktop && <div className="mx-auto mt-2 h-1.5 w-10 rounded-full bg-line" aria-hidden />}
        {(title || dismissible) && (
          <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
            <div>
              {title && <h2 className="text-lg font-semibold text-ink">{title}</h2>}
              {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
            </div>
            {dismissible && (
              <button
                type="button"
                data-close
                onClick={onClose}
                className="-mr-2 -mt-1 flex h-11 w-11 items-center justify-center rounded-lg text-muted hover:bg-surface-2 hover:text-ink"
                aria-label="Close"
              >
                <X className="h-5 w-5" />
              </button>
            )}
          </div>
        )}
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex flex-col-reverse gap-2 border-t border-line px-5 py-3 sm:flex-row sm:justify-end">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

export { useLockBody, useEscape };
