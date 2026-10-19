import { useQuery } from '@tanstack/react-query';
import * as metaApi from '../../../../api/endpoints/meta';

/** Active WABAs as select options. ownerType: 'PLATFORM' | 'PARTNER' | '' */
export function useAdminWabaOptions(ownerType = '') {
  const q = useQuery({
    queryKey: ['admin', 'wabas', 'options', ownerType],
    queryFn: () => metaApi.listWabas({ ownerType, status: 'ACTIVE', size: 100 }),
    staleTime: 60_000,
  });
  const options = (q.data?.content || []).map((w) => ({
    value: w.id,
    label: `${w.name || 'WABA'} · ${w.ownerType === 'PLATFORM' ? 'our account' : w.partner?.businessName || 'partner'} (${w.wabaId})`,
  }));
  return { ...q, options };
}
