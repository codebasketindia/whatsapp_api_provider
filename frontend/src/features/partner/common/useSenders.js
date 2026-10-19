import { useQuery } from '@tanstack/react-query';
import * as sendersApi from '../../../api/endpoints/senders';

/** All senders of the logged-in partner (assigned platform numbers + own numbers). */
export function useMySenders(options = {}) {
  return useQuery({ queryKey: ['partner', 'senders'], queryFn: sendersApi.listMySenders, staleTime: 30_000, ...options });
}

/** Senders that can send right now. */
export const usableSenders = (list = []) => list.filter((s) => s.status === 'CONNECTED' && !s.isPaused);

export const senderLabel = (s) => `${s.verifiedName || 'Unnamed'} · ${s.displayPhoneNumber || s.phoneNumberId}`;

export const defaultSenderId = (list = []) => {
  const usable = usableSenders(list);
  return (usable.find((s) => s.isDefault) || usable[0])?.id ?? '';
};
