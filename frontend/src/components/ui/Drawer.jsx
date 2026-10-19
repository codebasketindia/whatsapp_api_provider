import { useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useIsDesktop } from '../../hooks/useMediaQuery';
import { useEscape, useLockBody } from './Modal';

/**
 * Side drawer on desktop (side = right|left), bottom sheet on phones.
 * Use for details panels and quick forms.
 */
export default function Drawer({ open, onClose, title, description, children, footer, side = 'right', width = 'md:w-[440px]', className }) {
  const isDesktop = useIsDesktop();
  const panelRef = useRef(null);
  useLockBody(open);
  useEscape(open, onClose);
  if (!open) return null;
  const sheet = !isDesktop;
  return createPortal(
    <div className="fixed inset-0 z-50" role="presentation">
      <div className="absolute inset-0 animate-fade-in bg-black/50" onClick={onClose} aria-hidden />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === 'string' ? title : undefined}
        className={cn(
          'absolute flex flex-col bg-surface shadow-xl',
          sheet
            ? 'inset-x-0 bottom-0 max-h-[92dvh] animate-slide-up rounded-t-2xl safe-bottom'
            : cn('inset-y-0 h-full', width, side === 'right' ? 'right-0 animate-slide-in-right' : 'left-0 animate-slide-in-left'),
          className,
        )}
      >
        {sheet && <div className="mx-auto mt-2 h-1.5 w-10 rounded-full bg-line" aria-hidden />}
        <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
          <div className="min-w-0">
            {title && <h2 className="truncate text-lg font-semibold text-ink">{title}</h2>}
            {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="-mr-2 -mt-1 flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-muted hover:bg-surface-2 hover:text-ink"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex flex-col-reverse gap-2 border-t border-line px-5 py-3 sm:flex-row sm:justify-end">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

/** Alias: Drawer already renders as a bottom sheet on phones. */
export const BottomSheet = Drawer;
