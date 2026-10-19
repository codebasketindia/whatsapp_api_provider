import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import * as metaApi from '../../../../api/endpoints/meta';
import { Banner } from '../../../../components/ui';

/** M18 alerts (low quality, restrictions, invalid tokens) shown above sender lists. */
export default function SenderHealthAlerts() {
  const q = useQuery({ queryKey: ['admin', 'meta-health'], queryFn: metaApi.getMetaHealth, staleTime: 60_000 });
  const alerts = q.data?.alerts || [];
  if (!alerts.length) return null;
  return (
    <div className="mb-4 space-y-2">
      {alerts.slice(0, 5).map((a, i) => {
        const text = typeof a === 'string' ? a : a.message || a.title;
        const link = typeof a === 'object' && (a.senderId ? `/admin/whatsapp/senders/${a.senderId}` : a.wabaId ? `/admin/whatsapp/accounts/${a.wabaId}` : a.link);
        return (
          <Banner key={i} variant={a.severity === 'ERROR' || a.severity === 'RED' ? 'error' : 'warning'} action={link && <Link className="font-medium underline" to={link}>Open</Link>}>
            {text}
          </Banner>
        );
      })}
    </div>
  );
}
