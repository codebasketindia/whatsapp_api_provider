import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ChevronRight, Lock, Plus, Smartphone, Star } from 'lucide-react';
import * as sendersApi from '../../../../api/endpoints/senders';
import { Alert, Badge, Button, EmptyState, ErrorState, InfoTip, PageHeader, Skeleton, StatusBadge } from '../../../../components/ui';
import QualityBadge from '../../../../components/whatsapp/QualityBadge';
import { useMySenders } from '../../common/useSenders';

export default function MySendersPage() {
  const q = useMySenders();
  const cfg = useQuery({ queryKey: ['partner', 'embedded-signup', 'config'], queryFn: sendersApi.getEmbeddedSignupConfig, retry: false });
  const allowed = cfg.isSuccess && cfg.data?.allowed;
  const reason = cfg.isError
    ? 'Connecting your own number is not available right now.'
    : cfg.isSuccess && !cfg.data?.allowed
      ? 'Connecting your own number is not turned on for your account. Contact support to enable it.'
      : null;

  return (
    <>
      <PageHeader
        title="My numbers"
        subtitle="WhatsApp numbers you can send messages from"
        actions={
          <Button to="/partner/senders/connect" leftIcon={Plus} disabled={!allowed}>
            Connect WhatsApp number
          </Button>
        }
      />
      {reason && (
        <Alert variant="info" className="mb-4" title="Own number">
          {reason}
        </Alert>
      )}
      {q.isError ? (
        <ErrorState error={q.error} onRetry={q.refetch} />
      ) : q.isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => <Skeleton key={i} className="h-40 w-full rounded-xl" />)}
        </div>
      ) : !q.data?.length ? (
        <div className="card">
          <EmptyState
            icon={Smartphone}
            title="No WhatsApp number yet"
            text={allowed ? 'Connect your own WhatsApp number in a few minutes, or ask us to assign one of ours.' : 'Ask our team to assign a WhatsApp number to your account.'}
            action={allowed && <Button to="/partner/senders/connect" leftIcon={Plus}>Connect WhatsApp number</Button>}
          />
        </div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {q.data.map((s) => (
            <Link key={s.id} to={`/partner/senders/${s.id}`} className="card flex flex-col gap-3 p-4 transition-colors hover:bg-surface-2">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate font-semibold text-ink">{s.verifiedName || 'Name pending'}</p>
                  <p className="text-sm text-muted">{s.displayPhoneNumber}</p>
                </div>
                <ChevronRight className="h-5 w-5 shrink-0 text-muted" aria-hidden />
              </div>
              <div className="flex flex-wrap gap-1.5">
                <StatusBadge status={s.isPaused ? 'PAUSED' : s.status} />
                <QualityBadge rating={s.qualityRating} />
                {s.isDefault && <Badge tone="brand"><Star className="h-3 w-3" aria-hidden /> Default</Badge>}
                <Badge tone="gray">{s.senderType === 'PARTNER_OWN_SENDER' ? 'My own number' : 'Provided by us'}</Badge>
              </div>
              {s.waba?.tokenStatus && ['INVALID', 'EXPIRED'].includes(s.waba.tokenStatus) && (
                <p className="flex items-center gap-1 text-sm font-medium text-red-600"><Lock className="h-4 w-4" aria-hidden /> Access expired — reconnect needed</p>
              )}
              {s.isPaused && s.pauseReason && <p className="text-sm text-amber-700 dark:text-amber-300">Paused: {s.pauseReason}</p>}
              <p className="flex items-center gap-1 text-xs text-muted">
                Messaging limit: {s.messagingLimitTier ? String(s.messagingLimitTier).replace(/_/g, ' ').toLowerCase() : '—'} <InfoTip term="MESSAGING_LIMIT" />
              </p>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
