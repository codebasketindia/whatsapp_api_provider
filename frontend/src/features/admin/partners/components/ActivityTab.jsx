import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import * as partnersApi from '../../../../api/endpoints/partners';
import AuditTable from '../../audit/components/AuditTable';

export default function ActivityTab({ partner }) {
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ['admin', 'partners', 'activity', partner.id, page],
    queryFn: () => partnersApi.getPartnerActivity(partner.id, { page, size: 20 }),
    placeholderData: (p) => p,
  });
  return <AuditTable query={q} onPageChange={setPage} />;
}
