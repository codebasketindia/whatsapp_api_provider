import { useMemo } from 'react';
import { useMySenders } from '../../common/useSenders';

/** Distinct WhatsApp Business Accounts of the partner's senders, for template forms. */
export function useWabaOptions() {
  const q = useMySenders();
  const options = useMemo(() => {
    const map = new Map();
    (q.data || []).forEach((s) => {
      if (!s.waba?.id || map.has(s.waba.id)) return;
      const own = s.senderType === 'PARTNER_OWN_SENDER';
      map.set(s.waba.id, {
        value: s.waba.id,
        label: `${s.waba.name || s.verifiedName || 'WhatsApp account'} (${s.displayPhoneNumber}${own ? '' : ' · reviewed by us first'})`,
        own,
      });
    });
    return Array.from(map.values());
  }, [q.data]);
  return { ...q, options };
}
