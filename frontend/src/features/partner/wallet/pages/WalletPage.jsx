import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, FileText, Plus, Settings2 } from 'lucide-react';
import toast from 'react-hot-toast';
import * as walletApi from '../../../../api/endpoints/wallet';
import * as paymentsApi from '../../../../api/endpoints/payments';
import { getErrorMessage } from '../../../../api/axios';
import { downloadStoredFile, toApiPath } from '../../../../api/endpoints/files';
import {
  Button, DataTable, DateRangePicker, DateTime, EmptyState, FilterBar, Input, Modal, Money, PageHeader, Select, Skeleton,
  StatusBadge, Switch, Tabs, useActiveTab,
} from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { WALLET_TXN_TYPES } from '../../../../lib/constants';
import { formatMoney, humanize, toNumber } from '../../../../lib/format';
import { balanceDelta, txnDescription } from '../components/walletLabels';

const TABS = [
  { value: 'passbook', label: 'Passbook' },
  { value: 'requests', label: 'My requests' },
  { value: 'payments', label: 'Online payments' },
  { value: 'invoices', label: 'Invoices' },
];

function ThresholdModal({ open, onClose, wallet }) {
  const qc = useQueryClient();
  const [value, setValue] = useState(wallet?.lowBalanceThreshold ? String(toNumber(wallet.lowBalanceThreshold)) : '');
  const m = useMutation({
    mutationFn: () => walletApi.setLowBalanceThreshold(value === '' ? null : value),
    onSuccess: () => {
      toast.success('Alert level saved');
      qc.invalidateQueries({ queryKey: ['partner', 'wallet'] });
      onClose();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const invalid = value !== '' && (!/^\d+(\.\d{1,2})?$/.test(value) || toNumber(value) > 10000000);
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Low balance alert"
      description="We warn you on the dashboard and by email when your balance falls below this amount."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={() => m.mutate()} loading={m.isPending} disabled={invalid}>Save</Button>
        </>
      }
    >
      <Input
        label="Alert me below"
        prefix="₹"
        inputMode="decimal"
        value={value}
        onChange={(e) => setValue(e.target.value.replace(/[^\d.]/g, ''))}
        error={invalid ? 'Enter a valid amount' : undefined}
        hint="Leave empty to use the default set by us"
      />
    </Modal>
  );
}

function Passbook() {
  const [f, update] = useUrlState({ page: 0, type: '', from: '', to: '', includeMessageEntries: 'false' });
  const params = { page: f.page, size: 20, type: f.type, from: f.from, to: f.to, includeMessageEntries: f.includeMessageEntries };
  const q = useQuery({
    queryKey: ['partner', 'wallet', 'transactions', params],
    queryFn: () => walletApi.listMyWalletTransactions(params),
    placeholderData: (p) => p,
  });
  const [exporting, setExporting] = useState(null);
  const doExport = async (format) => {
    setExporting(format);
    try {
      await walletApi.exportMyWalletTransactions({ ...params, page: undefined, size: undefined, format });
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setExporting(null);
    }
  };
  const columns = [
    { key: 'createdAt', header: 'Date', render: (r) => <DateTime value={r.createdAt} /> },
    {
      key: 'desc',
      header: 'Details',
      primary: true,
      render: (r) => (
        <div>
          <p className="font-medium text-ink">{txnDescription(r)}</p>
          {r.remarks && <p className="text-xs text-muted">{r.remarks}</p>}
        </div>
      ),
    },
    { key: 'type', header: 'Type', render: (r) => <StatusBadge status={r.type} /> },
    {
      key: 'amount',
      header: 'Amount',
      align: 'right',
      render: (r) => {
        const d = balanceDelta(r);
        if (d === 0) return <span className="text-muted"><Money value={r.amount} precise /> <span className="text-xs">(from hold)</span></span>;
        return <Money value={r.amount} precise signed negative={d < 0} />;
      },
    },
    { key: 'balanceAfter', header: 'Balance', align: 'right', render: (r) => <Money value={r.balanceAfter} /> },
  ];
  return (
    <>
      <FilterBar>
        <Select
          aria-label="Type"
          value={f.type}
          onChange={(e) => update({ type: e.target.value })}
          options={WALLET_TXN_TYPES.map((t) => ({ value: t, label: humanize(t) }))}
          placeholder="All types"
          className="sm:w-44"
        />
        <DateRangePicker value={{ from: f.from, to: f.to }} onChange={(v) => update(v)} allowClear />
        <Switch
          checked={f.includeMessageEntries === 'true'}
          onChange={(v) => update({ includeMessageEntries: v ? 'true' : 'false' })}
          label="Show each message"
          description="Holds and charges per message"
        />
      </FilterBar>
      <div className="mb-3 flex flex-wrap justify-end gap-2">
        <Button size="sm" variant="secondary" leftIcon={Download} loading={exporting === 'csv'} onClick={() => doExport('csv')}>CSV</Button>
        <Button size="sm" variant="secondary" leftIcon={Download} loading={exporting === 'xlsx'} onClick={() => doExport('xlsx')}>Excel</Button>
      </div>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        rowKey={(r) => r.uid}
        empty={<EmptyState compact title="No entries" text="Money added and message charges will appear here." />}
      />
    </>
  );
}

function Requests() {
  const [f, update] = useUrlState({ page: 0, status: '' });
  const params = { page: f.page, size: 20, status: f.status };
  const q = useQuery({ queryKey: ['partner', 'topups', params], queryFn: () => walletApi.listMyTopupRequests(params), placeholderData: (p) => p });
  const columns = [
    { key: 'requestNo', header: 'Request', primary: true, render: (r) => <span className="font-medium">{r.requestNo}</span> },
    { key: 'createdAt', header: 'Submitted', render: (r) => <DateTime value={r.createdAt} /> },
    { key: 'amount', header: 'Amount', align: 'right', render: (r) => <Money value={r.amount} /> },
    { key: 'paymentMode', header: 'Mode', render: (r) => r.paymentMode },
    { key: 'utr', header: 'UTR / Ref.', render: (r) => <span className="font-mono text-xs">{r.utr}</span> },
    {
      key: 'status',
      header: 'Status',
      render: (r) => (
        <div className="text-right md:text-left">
          <StatusBadge status={r.status} />
          {r.rejectReason && <p className="mt-1 text-xs text-red-600">{r.rejectReason}</p>}
        </div>
      ),
    },
    {
      key: 'proof',
      header: 'Proof',
      hideOnMobile: true,
      render: (r) =>
        r.screenshotUrl ? (
          <Button size="sm" variant="link" onClick={() => downloadStoredFile(toApiPath(r.screenshotUrl).replace('/files/', ''), `${r.requestNo}`).catch((e) => toast.error(getErrorMessage(e)))}>
            View
          </Button>
        ) : '—',
    },
  ];
  return (
    <>
      <FilterBar>
        <Select
          aria-label="Status"
          value={f.status}
          onChange={(e) => update({ status: e.target.value })}
          options={['PENDING', 'APPROVED', 'REJECTED'].map((s) => ({ value: s, label: humanize(s) }))}
          placeholder="All statuses"
          className="sm:w-44"
        />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        empty={
          <EmptyState
            compact
            title="No requests"
            text="Paid by bank transfer or UPI? Tell us and we will add it to your wallet."
            action={<Button size="sm" to="/partner/wallet/add?option=manual">I already paid</Button>}
          />
        }
      />
    </>
  );
}

function Payments() {
  const navigate = useNavigate();
  const [f, update] = useUrlState({ page: 0, status: '' });
  const params = { page: f.page, size: 20, status: f.status };
  const q = useQuery({ queryKey: ['partner', 'payments', params], queryFn: () => paymentsApi.listMyPayments(params), placeholderData: (p) => p });
  const columns = [
    { key: 'createdAt', header: 'Date', render: (r) => <DateTime value={r.createdAt} /> },
    { key: 'what', header: 'Item', primary: true, render: (r) => <span className="font-medium">{r.creditPack?.name || 'Wallet top-up'}</span> },
    { key: 'totalAmount', header: 'Paid', align: 'right', render: (r) => <Money value={r.totalAmount} /> },
    { key: 'creditAmount', header: 'Added to wallet', align: 'right', render: (r) => <Money value={r.creditAmount} /> },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
  ];
  return (
    <DataTable
      columns={columns}
      page={q.data}
      loading={q.isLoading}
      error={q.error}
      onRetry={q.refetch}
      onPageChange={(p) => update({ page: p })}
      rowKey={(r) => r.uid}
      onRowClick={(r) => navigate(`/partner/wallet/payment/${r.uid}`)}
      empty={<EmptyState compact title="No online payments" action={<Button size="sm" to="/partner/wallet/add">Pay by UPI</Button>} />}
    />
  );
}

function Invoices() {
  const [f, update] = useUrlState({ page: 0, from: '', to: '' });
  const params = { page: f.page, size: 20, from: f.from, to: f.to };
  const q = useQuery({ queryKey: ['partner', 'invoices', params], queryFn: () => paymentsApi.listMyInvoices(params), placeholderData: (p) => p });
  const [busy, setBusy] = useState(null);
  const download = async (inv) => {
    setBusy(inv.id);
    try {
      await paymentsApi.downloadMyInvoice(inv.id, inv.invoiceNo);
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setBusy(null);
    }
  };
  const columns = [
    { key: 'invoiceNo', header: 'Invoice', primary: true, render: (r) => <span className="font-medium">{r.invoiceNo}</span> },
    { key: 'invoiceDate', header: 'Date', render: (r) => <DateTime value={r.invoiceDate} mode="date" /> },
    { key: 'taxableAmount', header: 'Amount', align: 'right', render: (r) => <Money value={r.taxableAmount} /> },
    { key: 'gst', header: 'GST', align: 'right', render: (r) => <Money value={toNumber(r.cgst) + toNumber(r.sgst) + toNumber(r.igst)} /> },
    { key: 'total', header: 'Total', align: 'right', render: (r) => <Money value={r.total} /> },
    {
      key: 'pdf',
      header: 'PDF',
      render: (r) => (
        <Button size="sm" variant="secondary" leftIcon={FileText} loading={busy === r.id} onClick={() => download(r)}>
          Download
        </Button>
      ),
    },
  ];
  return (
    <>
      <FilterBar>
        <DateRangePicker value={{ from: f.from, to: f.to }} onChange={(v) => update(v)} allowClear />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        empty={<EmptyState compact title="No invoices yet" text="Invoices are created for payments when GST billing is on." />}
      />
    </>
  );
}

export default function WalletPage() {
  const tab = useActiveTab(TABS);
  const [thresholdOpen, setThresholdOpen] = useState(false);
  const w = useQuery({ queryKey: ['partner', 'wallet'], queryFn: walletApi.getMyWallet });
  return (
    <>
      <PageHeader title="Wallet" subtitle="Your prepaid balance for sending messages" actions={<Button to="/partner/wallet/add" leftIcon={Plus}>Add money</Button>} />
      <div className="mb-5 grid gap-3 sm:grid-cols-3">
        <div className="card p-4 sm:col-span-1">
          <p className="text-sm text-muted">Available balance</p>
          {w.isLoading ? <Skeleton className="mt-1 h-9 w-36" /> : <p className="text-3xl font-bold tabular-nums text-ink">{formatMoney(w.data?.balance)}</p>}
        </div>
        <div className="card p-4">
          <p className="text-sm text-muted">On hold</p>
          {w.isLoading ? <Skeleton className="mt-1 h-7 w-24" /> : <p className="text-xl font-semibold tabular-nums text-ink">{formatMoney(w.data?.held)}</p>}
          <p className="text-xs text-muted">Held for messages until delivery is confirmed</p>
        </div>
        <div className="card flex items-start justify-between gap-2 p-4">
          <div>
            <p className="text-sm text-muted">Low balance alert</p>
            {w.isLoading ? <Skeleton className="mt-1 h-7 w-24" /> : <p className="text-xl font-semibold tabular-nums text-ink">{formatMoney(w.data?.lowBalanceThreshold)}</p>}
            {w.data?.isLow && <StatusBadge status="LOW" label="Below alert level" />}
          </div>
          <Button size="sm" variant="secondary" leftIcon={Settings2} onClick={() => setThresholdOpen(true)} disabled={!w.data}>
            Change
          </Button>
        </div>
      </div>
      <Tabs tabs={TABS} urlParam="tab" className="mb-4" />
      {tab === 'passbook' && <Passbook />}
      {tab === 'requests' && <Requests />}
      {tab === 'payments' && <Payments />}
      {tab === 'invoices' && <Invoices />}
      {thresholdOpen && <ThresholdModal open onClose={() => setThresholdOpen(false)} wallet={w.data} />}
    </>
  );
}
