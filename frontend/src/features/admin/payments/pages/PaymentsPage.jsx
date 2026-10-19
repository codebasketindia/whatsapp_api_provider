import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CreditCard, RefreshCw } from 'lucide-react';
import * as paymentsApi from '../../../../api/endpoints/payments';
import PermissionGate from '../../../../auth/PermissionGate';
import { useUrlState } from '../../../../hooks/useUrlState';
import { humanize } from '../../../../lib/format';
import {
  Button, ConfirmDialog, DataTable, DateRangePicker, DateTime, EmptyState, FilterBar, Money, PageHeader, SearchInput, Select, StatusBadge,
} from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';
import { useState } from 'react';

export const PAYMENT_STATUSES = ['CREATED', 'ATTEMPTED', 'PAID', 'FAILED', 'EXPIRED'];

export default function PaymentsPage() {
  const navigate = useNavigate();
  const [state, update] = useUrlState({ page: 0, status: '', search: '', from: '', to: '' });
  const [confirmAll, setConfirmAll] = useState(false);
  const q = useQuery({
    queryKey: ['admin', 'payments', state],
    queryFn: () => paymentsApi.listPayments({ ...state, size: 20 }),
    placeholderData: (p) => p,
  });
  const reconcileAll = useApiMutation({
    mutationFn: paymentsApi.reconcilePendingPayments,
    success: (r) => `Checked ${r?.checked ?? 0} orders: ${r?.paid ?? 0} paid, ${r?.failed ?? 0} failed, ${r?.expired ?? 0} expired.`,
    invalidate: [['admin', 'payments']],
    onSuccess: () => setConfirmAll(false),
  });
  const columns = [
    {
      key: 'partner',
      header: 'Partner',
      primary: true,
      render: (o) => (
        <span>
          <span className="block font-medium text-ink">{o.partner?.businessName || '—'}</span>
          <span className="block font-mono text-xs text-muted">{o.gatewayOrderId || o.uid}</span>
        </span>
      ),
    },
    { key: 'totalAmount', header: 'Paid', align: 'right', render: (o) => <Money value={o.totalAmount} className="font-semibold" /> },
    { key: 'creditAmount', header: 'Wallet credit', align: 'right', hideOnMobile: true, render: (o) => <Money value={o.creditAmount} /> },
    { key: 'gst', header: 'GST', align: 'right', hideOnMobile: true, render: (o) => <Money value={o.gstAmount} /> },
    { key: 'method', header: 'Method', hideOnMobile: true, render: (o) => (o.method ? humanize(o.method) : '—') },
    { key: 'status', header: 'Status', render: (o) => <StatusBadge status={o.status} /> },
    { key: 'createdAt', header: 'Created', hideOnMobile: true, render: (o) => <DateTime value={o.createdAt} /> },
  ];
  return (
    <>
      <PageHeader
        title="Payments"
        subtitle="Online (UPI / card) payments through the payment gateway."
        actions={
          <>
            <Button variant="secondary" to="/admin/invoices">Invoices</Button>
            <PermissionGate permission="PAYMENT_RECONCILE">
              <Button variant="secondary" leftIcon={RefreshCw} onClick={() => setConfirmAll(true)}>Check pending payments</Button>
            </PermissionGate>
          </>
        }
      />
      <FilterBar>
        <SearchInput className="sm:flex-[2]" value={state.search} onSearch={(v) => update({ search: v })} placeholder="Search order id, payment id or partner" />
        <Select
          aria-label="Status"
          value={state.status}
          onChange={(e) => update({ status: e.target.value })}
          placeholder="All statuses"
          options={PAYMENT_STATUSES.map((s) => ({ value: s, label: humanize(s) }))}
        />
      </FilterBar>
      <DateRangePicker className="mb-4" value={{ from: state.from, to: state.to }} onChange={(r) => update(r)} allowClear />
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        rowKey={(o) => o.uid}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(o) => navigate(`/admin/payments/${o.uid}`)}
        empty={<EmptyState icon={CreditCard} title="No payments found" text="Online payments made by partners will show here." />}
      />
      <ConfirmDialog
        open={confirmAll}
        onClose={() => setConfirmAll(false)}
        title="Check pending payments now?"
        message="We will ask the payment gateway about every unfinished order older than 15 minutes. Paid orders are credited once; nothing is credited twice."
        confirmLabel="Check now"
        loading={reconcileAll.isPending}
        onConfirm={() => reconcileAll.mutate()}
      />
    </>
  );
}
