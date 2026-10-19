import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck } from 'lucide-react';
import * as meApi from '../../api/endpoints/me';
import { cn } from '../../lib/cn';
import { formatRelative } from '../../lib/format';
import Spinner from '../ui/Spinner';

const SEVERITY_DOT = {
  INFO: 'bg-blue-500',
  SUCCESS: 'bg-green-500',
  WARNING: 'bg-amber-500',
  ERROR: 'bg-red-500',
};

/** Bell with unread count (polls every 60 s) and a dropdown of recent notifications. */
export default function NotificationBell({ allPath }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const qc = useQueryClient();
  const navigate = useNavigate();

  const countQ = useQuery({
    queryKey: ['me', 'notifications', 'unread-count'],
    queryFn: meApi.getUnreadNotificationCount,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  });
  const listQ = useQuery({
    queryKey: ['me', 'notifications', 'recent'],
    queryFn: () => meApi.listNotifications({ page: 0, size: 8 }),
    enabled: open,
  });
  const invalidate = () => qc.invalidateQueries({ queryKey: ['me', 'notifications'] });
  const readOne = useMutation({ mutationFn: meApi.markNotificationRead, onSuccess: invalidate });
  const readAll = useMutation({ mutationFn: meApi.markAllNotificationsRead, onSuccess: invalidate });

  useEffect(() => {
    if (!open) return undefined;
    const onDoc = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  const count = countQ.data?.count || 0;
  const items = listQ.data?.content || [];

  const openItem = (n) => {
    if (!n.isRead) readOne.mutate(n.id);
    setOpen(false);
    if (n.link) navigate(n.link);
  };

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="relative flex h-11 w-11 items-center justify-center rounded-lg text-muted hover:bg-surface-2 hover:text-ink"
        aria-label={count ? `Notifications, ${count} unread` : 'Notifications'}
        aria-expanded={open}
      >
        <Bell className="h-5 w-5" />
        {count > 0 && (
          <span className="absolute right-1.5 top-1.5 flex h-5 min-w-[20px] items-center justify-center rounded-full bg-red-600 px-1 text-[11px] font-semibold text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>
      {open && (
        <div className="fixed inset-x-2 top-16 z-40 overflow-hidden rounded-xl border border-line bg-surface shadow-xl sm:absolute sm:inset-x-auto sm:right-0 sm:top-auto sm:mt-2 sm:w-96">
          <div className="flex items-center justify-between border-b border-line px-4 py-2">
            <p className="font-semibold text-ink">Notifications</p>
            <button
              type="button"
              disabled={!count || readAll.isPending}
              onClick={() => readAll.mutate()}
              className="inline-flex min-h-[36px] items-center gap-1 rounded-md px-2 text-sm text-brand-700 hover:bg-surface-2 disabled:opacity-40 dark:text-brand-300"
            >
              <CheckCheck className="h-4 w-4" /> Mark all read
            </button>
          </div>
          <div className="max-h-[60vh] overflow-y-auto">
            {listQ.isLoading && (
              <div className="flex justify-center py-8">
                <Spinner />
              </div>
            )}
            {listQ.isError && <p className="px-4 py-6 text-center text-sm text-muted">Could not load notifications.</p>}
            {!listQ.isLoading && !listQ.isError && items.length === 0 && (
              <p className="px-4 py-8 text-center text-sm text-muted">You are all caught up.</p>
            )}
            {items.map((n) => (
              <button
                key={n.id}
                type="button"
                onClick={() => openItem(n)}
                className={cn('flex w-full gap-3 border-b border-line px-4 py-3 text-left last:border-0 hover:bg-surface-2', !n.isRead && 'bg-brand-50/60 dark:bg-brand-500/5')}
              >
                <span className={cn('mt-1.5 h-2 w-2 shrink-0 rounded-full', n.isRead ? 'bg-transparent' : SEVERITY_DOT[n.severity] || 'bg-blue-500')} />
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-medium text-ink">{n.title}</span>
                  {n.body && <span className="mt-0.5 line-clamp-2 block text-sm text-muted">{n.body}</span>}
                  <span className="mt-1 block text-xs text-muted">{formatRelative(n.createdAt)}</span>
                </span>
              </button>
            ))}
          </div>
          <Link to={allPath} onClick={() => setOpen(false)} className="block border-t border-line px-4 py-3 text-center text-sm font-medium text-brand-700 hover:bg-surface-2 dark:text-brand-300">
            See all notifications
          </Link>
        </div>
      )}
    </div>
  );
}
