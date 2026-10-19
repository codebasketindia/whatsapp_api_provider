import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download, MessageSquare } from 'lucide-react';
import toast from 'react-hot-toast';
import * as messagesApi from '../../../../api/endpoints/messages';
import { getErrorMessage } from '../../../../api/axios';
import { useUrlState } from '../../../../hooks/useUrlState';
import { MESSAGE_STATUSES, PRICE_CATEGORIES } from '../../../../lib/constants';
import { addDaysToDateParam, formatPhone, humanize, toIstDateParam } from '../../../../lib/format';
import {
  Badge, Button, DataTable, DateRangePicker, DateTime, Drawer, EmptyState, FilterBar, Input, Money, PageHeader, SearchInput, Select, StatusBadge,
} from '../../../../components/ui';
import MessageDetail from '../components/MessageDetail';

const daysBetween = (a, b) => Math.round((new Date(b) - new Date(a)) / 86400000);

export default function MessageLogsPage() {
  const today = toIstDateParam();
  const [state, update] = useUrlState({ page: 0, search: '', status: '', category: '', direction: '', partnerId: '', senderId: '', from: addDaysToDateParam(today, -6), to: today });
  const [selected, setSelected] = useState(null);
  const [exporting, setExporting] = useState(false);
  const q = useQuery({ queryKey: ['admin', 'messages', state], queryFn: () => messagesApi.listMessages({ ...state, size: 25 }), placeholderData: (p) => p, refetchInterval: 30_000 });
  const tooLong = state.from && state.to && daysBetween(state.from, state.to) > 30;
  const exportCsv = async () => {
    setExporting(true);
    try {
      const { page, ...filters } = state;
      await messagesApi.exportMessages({ ...filters, format: 'csv' });
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setExporting(false);
    }
  };
  const columns = [
    { key: 'createdAt', header: 'Time', render: (m) => <DateTime value={m.createdAt} /> },
    {
      key: 'to',
      header: 'Contact',
      primary: true,
      render: (m) => (
        <span>
          <span className="flex items-center gap-2 font-medium text-ink">
            {m.direction === 'INBOUND' ? <Badge tone="blue">In</Badge> : <Badge tone="brand">Out</Badge>}
            {formatPhone(m.to || m.from || m.waId)}
          </span>
          <span className="block max-w-xs truncate text-xs text-muted">{m.template ? `Template: ${m.template.name}` : m.preview || humanize(m.type)}</span>
        </span>
      ),
    },
    { key: 'partner', header: 'Partner', render: (m) => m.partner?.businessName || '—' },
    { key: 'sender', header: 'Sender', hideOnMobile: true, render: (m) => m.sender?.displayPhoneNumber },
    { key: 'category', header: 'Category', hideOnMobile: true, render: (m) => (m.category ? humanize(m.category) : '—') },
    { key: 'price', header: 'Price', align: 'right', hideOnMobile: true, render: (m) => <Money value={m.price} precise /> },
    { key: 'status', header: 'Status', render: (m) => <span title={m.errorMessage || ''}><StatusBadge status={m.status} /></span> },
  ];
  return (
    <>
      <PageHeader
        title="Message logs"
        subtitle="Every message sent and received on the platform. Updates every 30 seconds."
        actions={<Button variant="secondary" leftIcon={Download} loading={exporting} disabled={tooLong} onClick={exportCsv}>Export CSV</Button>}
      />
      <FilterBar>
        <SearchInput className="sm:flex-[2]" value={state.search} onSearch={(v) => update({ search: v })} placeholder="Phone, wamid or client reference" />
        <Select aria-label="Status" value={state.status} onChange={(e) => update({ status: e.target.value })} placeholder="All statuses" options={MESSAGE_STATUSES.map((s) => ({ value: s, label: humanize(s) }))} />
        <Select aria-label="Category" value={state.category} onChange={(e) => update({ category: e.target.value })} placeholder="All categories" options={PRICE_CATEGORIES.map((s) => ({ value: s, label: humanize(s) }))} />
        <Select aria-label="Direction" value={state.direction} onChange={(e) => update({ direction: e.target.value })} placeholder="Sent and received" options={[{ value: 'OUTBOUND', label: 'Sent' }, { value: 'INBOUND', label: 'Received' }]} />
        <Input aria-label="Partner id" placeholder="Partner id" inputMode="numeric" value={state.partnerId} onChange={(e) => update({ partnerId: e.target.value.replace(/\D/g, '') })} />
        <Input aria-label="Sender id" placeholder="Sender id" inputMode="numeric" value={state.senderId} onChange={(e) => update({ senderId: e.target.value.replace(/\D/g, '') })} />
      </FilterBar>
      <DateRangePicker className="mb-2" value={{ from: state.from, to: state.to }} onChange={(r) => update(r)} />
      {tooLong && <p className="mb-2 text-sm text-amber-700 dark:text-amber-300">Export is limited to 31 days. Choose a shorter range to export.</p>}
      <DataTable
        className="mt-2"
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        rowKey={(m) => m.uid}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(m) => setSelected(m.uid)}
        empty={<EmptyState icon={MessageSquare} title="No messages in this period" text="Try a wider date range or clear the filters." />}
      />
      <Drawer open={Boolean(selected)} onClose={() => setSelected(null)} title="Message details" width="md:w-[560px]">
        {selected && <MessageDetail uid={selected} />}
      </Drawer>
    </>
  );
}
