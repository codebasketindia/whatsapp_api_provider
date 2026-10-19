import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Bell, CheckCheck } from 'lucide-react';
import * as meApi from '../../api/endpoints/me';
import { getErrorMessage } from '../../api/axios';
import { useUrlState } from '../../hooks/useUrlState';
import { cn } from '../../lib/cn';
import { formatDateTime, formatRelative } from '../../lib/format';
import { Button, EmptyState, ErrorState, PageHeader, Pagination, Skeleton, Tabs } from '../../components/ui';

const DOT = { INFO: 'bg-blue-500', SUCCESS: 'bg-green-500', WARNING: 'bg-amber-500', ERROR: 'bg-red-500' };

export default function NotificationsPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [state, update] = useUrlState({ page: 0, filter: 'all' });
  const unreadOnly = state.filter === 'unread';
  const q = useQuery({
    queryKey: ['me', 'notifications', 'list', state.page, unreadOnly],
    queryFn: () => meApi.listNotifications({ page: state.page, size: 20, unreadOnly: unreadOnly || undefined }),
    placeholderData: (prev) => prev,
  });
  const invalidate = () => qc.invalidateQueries({ queryKey: ['me', 'notifications'] });
  const readOne = useMutation({ mutationFn: meApi.markNotificationRead, onSuccess: invalidate });
  const readAll = useMutation({
    mutationFn: meApi.markAllNotificationsRead,
    onSuccess: (r) => {
      toast.success(r?.updated ? `${r.updated} marked as read` : 'All caught up');
      invalidate();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const items = q.data?.content || [];

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle="Alerts about your wallet, numbers, templates and campaigns."
        actions={
          <Button variant="secondary" leftIcon={CheckCheck} onClick={() => readAll.mutate()} loading={readAll.isPending}>
            Mark all as read
          </Button>
        }
      />
      <Tabs
        className="mb-4"
        value={state.filter}
        onChange={(v) => update({ filter: v === 'all' ? '' : v })}
        tabs={[
          { value: 'all', label: 'All' },
          { value: 'unread', label: 'Unread' },
        ]}
      />
      {q.isError ? (
        <ErrorState error={q.error} onRetry={q.refetch} />
      ) : (
        <div className="card divide-y divide-line">
          {q.isLoading &&
            Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="space-y-2 p-4">
                <Skeleton className="h-4 w-1/3" />
                <Skeleton className="h-4 w-2/3" />
              </div>
            ))}
          {!q.isLoading && items.length === 0 && (
            <EmptyState icon={Bell} title={unreadOnly ? 'No unread notifications' : 'No notifications yet'} text="We will let you know when something needs your attention." />
          )}
          {items.map((n) => (
            <button
              key={n.id}
              type="button"
              onClick={() => {
                if (!n.isRead) readOne.mutate(n.id);
                if (n.link) navigate(n.link);
              }}
              className={cn('flex w-full gap-3 p-4 text-left hover:bg-surface-2', !n.isRead && 'bg-brand-50/50 dark:bg-brand-500/5')}
            >
              <span className={cn('mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full', n.isRead ? 'bg-line' : DOT[n.severity] || 'bg-blue-500')} />
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-baseline justify-between gap-x-3">
                  <span className={cn('text-sm text-ink', !n.isRead && 'font-semibold')}>{n.title}</span>
                  <span className="text-xs text-muted" title={formatDateTime(n.createdAt)}>
                    {formatRelative(n.createdAt)}
                  </span>
                </span>
                {n.body && <span className="mt-1 block text-sm text-muted">{n.body}</span>}
              </span>
            </button>
          ))}
        </div>
      )}
      {q.data && (
        <Pagination page={q.data.page} totalPages={q.data.totalPages} totalElements={q.data.totalElements} size={q.data.size} onPageChange={(p) => update({ page: p })} />
      )}
    </>
  );
}
