import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Download } from 'lucide-react';
import toast from 'react-hot-toast';
import * as messagesApi from '../../../../api/endpoints/messages';
import { myUsageReport } from '../../../../api/endpoints/dashboard';
import { getErrorMessage } from '../../../../api/axios';
import {
  Alert, Button, Card, DataTable, DateRangePicker, DateTime, Drawer, EmptyState, ErrorState, FilterBar, KeyValue, Money, PageHeader,
  SearchInput, Select, Skeleton, StatusBadge, Tabs, useActiveTab,
} from '../../../../components/ui';
import StatusTicks from '../../../../components/whatsapp/StatusTicks';
import { useUrlState } from '../../../../hooks/useUrlState';
import { MESSAGE_STATUSES, PRICE_CATEGORIES } from '../../../../lib/constants';
import { addDaysToDateParam, formatDateTime, formatMoney, formatNumber, formatPhone, humanize, toIstDateParam, toNumber } from '../../../../lib/format';
import { senderLabel, useMySenders } from '../../common/useSenders';

const TABS = [
  { value: 'logs', label: 'Message logs' },
  { value: 'usage', label: 'Usage summary' },
];

function MessageDrawer({ uid, onClose }) {
  const q = useQuery({ queryKey: ['partner', 'messages', 'detail', uid], queryFn: () => messagesApi.getMyMessage(uid), enabled: Boolean(uid) });
  const m = q.data;
  return (
    <Drawer open={Boolean(uid)} onClose={onClose} title="Message details">
      {q.isLoading ? (
        <Skeleton className="h-60 w-full" />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={q.refetch} />
      ) : m ? (
        <div className="space-y-5">
          {m.status === 'FAILED' && <Alert variant="error" title="Not delivered">{m.errorMessage || 'WhatsApp could not deliver this message.'}{m.errorCode ? ` (code ${m.errorCode})` : ''}</Alert>}
          <KeyValue
            items={[
              { label: 'Status', value: <StatusTicks status={m.status} /> },
              { label: 'Direction', value: m.direction === 'INBOUND' ? 'Received' : 'Sent' },
              { label: m.direction === 'INBOUND' ? 'From' : 'To', value: formatPhone(m.to || m.from) },
              { label: 'Our number', value: m.sender?.displayPhoneNumber },
              { label: 'Type', value: humanize(m.type) },
              { label: 'Category', value: humanize(m.category) },
              { label: 'Template', value: m.template?.name, hidden: !m.template },
              { label: 'Price', value: <Money value={m.price} precise /> },
              { label: 'Charge', value: <StatusBadge status={m.chargeStatus} /> },
              { label: 'Your reference', value: m.clientReference, hidden: !m.clientReference },
              { label: 'Source', value: humanize(m.source) },
              { label: 'Campaign', value: m.campaign?.name, hidden: !m.campaign },
              { label: 'Message ID', value: <span className="break-all font-mono text-xs">{m.uid}</span> },
              { label: 'WhatsApp ID', value: <span className="break-all font-mono text-xs">{m.wamid || '—'}</span> },
            ]}
            columns={1}
          />
          {m.preview && (
            <div>
              <p className="label-base">Content</p>
              <p className="whitespace-pre-wrap rounded-lg bg-surface-2 p-3 text-sm">{m.preview}</p>
            </div>
          )}
          <div>
            <p className="label-base">Timeline</p>
            <ol className="space-y-2 border-l-2 border-line pl-4">
              <li className="text-sm"><span className="font-medium">Created</span> · {formatDateTime(m.createdAt)}</li>
              {(m.statusHistory || []).map((h, i) => (
                <li key={i} className="text-sm">
                  <span className="font-medium">{humanize(h.status)}</span> · {formatDateTime(h.at)}
                  {h.errorCode ? <span className="text-red-600"> (code {h.errorCode})</span> : null}
                </li>
              ))}
            </ol>
          </div>
        </div>
      ) : null}
    </Drawer>
  );
}

function Logs() {
  const senders = useMySenders();
  const [f, update] = useUrlState({ page: 0, direction: '', status: '', senderId: '', category: '', search: '', from: '', to: '' });
  const params = { page: f.page, size: 20, direction: f.direction, status: f.status, senderId: f.senderId, category: f.category, search: f.search, from: f.from, to: f.to };
  const q = useQuery({ queryKey: ['partner', 'messages', params], queryFn: () => messagesApi.listMyMessages(params), placeholderData: (p) => p, refetchInterval: 30_000 });
  const [uid, setUid] = useState(null);
  const [exporting, setExporting] = useState(null);
  const doExport = async (format) => {
    const to = f.to || toIstDateParam();
    const from = f.from || addDaysToDateParam(to, -30);
    setExporting(format);
    try {
      await messagesApi.exportMyMessages({ ...params, page: undefined, size: undefined, from, to, format });
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setExporting(null);
    }
  };
  const columns = [
    { key: 'createdAt', header: 'Time', render: (r) => <DateTime value={r.createdAt} /> },
    {
      key: 'who',
      header: 'Customer',
      primary: true,
      render: (r) => (
        <div>
          <p className="font-medium text-ink">{r.contact?.name || formatPhone(r.to || r.from)}</p>
          <p className="max-w-xs truncate text-xs text-muted">{r.direction === 'INBOUND' ? '↙ ' : '↗ '}{r.template?.name || r.preview || humanize(r.type)}</p>
        </div>
      ),
    },
    { key: 'category', header: 'Category', hideOnMobile: true, render: (r) => humanize(r.category) },
    { key: 'status', header: 'Status', render: (r) => <StatusTicks status={r.status} /> },
    { key: 'price', header: 'Price', align: 'right', render: (r) => (toNumber(r.price) ? <Money value={r.price} precise /> : <span className="text-muted">Free</span>) },
  ];
  return (
    <>
      <FilterBar>
        <SearchInput value={f.search} onSearch={(v) => update({ search: v })} placeholder="Number, message ID or reference" />
        <Select aria-label="Status" value={f.status} onChange={(e) => update({ status: e.target.value })} options={MESSAGE_STATUSES.map((s) => ({ value: s, label: humanize(s) }))} placeholder="All statuses" />
        <Select aria-label="Direction" value={f.direction} onChange={(e) => update({ direction: e.target.value })} options={[{ value: 'OUTBOUND', label: 'Sent' }, { value: 'INBOUND', label: 'Received' }]} placeholder="Sent & received" />
        <Select aria-label="Category" value={f.category} onChange={(e) => update({ category: e.target.value })} options={PRICE_CATEGORIES.map((c) => ({ value: c, label: humanize(c) }))} placeholder="All categories" />
        {(senders.data || []).length > 1 && (
          <Select aria-label="Number" value={f.senderId} onChange={(e) => update({ senderId: e.target.value })} options={(senders.data || []).map((s) => ({ value: s.id, label: senderLabel(s) }))} placeholder="All numbers" />
        )}
        <DateRangePicker value={{ from: f.from, to: f.to }} onChange={(v) => update(v)} allowClear />
      </FilterBar>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-muted">Exports cover up to 31 days.</p>
        <div className="flex gap-2">
          <Button size="sm" variant="secondary" leftIcon={Download} loading={exporting === 'csv'} onClick={() => doExport('csv')}>CSV</Button>
          <Button size="sm" variant="secondary" leftIcon={Download} loading={exporting === 'xlsx'} onClick={() => doExport('xlsx')}>Excel</Button>
        </div>
      </div>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        rowKey={(r) => r.uid}
        onRowClick={(r) => setUid(r.uid)}
        empty={<EmptyState compact title="No messages found" text="Try another date range or filter." />}
      />
      <MessageDrawer uid={uid} onClose={() => setUid(null)} />
    </>
  );
}

const GROUP_BY = [
  { value: 'day', label: 'By day' },
  { value: 'category', label: 'By category' },
  { value: 'country', label: 'By country' },
  { value: 'sender', label: 'By number' },
];

function Usage() {
  const [range, setRange] = useState(() => {
    const to = toIstDateParam();
    return { from: addDaysToDateParam(to, -29), to };
  });
  const [groupBy, setGroupBy] = useState('day');
  const params = { ...range, groupBy };
  const q = useQuery({ queryKey: ['partner', 'reports', 'usage', params], queryFn: () => myUsageReport.get(params), placeholderData: (p) => p });
  const rows = Array.isArray(q.data) ? q.data : q.data?.content || [];
  const totals = rows.reduce(
    (a, r) => ({ sent: a.sent + toNumber(r.sent), delivered: a.delivered + toNumber(r.delivered), read: a.read + toNumber(r.read), failed: a.failed + toNumber(r.failed), charged: a.charged + toNumber(r.charged) }),
    { sent: 0, delivered: 0, read: 0, failed: 0, charged: 0 },
  );
  const label = (k) => (groupBy === 'category' ? humanize(k) : k);
  const download = (format) => myUsageReport.download(params, format).catch((e) => toast.error(getErrorMessage(e)));
  return (
    <div className="space-y-5">
      <Card>
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_220px]">
          <DateRangePicker value={range} onChange={setRange} />
          <Select label="Group" value={groupBy} onChange={(e) => setGroupBy(e.target.value)} options={GROUP_BY} />
        </div>
      </Card>
      <Card
        title="Summary"
        subtitle={`${formatNumber(totals.sent + totals.delivered + totals.read + totals.failed)} messages · ${formatMoney(totals.charged)} charged`}
        actions={
          <>
            <Button size="sm" variant="secondary" leftIcon={Download} onClick={() => download('csv')}>CSV</Button>
            <Button size="sm" variant="secondary" leftIcon={Download} onClick={() => download('xlsx')}>Excel</Button>
          </>
        }
      >
        {q.isError ? (
          <ErrorState error={q.error} onRetry={q.refetch} />
        ) : q.isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : !rows.length ? (
          <EmptyState compact title="No usage in this period" />
        ) : (
          <>
            <div className="mb-5 h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={rows.map((r) => ({ ...r, label: label(r.key) }))} margin={{ left: -20, right: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--line))" />
                  <XAxis dataKey="label" fontSize={12} />
                  <YAxis allowDecimals={false} fontSize={12} />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="delivered" name="Delivered" stackId="a" fill="#25D366" />
                  <Bar dataKey="read" name="Read" stackId="a" fill="#0ea5e9" />
                  <Bar dataKey="sent" name="Sent" stackId="a" fill="#94a3b8" />
                  <Bar dataKey="failed" name="Failed" stackId="a" fill="#dc2626" />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <DataTable
              rows={rows}
              rowKey={(r) => r.key}
              columns={[
                { key: 'key', header: GROUP_BY.find((g) => g.value === groupBy).label.replace('By ', ''), primary: true, render: (r) => label(r.key) },
                { key: 'sent', header: 'Sent', align: 'right', render: (r) => formatNumber(r.sent) },
                { key: 'delivered', header: 'Delivered', align: 'right', render: (r) => formatNumber(r.delivered) },
                { key: 'read', header: 'Read', align: 'right', render: (r) => formatNumber(r.read) },
                { key: 'failed', header: 'Failed', align: 'right', render: (r) => formatNumber(r.failed) },
                { key: 'charged', header: 'Charged', align: 'right', render: (r) => <Money value={r.charged} /> },
              ]}
            />
          </>
        )}
      </Card>
    </div>
  );
}

export default function MyReportsPage() {
  const tab = useActiveTab(TABS);
  return (
    <>
      <PageHeader title="Reports" subtitle="Every message you sent or received, with status and cost" />
      <Tabs tabs={TABS} urlParam="tab" className="mb-4" />
      {tab === 'logs' ? <Logs /> : <Usage />}
    </>
  );
}
