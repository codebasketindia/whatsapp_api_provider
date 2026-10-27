import { useQuery } from '@tanstack/react-query';
import * as messagesApi from '../../../api/endpoints/messages';

/**
 * Is the 24-hour customer service window open for this sender + number?
 * Uses the inbox conversation list (IN1) filtered by number.
 */
export function useWindowStatus(senderId, waId) {
  const enabled = Boolean(senderId && waId && waId.length >= 8);
  const q = useQuery({
    queryKey: ['partner', 'inbox', 'window', senderId, waId],
    queryFn: () => messagesApi.listConversations({ senderId, search: waId, size: 5 }),
    enabled,
    staleTime: 30_000,
  });
  const rows = q.data?.content || [];
  const conv = rows.find((r) => String(r.waId) === String(waId));
  return {
    loading: enabled && q.isLoading,
    open: Boolean(conv?.windowOpen),
    expiresAt: conv?.windowExpiresAt || null,
    known: q.isSuccess,
    conversation: conv || null,
  };
}
