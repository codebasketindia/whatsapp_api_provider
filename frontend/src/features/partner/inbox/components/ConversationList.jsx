import { useQuery } from '@tanstack/react-query';
import { NavLink } from 'react-router-dom';
import { MessagesSquare } from 'lucide-react';
import * as messagesApi from '../../../../api/endpoints/messages';
import { Avatar, EmptyState, ErrorState, SearchInput, Select, Skeleton, Switch } from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { cn } from '../../../../lib/cn';
import { formatPhone, formatRelative } from '../../../../lib/format';
import { senderLabel, useMySenders } from '../../common/useSenders';

/** Conversation list (IN1) with search, sender filter and unread toggle. */
export default function ConversationList({ live, className }) {
  const [f, update] = useUrlState({ senderId: '', search: '', unreadOnly: '' });
  const senders = useMySenders();
  const params = { senderId: f.senderId, search: f.search, unreadOnly: f.unreadOnly === 'true' ? true : undefined, size: 50 };
  const q = useQuery({
    queryKey: ['partner', 'inbox', 'conversations', params],
    queryFn: () => messagesApi.listConversations(params),
    refetchInterval: live ? 60_000 : 10_000,
    placeholderData: (p) => p,
  });
  const rows = q.data?.content || [];
  return (
    <div className={cn('flex min-h-0 flex-col', className)}>
      <div className="space-y-2 border-b border-line p-3">
        <SearchInput value={f.search} onSearch={(v) => update({ search: v })} placeholder="Search name or number" />
        <div className="flex items-center gap-2">
          {(senders.data || []).length > 1 && (
            <Select
              aria-label="Number"
              className="flex-1"
              value={f.senderId}
              onChange={(e) => update({ senderId: e.target.value })}
              options={(senders.data || []).map((s) => ({ value: s.id, label: senderLabel(s) }))}
              placeholder="All numbers"
            />
          )}
          <Switch checked={f.unreadOnly === 'true'} onChange={(v) => update({ unreadOnly: v ? 'true' : '' })} label="Unread" className="shrink-0" />
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {q.isError ? (
          <ErrorState error={q.error} onRetry={q.refetch} />
        ) : q.isLoading ? (
          <div className="space-y-3 p-3">{[1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-14 w-full" />)}</div>
        ) : !rows.length ? (
          <EmptyState compact icon={MessagesSquare} title="No conversations" text="When customers message your WhatsApp number, chats appear here." />
        ) : (
          <ul className="divide-y divide-line">
            {rows.map((c) => (
              <li key={`${c.senderId}-${c.waId}`}>
                <NavLink
                  to={`/partner/inbox/${c.senderId}/${c.waId}`}
                  className={({ isActive }) => cn('flex min-h-[64px] items-center gap-3 px-3 py-2 hover:bg-surface-2', isActive && 'bg-brand-50 dark:bg-brand-500/10')}
                >
                  <Avatar name={c.contact?.name || c.waId} />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center justify-between gap-2">
                      <span className={cn('truncate text-sm', c.unreadCount ? 'font-semibold text-ink' : 'font-medium text-ink')}>{c.contact?.name || formatPhone(c.waId)}</span>
                      <span className="shrink-0 text-xs text-muted">{formatRelative(c.lastMessageAt)}</span>
                    </span>
                    <span className="flex items-center justify-between gap-2">
                      <span className="truncate text-xs text-muted">{c.lastMessagePreview || '…'}</span>
                      {c.unreadCount > 0 && <span className="flex h-5 min-w-[20px] items-center justify-center rounded-full bg-brand-600 px-1.5 text-[11px] font-semibold text-white">{c.unreadCount}</span>}
                    </span>
                    {c.windowOpen && <span className="text-[11px] font-medium text-green-700 dark:text-green-400">Can reply freely</span>}
                  </span>
                </NavLink>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
