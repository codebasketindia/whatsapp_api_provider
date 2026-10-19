import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowDownLeft, ArrowUpRight, ReceiptText } from 'lucide-react';
import * as walletApi from '../../../../api/endpoints/wallet';
import { useAuth } from '../../../../auth/useAuth';
import { WALLET_TXN_TYPES } from '../../../../lib/constants';
import { humanize } from '../../../../lib/format';
import {
  Badge, Button, Card, Checkbox, DataTable, DateRangePicker, DateTime, EmptyState, FilterBar, Money, Select, Skeleton, StatusBadge,
} from '../../../../components/ui';
import AdjustWalletDialog from './AdjustWalletDialog';

const CREDIT_TYPES = ['CREDIT', 'RELEASE', 'REFUND'];

export function walletTxnColumns() {
  return [
    { key: 'createdAt', header: 'Date', render: (t) => <DateTime value={t.createdAt} /> },
    {
      key: 'type',
      header: 'Entry',
      primary: true,
      render: (t) => (
        <span className="min-w-0">
          <span className="flex items-center gap-2">
            <StatusBadge status={t.type} />
            <span className="text-sm text-ink">{humanize(t.referenceType)}</span>
          </span>
          {t.remarks && <span className="mt-0.5 block max-w-md truncate text-xs text-muted">{t.remarks}</span>}
        </span>
      ),
    },
    {
      key: 'amount',
      header: 'Amount',
      align: 'right',
      render: (t) =>
        t.type === 'HOLD' ? (
          <span className="text-amber-700 dark:text-amber-300">−<Money value={t.amount} precise /> <span className="text-xs">(held)</span></span>
        ) : t.type === 'DEBIT' && Number(t.balanceBefore) === Number(t.balanceAfter) ? (
          <span className="text-muted"><Money value={t.amount} precise /> <span className="text-xs">(charged from hold)</span></span>
        ) : (
          <Money value={t.amount} precise signed negative={!CREDIT_TYPES.includes(t.type)} />
        ),
    },
    { key: 'balanceAfter', header: 'Balance after', align: 'right', render: (t) => <Money value={t.balanceAfter} precise /> },
    { key: 'heldAfter', header: 'On hold after', align: 'right', hideOnMobile: true, render: (t) => <Money value={t.heldAfter} precise /> },
    { key: 'ref', header: 'Reference', hideOnMobile: true, render: (t) => <span className="font-mono text-xs text-muted">{t.referenceId || t.uid}</span> },
    { key: 'by', header: 'By', hideOnMobile: true, render: (t) => t.createdBy?.name || <Badge tone="gray">System</Badge> },
  ];
}

export default function WalletTab({ partner }) {
  const { hasPermission } = useAuth();
  const [filters, setFilters] = useState({ page: 0, type: '', from: '', to: '', includeMessageEntries: false });
  const [adjust, setAdjust] = useState(null); // 'CREDIT' | 'DEBIT'
  const walletQ = useQuery({ queryKey: ['admin', 'partners', 'wallet', partner.id], queryFn: () => walletApi.getPartnerWallet(partner.id) });
  const txQ = useQuery({
    queryKey: ['admin', 'partners', 'wallet-txns', partner.id, filters],
    queryFn: () => walletApi.listPartnerWalletTransactions(partner.id, { ...filters, size: 20 }),
    placeholderData: (p) => p,
  });
  const set = (patch) => setFilters((f) => ({ ...f, page: 0, ...patch }));
  const w = walletQ.data;
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        <div className="card p-4">
          <p className="text-sm text-muted">Available balance</p>
          {walletQ.isLoading ? <Skeleton className="mt-1 h-8 w-32" /> : <p className="mt-1 text-2xl font-semibold text-ink"><Money value={w?.balance} /></p>}
          {w?.isLow && <Badge tone="red" className="mt-2">Low balance</Badge>}
        </div>
        <div className="card p-4">
          <p className="text-sm text-muted">On hold (messages in progress)</p>
          {walletQ.isLoading ? <Skeleton className="mt-1 h-8 w-32" /> : <p className="mt-1 text-2xl font-semibold text-ink"><Money value={w?.held} /></p>}
          <p className="mt-1 text-xs text-muted">Alert below <Money value={w?.lowBalanceThreshold} /></p>
        </div>
        {hasPermission('WALLET_ADJUST') && (
          <div className="card flex flex-col justify-center gap-2 p-4">
            <Button leftIcon={ArrowDownLeft} onClick={() => setAdjust('CREDIT')}>Add credit</Button>
            <Button variant="danger-outline" leftIcon={ArrowUpRight} onClick={() => setAdjust('DEBIT')}>Deduct</Button>
          </div>
        )}
      </div>
      <Card title="Passbook" subtitle="Every money movement, newest first." padded={false} bodyClassName="p-4">
        <FilterBar>
          <Select
            aria-label="Entry type"
            value={filters.type}
            onChange={(e) => set({ type: e.target.value })}
            placeholder="All entries"
            options={WALLET_TXN_TYPES.map((t) => ({ value: t, label: humanize(t) }))}
          />
          <Checkbox
            label="Show per-message entries"
            description="Holds and charges for each message"
            checked={filters.includeMessageEntries}
            onChange={(e) => set({ includeMessageEntries: e.target.checked })}
          />
        </FilterBar>
        <DateRangePicker className="mb-4" value={{ from: filters.from, to: filters.to }} onChange={(r) => set(r)} allowClear />
        <DataTable
          columns={walletTxnColumns()}
          page={txQ.data}
          loading={txQ.isLoading}
          error={txQ.error}
          onRetry={txQ.refetch}
          rowKey={(t) => t.uid}
          onPageChange={(page) => setFilters((f) => ({ ...f, page }))}
          empty={<EmptyState icon={ReceiptText} title="No entries" text="Credits, debits and message charges will appear here." />}
        />
      </Card>
      <AdjustWalletDialog partner={partner} wallet={w} type={adjust} onClose={() => setAdjust(null)} />
    </div>
  );
}
