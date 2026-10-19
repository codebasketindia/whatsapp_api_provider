import { ChevronLeft, ChevronRight } from 'lucide-react';
import { formatNumber } from '../../lib/format';
import { cn } from '../../lib/cn';

/** Works with the API PageResponse (0-based page). */
export default function Pagination({ page = 0, totalPages = 0, totalElements, size, onPageChange, className }) {
  if (!totalPages || totalPages <= 1) {
    return totalElements !== undefined && totalElements > 0 ? (
      <p className={cn('px-1 py-3 text-sm text-muted', className)}>{formatNumber(totalElements)} result{totalElements === 1 ? '' : 's'}</p>
    ) : null;
  }
  const from = size ? page * size + 1 : null;
  const to = size && totalElements !== undefined ? Math.min((page + 1) * size, totalElements) : null;
  return (
    <nav className={cn('flex items-center justify-between gap-3 px-1 py-3', className)} aria-label="Pagination">
      <p className="text-sm text-muted">
        {from && to ? (
          <>
            {formatNumber(from)}–{formatNumber(to)} of {formatNumber(totalElements)}
          </>
        ) : (
          <>Page {page + 1} of {totalPages}</>
        )}
      </p>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          className="flex h-11 w-11 items-center justify-center rounded-lg border border-line text-ink hover:bg-surface-2 disabled:opacity-40"
          aria-label="Previous page"
        >
          <ChevronLeft className="h-5 w-5" />
        </button>
        <span className="min-w-[4rem] text-center text-sm text-ink">
          {page + 1} / {totalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="flex h-11 w-11 items-center justify-center rounded-lg border border-line text-ink hover:bg-surface-2 disabled:opacity-40"
          aria-label="Next page"
        >
          <ChevronRight className="h-5 w-5" />
        </button>
      </div>
    </nav>
  );
}
