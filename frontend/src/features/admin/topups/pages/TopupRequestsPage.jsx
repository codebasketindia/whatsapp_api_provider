import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Check, Eye, Wallet, X } from 'lucide-react';
import * as walletApi from '../../../../api/endpoints/wallet';
import { useAuth } from '../../../../auth/useAuth';
import { useUrlState } from '../../../../hooks/useUrlState';
import { PAYMENT_MODES } from '../../../../lib/constants';
import { formatDate, formatMoney, humanize } from '../../../../lib/format';
import {
  Button, ConfirmDialog, DataTable, DateRangePicker, DateTime, Drawer, EmptyState, FilterBar, KeyValue, Money, PageHeader,
  SearchInput, Select, StatusBadge, Tabs, Textarea,
} from '../../../../components/ui';
import ReasonDialog from '../../shared/ReasonDialog';
import ImageOrPdfViewer from '../../shared/ImageOrPdfViewer';
import { useApiMutation } from '../../shared/useApiMutation';

const STATUS_TABS = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All' },
];

export default function TopupRequestsPage() {
  const { hasPermission } = useAuth();
  const canApprove = hasPermission('TOPUP_APPROVE');
  const [state, update] = useUrlState({ page: 0, status: 'PENDING', search: '', paymentMode: '', from: '', to: '' });
  const [selected, setSelected] = useState(null);
  const [action, setAction] = useState(null); // { type: 'approve'|'reject', req }
  const [remarks, setRemarks] = useState('');

  const q = useQuery({
    queryKey: ['admin', 'topups', state],
    queryFn: () =>
      walletApi.listTopupRequests({
        page: state.page, size: 20, status: state.status === 'ALL' ? '' : state.status, search: state.search,
        paymentMode: state.paymentMode, from: state.from, to: state.to,
      }),
    placeholderData: (p) => p,
  });
  const pendingCountQ = useQuery({
    queryKey: ['admin', 'topups', 'pending-count'],
    queryFn: () => walletApi.listTopupRequests({ status: 'PENDING', size: 1 }),
  });

  const approveM = useApiMutation({
    mutationFn: ({ req, remarks: r }) => walletApi.approveTopupRequest(req.id, { remarks: r || undefined }),
    success: (d) => `Approved. ${formatMoney(d?.amount)} credited to ${d?.partner?.businessName || 'the partner'}.`,
    invalidate: [['admin', 'topups'], ['admin', 'partners'], ['admin', 'dashboard']],
    onSuccess: () => { setAction(null); setSelected(null); setRemarks(''); },
  });
  const rejectM = useApiMutation({
    mutationFn: ({ req, reason }) => walletApi.rejectTopupRequest(req.id, reason),
    success: 'Request rejected. The partner has been informed.',
    invalidate: [['admin', 'topups'], ['admin', 'dashboard']],
    onSuccess: () => { setAction(null); setSelected(null); },
  });

  const actionButtons = (r, size = 'sm') =>
    canApprove && r.status === 'PENDING' ? (
      <>
        <Button size={size} leftIcon={Check} onClick={(e) => { e.stopPropagation(); setAction({ type: 'approve', req: r }); }}>Approve</Button>
        <Button size={size} variant="danger-outline" leftIcon={X} onClick={(e) => { e.stopPropagation(); setAction({ type: 'reject', req: r }); }}>Reject</Button>
      </>
    ) : null;

  const columns = [
    {
      key: 'partner',
      header: 'Partner',
      primary: true,
      render: (r) => (
        <span>
          <span className="block font-medium text-ink">{r.partner?.businessName}</span>
          <span className="block text-xs text-muted">{r.requestNo}</span>
        </span>
      ),
    },
    { key: 'amount', header: 'Amount', align: 'right', render: (r) => <Money value={r.amount} className="font-semibold" /> },
    {
      key: 'utr',
      header: 'Payment',
      render: (r) => (
        <span>
          <span className="block">{humanize(r.paymentMode)} · <span className="font-mono text-xs">{r.utr}</span></span>
          <span className="block text-xs text-muted">Paid on {formatDate(r.paymentDate)}</span>
        </span>
      ),
    },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
    { key: 'createdAt', header: 'Requested', hideOnMobile: true, render: (r) => <DateTime value={r.createdAt} mode="relative" /> },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      align: 'right',
      hideOnMobile: true,
      render: (r) => (
        <span className="flex justify-end gap-2">
          <Button size="sm" variant="secondary" leftIcon={Eye} onClick={(e) => { e.stopPropagation(); setSelected(r); }}>View</Button>
          {actionButtons(r)}
        </span>
      ),
    },
  ];

  const pendingCount = pendingCountQ.data?.totalElements;
  const a = action;
  return (
    <>
      <PageHeader title="Top-up requests" subtitle="Manual payments reported by partners. Check the proof, then approve to credit the wallet." />
      <Tabs
        className="mb-4"
        tabs={STATUS_TABS.map((t) => (t.value === 'PENDING' ? { ...t, count: pendingCount } : t))}
        value={state.status}
        onChange={(v) => update({ status: v })}
      />
      <FilterBar>
        <SearchInput className="sm:flex-[2]" value={state.search} onSearch={(v) => update({ search: v })} placeholder="Search UTR or request number" />
        <Select
          aria-label="Payment mode"
          value={state.paymentMode}
          onChange={(e) => update({ paymentMode: e.target.value })}
          placeholder="All payment modes"
          options={PAYMENT_MODES.map((m) => (typeof m === 'object' ? m : { value: m, label: humanize(m) }))}
        />
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
        mobileActions={(r) => actionButtons(r)}
        empty={
          <EmptyState
            icon={Wallet}
            title={state.status === 'PENDING' ? 'No pending requests' : 'No requests found'}
            text={state.status === 'PENDING' ? 'All caught up. New requests from partners will appear here.' : 'Try another status or date range.'}
          />
        }
      />

      <Drawer
        open={Boolean(selected)}
        onClose={() => setSelected(null)}
        title={selected ? `Request ${selected.requestNo}` : ''}
        description={selected?.partner?.businessName}
        width="md:w-[560px]"
        footer={selected && actionButtons(selected, 'md')}
      >
        {selected && (
          <div className="space-y-5">
            <KeyValue
              items={[
                { label: 'Amount', value: <Money value={selected.amount} className="text-lg font-semibold" /> },
                { label: 'Status', value: <StatusBadge status={selected.status} /> },
                { label: 'Payment mode', value: humanize(selected.paymentMode) },
                { label: 'UTR / reference', value: <span className="font-mono">{selected.utr}</span> },
                { label: 'Payment date', value: formatDate(selected.paymentDate) },
                { label: 'Requested', value: <DateTime value={selected.createdAt} /> },
                { label: 'Note from partner', value: selected.note || '—' },
                { label: 'Reviewed by', value: selected.reviewedBy?.name, hidden: !selected.reviewedBy },
                { label: 'Reviewed at', value: <DateTime value={selected.reviewedAt} />, hidden: !selected.reviewedAt },
                { label: 'Reject reason', value: selected.rejectReason, hidden: !selected.rejectReason },
              ]}
            />
            <div>
              <h3 className="mb-2 text-sm font-semibold text-ink">Payment proof</h3>
              <ImageOrPdfViewer url={selected.screenshotUrl} filename={`${selected.requestNo}-proof`} />
            </div>
          </div>
        )}
      </Drawer>

      <ConfirmDialog
        open={a?.type === 'approve'}
        onClose={() => setAction(null)}
        title={`Approve ${formatMoney(a?.req?.amount)}?`}
        message={
          <span>
            <Money value={a?.req?.amount} className="font-semibold" /> will be credited to <b>{a?.req?.partner?.businessName}</b> right away.
            Make sure the money (UTR <span className="font-mono">{a?.req?.utr}</span>) has reached the bank account. This cannot be undone.
          </span>
        }
        confirmLabel="Approve and credit"
        loading={approveM.isPending}
        onConfirm={() => approveM.mutate({ req: a.req, remarks })}
      >
        <Textarea label="Remark (optional)" rows={2} maxLength={500} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
      </ConfirmDialog>
      <ReasonDialog
        open={a?.type === 'reject'}
        onClose={() => setAction(null)}
        danger
        title="Reject this request?"
        message="The partner will see your reason. No money will be credited."
        confirmLabel="Reject"
        label="Reason for rejection"
        placeholder="e.g. Payment not received in bank, UTR does not match"
        loading={rejectM.isPending}
        onConfirm={(reason) => rejectM.mutate({ req: a.req, reason })}
      />
    </>
  );
}
