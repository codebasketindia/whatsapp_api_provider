import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useIsDesktop } from '../../hooks/useMediaQuery';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import ErrorState from './ErrorState';
import Pagination from './Pagination';

/**
 * Responsive table: real <table> on >= 768px, stacked cards on phones.
 *
 * columns: [{
 *   key, header, render?(row) => node, accessor?(row) => value,
 *   sortable?, sortKey?, align? ('left'|'right'|'center'), className?,
 *   hideOnMobile?, mobileLabel? (defaults to header), primary? (shown as card title on mobile)
 * }]
 * page: PageResponse { content, page, size, totalElements, totalPages } — or pass `rows`.
 * sort: "field,asc|desc", onSortChange(newSort)
 */
export default function DataTable({
  columns,
  rows,
  page,
  loading,
  error,
  onRetry,
  onRowClick,
  rowKey = (r) => r.id ?? r.uid,
  empty,
  sort,
  onSortChange,
  onPageChange,
  mobileActions,
  className,
}) {
  const isDesktop = useIsDesktop();
  const data = rows || page?.content || [];
  const [sortField, sortDir] = (sort || '').split(',');

  const cell = (col, row) => (col.render ? col.render(row) : col.accessor ? col.accessor(row) : row[col.key]);

  const toggleSort = (col) => {
    if (!onSortChange) return;
    const field = col.sortKey || col.key;
    const nextDir = sortField === field && sortDir === 'asc' ? 'desc' : 'asc';
    onSortChange(`${field},${nextDir}`);
  };

  if (error) {
    return <ErrorState error={error} onRetry={onRetry} className={className} />;
  }

  const showEmpty = !loading && data.length === 0;
  const alignCls = (a) => (a === 'right' ? 'text-right' : a === 'center' ? 'text-center' : 'text-left');

  return (
    <div className={className}>
      {isDesktop ? (
        <div className="relative overflow-x-auto rounded-xl border border-line bg-surface">
          <table className="min-w-full divide-y divide-line text-sm">
            <thead className="bg-surface-2">
              <tr>
                {columns.map((col) => {
                  const field = col.sortKey || col.key;
                  const active = sortField === field;
                  return (
                    <th
                      key={col.key}
                      scope="col"
                      aria-sort={active ? (sortDir === 'asc' ? 'ascending' : 'descending') : undefined}
                      className={cn('whitespace-nowrap px-4 py-3 font-medium text-muted', alignCls(col.align), col.headerClassName)}
                    >
                      {col.sortable && onSortChange ? (
                        <button type="button" onClick={() => toggleSort(col)} className="inline-flex items-center gap-1 hover:text-ink">
                          {col.header}
                          {active ? (
                            sortDir === 'asc' ? <ArrowUp className="h-3.5 w-3.5" /> : <ArrowDown className="h-3.5 w-3.5" />
                          ) : (
                            <ArrowUpDown className="h-3.5 w-3.5 opacity-50" />
                          )}
                        </button>
                      ) : (
                        col.header
                      )}
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {loading
                ? Array.from({ length: 6 }).map((_, i) => (
                    <tr key={`sk-${i}`}>
                      {columns.map((col) => (
                        <td key={col.key} className="px-4 py-3">
                          <Skeleton className="h-4 w-full max-w-[160px]" />
                        </td>
                      ))}
                    </tr>
                  ))
                : data.map((row) => (
                    <tr
                      key={rowKey(row)}
                      onClick={onRowClick ? () => onRowClick(row) : undefined}
                      className={cn(onRowClick && 'cursor-pointer hover:bg-surface-2')}
                    >
                      {columns.map((col) => (
                        <td key={col.key} className={cn('px-4 py-3 align-middle text-ink', alignCls(col.align), col.className)}>
                          {cell(col, row)}
                        </td>
                      ))}
                    </tr>
                  ))}
            </tbody>
          </table>
          {showEmpty && (empty || <EmptyState title="Nothing here yet" compact />)}
        </div>
      ) : (
        <div className="space-y-3">
          {loading &&
            Array.from({ length: 4 }).map((_, i) => (
              <div key={`msk-${i}`} className="card space-y-2 p-4">
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-2/3" />
              </div>
            ))}
          {!loading &&
            data.map((row) => {
              const primary = columns.find((c) => c.primary) || columns[0];
              const rest = columns.filter((c) => c !== primary && !c.hideOnMobile);
              return (
                <div
                  key={rowKey(row)}
                  role={onRowClick ? 'button' : undefined}
                  tabIndex={onRowClick ? 0 : undefined}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  onKeyDown={
                    onRowClick
                      ? (e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            onRowClick(row);
                          }
                        }
                      : undefined
                  }
                  className={cn('card block w-full p-4 text-left', onRowClick && 'cursor-pointer active:bg-surface-2')}
                >
                  <div className="mb-2 font-medium text-ink">{cell(primary, row)}</div>
                  <dl className="space-y-1.5">
                    {rest.map((col) => (
                      <div key={col.key} className="flex items-start justify-between gap-3 text-sm">
                        <dt className="shrink-0 text-muted">{col.mobileLabel || col.header}</dt>
                        <dd className="min-w-0 break-words text-right text-ink">{cell(col, row)}</dd>
                      </div>
                    ))}
                  </dl>
                  {mobileActions && <div className="mt-3 flex flex-wrap gap-2" onClick={(e) => e.stopPropagation()}>{mobileActions(row)}</div>}
                </div>
              );
            })}
          {showEmpty && <div className="card">{empty || <EmptyState title="Nothing here yet" compact />}</div>}
        </div>
      )}
      {page && onPageChange && !loading && (
        <Pagination page={page.page} totalPages={page.totalPages} totalElements={page.totalElements} size={page.size} onPageChange={onPageChange} />
      )}
    </div>
  );
}
