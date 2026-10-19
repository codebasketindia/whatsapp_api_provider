import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Ban, Megaphone, Pause } from 'lucide-react';
import * as campaignsApi from '../../../../api/endpoints/campaigns';
import { useAuth } from '../../../../auth/useAuth';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatNumber, humanize } from '../../../../lib/format';
import {
  Button, DataTable, DateRangePicker, DateTime, Drawer, EmptyState, FilterBar, Input, KeyValue, Money, PageHeader, ProgressBar, Select, StatusBadge,
} from '../../../../components/ui';
import ReasonDialog from '../../shared/ReasonDialog';
import { useApiMutation } from '../../shared/useApiMutation';

const STATUSES = ['DRAFT', 'SCHEDULED', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'];

export const campaignProgress = (c) => {
  const k = c.counts || {};
  const done = (k.sent || 0) + (k.delivered || 0) + (k.read || 0) + (k.failed || 0);
  const total = k.valid || k.total || 0;
  return total ? Math.min(100, Math.round((done / total) * 100)) : 0;
};

export default function CampaignsPage() {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('CAMPAIGN_MANAGE');
  const [state, update] = useUrlState({ page: 0, status: '', partnerId: '', from: '', to: '' });
  const [selected, setSelected] = useState(null);
  const [action, setAction] = useState(null);
  const q = useQuery({
    queryKey: ['admin', 'campaigns', state],
    queryFn: () => campaignsApi.listCampaigns({ ...state, size: 20 }),
    placeholderData: (p) => p,
    refetchInterval: (query) => (query.state.data?.content?.some((c) => c.status === 'RUNNING') ? 10_000 : false),
  });
  const pauseM = useApiMutation({ mutationFn: ({ c, reason }) => campaignsApi.pauseCampaignAdmin(c.id, reason), success: 'Campaign paused', invalidate: [['admin', 'campaigns']], onSuccess: () => { setAction(null); setSelected(null); } });
  const cancelM = useApiMutation({ mutationFn: ({ c, reason }) => campaignsApi.cancelCampaignAdmin(c.id, reason), success: 'Campaign cancelled', invalidate: [['admin', 'campaigns']], onSuccess: () => { setAction(null); setSelected(null); } });

  const actions = (c, size = 'sm') =>
    canManage && ['RUNNING', 'SCHEDULED', 'PAUSED'].includes(c.status) ? (
      <>
        {c.status !== 'PAUSED' && <Button size={size} variant="secondary" leftIcon={Pause} onClick={(e) => { e.stopPropagation(); setAction({ type: 'pause', c }); }}>Pause</Button>}
        <Button size={size} variant="danger-outline" leftIcon={Ban} onClick={(e) => { e.stopPropagation(); setAction({ type: 'cancel', c }); }}>Cancel</Button>
      </>
    ) : null;

  const columns = [
    {
      key: 'name',
      header: 'Campaign',
      primary: true,
      render: (c) => (
        <span>
          <span className="block font-medium text-ink">{c.name}</span>
          <span className="block text-xs text-muted">{c.partner?.businessName} · {c.template?.name}</span>
        </span>
      ),
    },
    { key: 'status', header: 'Status', render: (c) => <span><StatusBadge status={c.status} />{c.pauseReason && <span className="block text-xs text-muted">{humanize(c.pauseReason)}</span>}</span> },
    { key: 'progress', header: 'Progress', render: (c) => <div className="w-32"><ProgressBar value={campaignProgress(c)} /><span className="text-xs text-muted">{formatNumber(c.counts?.valid ?? c.counts?.total)} recipients</span></div> },
    { key: 'cost', header: 'Charged', align: 'right', hideOnMobile: true, render: (c) => <Money value={c.chargedAmount} /> },
    { key: 'when', header: 'Scheduled / started', hideOnMobile: true, render: (c) => <DateTime value={c.startedAt || c.scheduledAt || c.createdAt} /> },
    { key: 'actions', header: <span className="sr-only">Actions</span>, align: 'right', hideOnMobile: true, render: (c) => <span className="flex justify-end gap-2">{actions(c)}</span> },
  ];
  const c = selected;
  return (
    <>
      <PageHeader title="Campaigns" subtitle="Bulk campaigns of all partners. Pause or cancel a campaign if something looks wrong." />
      <FilterBar>
        <Select aria-label="Status" value={state.status} onChange={(e) => update({ status: e.target.value })} placeholder="All statuses" options={STATUSES.map((s) => ({ value: s, label: humanize(s) }))} />
        <Input aria-label="Partner id" placeholder="Partner id" inputMode="numeric" value={state.partnerId} onChange={(e) => update({ partnerId: e.target.value.replace(/\D/g, '') })} />
      </FilterBar>
      <DateRangePicker className="mb-4" value={{ from: state.from, to: state.to }} onChange={(r) => update(r)} allowClear />
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={setSelected}
        mobileActions={(row) => actions(row)}
        empty={<EmptyState icon={Megaphone} title="No campaigns found" />}
      />
      <Drawer open={Boolean(c)} onClose={() => setSelected(null)} title={c?.name} description={c?.partner?.businessName} footer={c && actions(c, 'md')}>
        {c && (
          <div className="space-y-4">
            <ProgressBar value={campaignProgress(c)} />
            <KeyValue
              items={[
                { label: 'Status', value: <StatusBadge status={c.status} /> },
                { label: 'Pause reason', value: humanize(c.pauseReason), hidden: !c.pauseReason },
                { label: 'Sender', value: c.sender?.displayPhoneNumber },
                { label: 'Template', value: c.template?.name },
                { label: 'Rows in file', value: formatNumber(c.counts?.total) },
                { label: 'Valid recipients', value: formatNumber(c.counts?.valid) },
                { label: 'Skipped', value: formatNumber((c.counts?.invalid || 0) + (c.counts?.skipped || 0)) },
                { label: 'Queued', value: formatNumber(c.counts?.queued) },
                { label: 'Sent', value: formatNumber(c.counts?.sent) },
                { label: 'Delivered', value: formatNumber(c.counts?.delivered) },
                { label: 'Read', value: formatNumber(c.counts?.read) },
                { label: 'Failed', value: formatNumber(c.counts?.failed) },
                { label: 'Estimated cost', value: <Money value={c.estimatedCost} /> },
                { label: 'Charged', value: <Money value={c.chargedAmount} /> },
                { label: 'Refunded', value: <Money value={c.refundedAmount} /> },
                { label: 'Scheduled for', value: <DateTime value={c.scheduledAt} />, hidden: !c.scheduledAt },
                { label: 'Started', value: <DateTime value={c.startedAt} /> },
                { label: 'Completed', value: <DateTime value={c.completedAt} />, hidden: !c.completedAt },
              ]}
            />
          </div>
        )}
      </Drawer>
      <ReasonDialog
        open={action?.type === 'pause'}
        onClose={() => setAction(null)}
        title={`Pause "${action?.c?.name}"?`}
        message="No new messages are sent until the partner resumes it. The partner sees your reason."
        confirmLabel="Pause campaign"
        loading={pauseM.isPending}
        onConfirm={(reason) => pauseM.mutate({ c: action.c, reason })}
      />
      <ReasonDialog
        open={action?.type === 'cancel'}
        onClose={() => setAction(null)}
        danger
        title={`Cancel "${action?.c?.name}"?`}
        message="Recipients not yet sent will be skipped. This cannot be undone. Messages already queued may still go out."
        confirmLabel="Cancel campaign"
        loading={cancelM.isPending}
        onConfirm={(reason) => cancelM.mutate({ c: action.c, reason })}
      />
    </>
  );
}
