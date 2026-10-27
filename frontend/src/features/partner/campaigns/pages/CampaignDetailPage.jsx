import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { CalendarClock, Download, Pause, Pencil, Play, Trash2, XCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import * as campaignsApi from '../../../../api/endpoints/campaigns';
import { getErrorMessage } from '../../../../api/axios';
import {
  Alert, Button, Card, ConfirmDialog, DataTable, DateTime, EmptyState, ErrorState, FilterBar, KeyValue, PageHeader, PageLoader, ProgressBar,
  SearchInput, Select, StatCard, StatusBadge,
} from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatDateTime, formatMoney, formatNumber, formatPhone, formatPercent } from '../../../../lib/format';
import { CAMPAIGN_STATUS_LABEL, PAUSE_REASON_LABEL, SKIP_REASON_LABEL } from '../components/schedule';
import { campaignProgress } from './MyCampaignsPage';
import ScheduleTimeDialog from '../../scheduled/components/ScheduleTimeDialog';

const RECIPIENT_STATUSES = ['PENDING', 'SKIPPED', 'QUEUED', 'SENT', 'DELIVERED', 'READ', 'FAILED'];

function Recipients({ id }) {
  const [f, update] = useUrlState({ page: 0, rstatus: '', rsearch: '' });
  const params = { page: f.page, size: 20, status: f.rstatus, search: f.rsearch };
  const q = useQuery({ queryKey: ['partner', 'campaigns', id, 'recipients', params], queryFn: () => campaignsApi.listCampaignRecipients(id, params), placeholderData: (p) => p });
  const columns = [
    { key: 'rowNo', header: 'Row', hideOnMobile: true, render: (r) => r.rowNo },
    { key: 'phone', header: 'Number', primary: true, render: (r) => formatPhone(r.phone) },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
    {
      key: 'note',
      header: 'Note',
      render: (r) => (r.skipReason ? SKIP_REASON_LABEL[r.skipReason] || r.skipReason : r.errorMessage || '—'),
    },
    {
      key: 'message',
      header: 'Message',
      hideOnMobile: true,
      render: (r) => (r.messageUid ? <Link className="text-brand-700 underline dark:text-brand-300" to={`/partner/reports?search=${r.messageUid}`}>View</Link> : '—'),
    },
  ];
  return (
    <Card title="People">
      <FilterBar>
        <SearchInput value={f.rsearch} onSearch={(v) => update({ rsearch: v })} placeholder="Search number" />
        <Select aria-label="Status" value={f.rstatus} onChange={(e) => update({ rstatus: e.target.value })} options={RECIPIENT_STATUSES.map((s) => ({ value: s, label: s.charAt(0) + s.slice(1).toLowerCase() }))} placeholder="All" />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        rowKey={(r) => `${r.rowNo}-${r.phone}`}
        empty={<EmptyState compact title="Nobody in this list" />}
      />
    </Card>
  );
}

export default function CampaignDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [confirm, setConfirm] = useState(null);
  const q = useQuery({
    queryKey: ['partner', 'campaigns', 'detail', Number(id)],
    queryFn: () => campaignsApi.getMyCampaign(id),
    refetchInterval: (query) => (['RUNNING', 'SCHEDULED'].includes(query.state.data?.status) ? 5000 : false),
  });
  const after = (msg) => ({
    onSuccess: () => {
      toast.success(msg);
      setConfirm(null);
      qc.invalidateQueries({ queryKey: ['partner', 'campaigns'] });
    },
    onError: (e) => {
      setConfirm(null);
      toast.error(getErrorMessage(e));
    },
  });
  const pause = useMutation({ mutationFn: () => campaignsApi.pauseCampaign(id), ...after('Campaign paused') });
  const resume = useMutation({ mutationFn: () => campaignsApi.resumeCampaign(id), ...after('Campaign resumed') });
  const cancel = useMutation({ mutationFn: () => campaignsApi.cancelCampaign(id), ...after('Campaign cancelled') });
  const reschedule = useMutation({ mutationFn: (iso) => campaignsApi.rescheduleCampaign(id, iso), ...after('Campaign time changed') });
  const del = useMutation({
    mutationFn: () => campaignsApi.deleteCampaign(id),
    onSuccess: () => {
      toast.success('Draft deleted');
      navigate('/partner/campaigns');
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const [downloading, setDownloading] = useState(null);
  const download = async (format) => {
    setDownloading(format);
    try {
      await campaignsApi.downloadCampaignReport(id, format);
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setDownloading(null);
    }
  };

  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const c = q.data;
  const k = c.counts || {};
  const delivered = (k.delivered || 0) + (k.read || 0);
  const attempted = (k.sent || 0) + delivered + (k.failed || 0);
  const status = c.status;

  return (
    <>
      <PageHeader
        title={c.name}
        subtitle={`${c.template?.name || ''}${c.sender?.displayPhoneNumber ? ` · from ${c.sender.displayPhoneNumber}` : ''}`}
        backTo="/partner/campaigns"
        backLabel="Campaigns"
        actions={
          <>
            {status === 'DRAFT' && <Button leftIcon={Pencil} to={`/partner/campaigns/new?id=${c.id}`}>Continue setup</Button>}
            {status === 'DRAFT' && <Button variant="danger-outline" leftIcon={Trash2} onClick={() => setConfirm('delete')}>Delete</Button>}
            {['RUNNING', 'SCHEDULED'].includes(status) && <Button variant="secondary" leftIcon={Pause} onClick={() => pause.mutate()} loading={pause.isPending}>Pause</Button>}
            {status === 'PAUSED' && <Button leftIcon={Play} onClick={() => setConfirm('resume')}>Resume</Button>}
            {status === 'SCHEDULED' && <Button variant="secondary" leftIcon={CalendarClock} onClick={() => setConfirm('reschedule')}>Change time</Button>}
            {['RUNNING', 'SCHEDULED', 'PAUSED'].includes(status) && <Button variant="danger-outline" leftIcon={XCircle} onClick={() => setConfirm('cancel')}>Cancel</Button>}
            {status !== 'DRAFT' && (
              <>
                <Button variant="secondary" leftIcon={Download} loading={downloading === 'xlsx'} onClick={() => download('xlsx')}>Report</Button>
                <Button variant="ghost" loading={downloading === 'csv'} onClick={() => download('csv')}>CSV</Button>
              </>
            )}
          </>
        }
      />
      {status === 'PAUSED' && c.pauseReason && (
        <Alert variant="warning" className="mb-4" title="Campaign is paused" action={c.pauseReason === 'INSUFFICIENT_BALANCE' && <Button size="sm" to="/partner/wallet/add">Add money</Button>}>
          {PAUSE_REASON_LABEL[c.pauseReason] || c.pauseReason}
        </Alert>
      )}
      {status === 'SCHEDULED' && <Alert variant="info" className="mb-4">Scheduled for {formatDateTime(c.scheduledAt)} (India time).</Alert>}
      <Card className="mb-5">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <StatusBadge status={status} label={CAMPAIGN_STATUS_LABEL[status]} />
          <span className="text-sm text-muted">{formatPercent(campaignProgress(c), 0)} done{status === 'RUNNING' ? ' · updates every few seconds' : ''}</span>
        </div>
        <ProgressBar
          segments={[
            { value: ((k.read || 0) / Math.max(1, k.valid || 0)) * 100, className: 'bg-sky-500' },
            { value: ((k.delivered || 0) / Math.max(1, k.valid || 0)) * 100, className: 'bg-brand-500' },
            { value: ((k.sent || 0) / Math.max(1, k.valid || 0)) * 100, className: 'bg-gray-400' },
            { value: ((k.failed || 0) / Math.max(1, k.valid || 0)) * 100, className: 'bg-red-500' },
          ]}
        />
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <StatCard label="People" value={formatNumber(k.valid ?? k.total)} />
          <StatCard label="Waiting" tone="amber" value={formatNumber((k.queued || 0) + Math.max(0, (k.valid || 0) - (k.queued || 0) - attempted))} />
          <StatCard label="Sent" value={formatNumber(k.sent)} />
          <StatCard label="Delivered" tone="green" value={formatNumber(delivered)} hint={attempted ? `${formatPercent((delivered / attempted) * 100, 0)} of sent` : undefined} />
          <StatCard label="Read" tone="blue" value={formatNumber(k.read)} />
          <StatCard label="Failed" tone="red" value={formatNumber(k.failed)} hint={k.skipped ? `${formatNumber(k.skipped)} skipped` : undefined} />
        </div>
      </Card>
      <Card title="Details" className="mb-5">
        <KeyValue
          columns={3}
          items={[
            { label: 'Estimated cost', value: formatMoney(c.estimatedCost) },
            { label: 'Charged so far', value: formatMoney(c.chargedAmount) },
            { label: 'Refunded for failed', value: formatMoney(c.refundedAmount) },
            { label: 'Price per message', value: formatMoney(c.unitPrice, { precise: true }) },
            { label: 'Started', value: formatDateTime(c.startedAt) },
            { label: 'Finished', value: formatDateTime(c.completedAt) },
            { label: 'Created', value: <DateTime value={c.createdAt} /> },
            { label: 'Audience', value: c.audienceType === 'FILE' ? 'Uploaded file' : c.audienceType === 'GROUPS' ? 'Contact groups' : 'Selected contacts' },
          ]}
        />
      </Card>
      {status !== 'DRAFT' && <Recipients id={id} />}
      <ConfirmDialog
        open={confirm === 'resume'}
        onClose={() => setConfirm(null)}
        onConfirm={() => resume.mutate()}
        loading={resume.isPending}
        title="Resume this campaign?"
        message="Sending continues for the remaining people. Their message price is held from your wallet."
        confirmLabel="Resume"
      />
      <ConfirmDialog
        open={confirm === 'cancel'}
        onClose={() => setConfirm(null)}
        onConfirm={() => cancel.mutate()}
        loading={cancel.isPending}
        danger
        title="Cancel this campaign?"
        message="People who have not been sent the message yet will be skipped. This cannot be undone."
        confirmWord="CANCEL"
        confirmLabel="Cancel campaign"
      />
      <ScheduleTimeDialog
        open={confirm === 'reschedule'}
        onClose={() => setConfirm(null)}
        title="Change campaign time"
        description="The campaign starts automatically at the new time (India time)."
        initialIso={c.scheduledAt}
        submitLabel="Save new time"
        loading={reschedule.isPending}
        onSubmit={(iso) => reschedule.mutate(iso)}
      />
      <ConfirmDialog
        open={confirm === 'delete'}
        onClose={() => setConfirm(null)}
        onConfirm={() => del.mutate()}
        loading={del.isPending}
        danger
        title="Delete this draft?"
        confirmLabel="Delete"
      />
    </>
  );
}
