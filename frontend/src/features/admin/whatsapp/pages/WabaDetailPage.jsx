import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { BellRing, CreditCard, KeyRound, RefreshCw, ShieldCheck } from 'lucide-react';
import * as metaApi from '../../../../api/endpoints/meta';
import { useAuth } from '../../../../auth/useAuth';
import { humanize } from '../../../../lib/format';
import {
  Badge, Button, Card, ConfirmDialog, DataTable, DateTime, EmptyState, ErrorState, KeyValue, Modal, PageHeader, PageLoader, PasswordInput, StatusBadge,
} from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';
import Checklist from '../components/Checklist';
import { senderColumns } from '../components/senderColumns';

export default function WabaDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('WHATSAPP_MANAGE');
  const [tokenOpen, setTokenOpen] = useState(false);
  const [token, setToken] = useState('');
  const [testResult, setTestResult] = useState(null);
  const [confirmShare, setConfirmShare] = useState(false);
  const q = useQuery({ queryKey: ['admin', 'wabas', 'detail', id], queryFn: () => metaApi.getWaba(id) });
  const inv = [['admin', 'wabas'], ['admin', 'senders']];
  const syncM = useApiMutation({ mutationFn: () => metaApi.syncWaba(id), success: 'Account and numbers refreshed from Meta', invalidate: inv });
  const testM = useApiMutation({ mutationFn: () => metaApi.testWaba(id), onSuccess: setTestResult, invalidate: inv });
  const subM = useApiMutation({ mutationFn: () => metaApi.subscribeWaba(id), success: 'Webhooks subscribed', invalidate: inv });
  const tokenM = useApiMutation({
    mutationFn: () => metaApi.updateWabaToken(id, token.trim()),
    success: 'Access token updated',
    invalidate: inv,
    onSuccess: () => { setTokenOpen(false); setToken(''); },
  });
  const shareM = useApiMutation({ mutationFn: () => metaApi.shareWabaCreditLine(id), success: 'Credit line shared with this account', invalidate: inv, onSuccess: () => setConfirmShare(false) });

  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const w = q.data;
  return (
    <>
      <PageHeader
        title={w.name || `WABA ${w.wabaId}`}
        subtitle={<span className="flex flex-wrap items-center gap-2"><span className="font-mono">{w.wabaId}</span><StatusBadge status={w.status} />{w.ownerType === 'PLATFORM' ? <Badge tone="brand">Our account</Badge> : <Badge tone="blue">{w.partner?.businessName || 'Partner'}</Badge>}</span>}
        backTo="/admin/whatsapp/accounts"
        actions={
          canManage && (
            <>
              <Button variant="secondary" leftIcon={RefreshCw} loading={syncM.isPending} onClick={() => syncM.mutate()}>Sync from Meta</Button>
              <Button variant="secondary" leftIcon={ShieldCheck} loading={testM.isPending} onClick={() => testM.mutate()}>Test token</Button>
            </>
          )
        }
      />
      {testResult && (
        <Card className="mb-4" title="Token test">
          <KeyValue
            items={[
              { label: 'Token', value: testResult.tokenValid ? <Badge tone="green">Valid</Badge> : <Badge tone="red">Not valid</Badge> },
              { label: 'Expires', value: testResult.expiresAt ? <DateTime value={testResult.expiresAt} /> : 'Never' },
              { label: 'Webhooks', value: testResult.subscribed ? <Badge tone="green">Subscribed</Badge> : <Badge tone="amber">Not subscribed</Badge> },
              { label: 'Permissions', value: (testResult.scopes || []).join(', ') || '—' },
            ]}
          />
        </Card>
      )}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card title="Account" className="lg:col-span-2">
          <KeyValue
            items={[
              { label: 'Business ID', value: w.businessId || '—' },
              { label: 'Onboarding', value: humanize(w.onboardingType) },
              { label: 'Billing', value: humanize(w.billingMode) },
              { label: 'Credit line shared', value: w.creditLineShared ? 'Yes' : 'No', hidden: w.billingMode !== 'SOLUTION_PARTNER' },
              { label: 'Account review', value: w.accountReviewStatus ? <StatusBadge status={w.accountReviewStatus} /> : '—' },
              { label: 'Business verification', value: w.businessVerificationStatus ? humanize(w.businessVerificationStatus) : '—' },
              { label: 'Payment method', value: w.paymentMethodAttached == null ? 'Unknown' : w.paymentMethodAttached ? 'Added' : 'Missing' },
              { label: 'Access token', value: <span className="flex items-center gap-2"><StatusBadge status={w.tokenStatus} />{w.tokenCheckedAt && <span className="text-xs text-muted">checked <DateTime value={w.tokenCheckedAt} mode="relative" /></span>}</span> },
              { label: 'Webhooks', value: w.webhookSubscribed ? <Badge tone="green">Subscribed</Badge> : <Badge tone="amber">Not subscribed</Badge> },
              { label: 'Connected', value: <DateTime value={w.connectedAt} /> },
            ]}
          />
          {canManage && (
            <div className="mt-4 flex flex-wrap gap-2 border-t border-line pt-4">
              <Button size="sm" variant="secondary" leftIcon={KeyRound} onClick={() => setTokenOpen(true)}>Replace token</Button>
              {!w.webhookSubscribed && <Button size="sm" variant="secondary" leftIcon={BellRing} loading={subM.isPending} onClick={() => subM.mutate()}>Subscribe webhooks</Button>}
              {w.billingMode === 'SOLUTION_PARTNER' && !w.creditLineShared && (
                <Button size="sm" variant="secondary" leftIcon={CreditCard} onClick={() => setConfirmShare(true)}>Share credit line</Button>
              )}
            </div>
          )}
        </Card>
        <Card title="Setup checklist">
          <Checklist checklist={w.checklist} />
        </Card>
      </div>
      <Card title="Phone numbers" className="mt-4" padded={false} bodyClassName="p-4">
        <DataTable
          columns={senderColumns({ showPartner: true })}
          rows={w.senders || []}
          onRowClick={(s) => navigate(`/admin/whatsapp/senders/${s.id}`)}
          empty={<EmptyState compact title="No numbers in this account" text="Add a number in WhatsApp Manager, then press Sync from Meta." />}
        />
      </Card>
      <Modal
        open={tokenOpen}
        onClose={() => setTokenOpen(false)}
        title="Replace access token"
        description="Paste a new permanent token. We validate it with Meta before saving."
        footer={<><Button variant="secondary" onClick={() => setTokenOpen(false)}>Cancel</Button><Button disabled={token.trim().length < 20} loading={tokenM.isPending} onClick={() => tokenM.mutate()}>Save token</Button></>}
      >
        <PasswordInput label="Access token" autoComplete="off" value={token} onChange={(e) => setToken(e.target.value)} />
      </Modal>
      <ConfirmDialog
        open={confirmShare}
        onClose={() => setConfirmShare(false)}
        title="Share our credit line?"
        message="Meta charges for this account will be billed to our credit line. The partner's wallet is charged the full rate plan price."
        confirmLabel="Share credit line"
        loading={shareM.isPending}
        onConfirm={() => shareM.mutate()}
      />
    </>
  );
}
