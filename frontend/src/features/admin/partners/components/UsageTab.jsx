import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import * as partnersApi from '../../../../api/endpoints/partners';
import { addDaysToDateParam, formatMoney, formatNumber, humanize, toIstDateParam, toNumber } from '../../../../lib/format';
import { Card, DataTable, DateRangePicker, EmptyState, Money, Select } from '../../../../components/ui';
import ChartCard from '../../shared/ChartCard';

const GROUPS = [
  { value: 'day', label: 'By day' },
  { value: 'category', label: 'By category' },
  { value: 'country', label: 'By country' },
  { value: 'sender', label: 'By sender number' },
];

export default function UsageTab({ partner }) {
  const today = toIstDateParam();
  const [range, setRange] = useState({ from: addDaysToDateParam(today, -29), to: today });
  const [groupBy, setGroupBy] = useState('day');
  const q = useQuery({
    queryKey: ['admin', 'partners', 'usage', partner.id, range, groupBy],
    queryFn: () => partnersApi.getPartnerUsage(partner.id, { ...range, groupBy }),
    placeholderData: (p) => p,
  });
  const rows = (q.data || []).map((r) => ({ ...r, charged: toNumber(r.charged) }));
  const totals = rows.reduce(
    (t, r) => ({ sent: t.sent + toNumber(r.sent), delivered: t.delivered + toNumber(r.delivered), read: t.read + toNumber(r.read), failed: t.failed + toNumber(r.failed), charged: t.charged + r.charged }),
    { sent: 0, delivered: 0, read: 0, failed: 0, charged: 0 },
  );
  const label = groupBy === 'category' || groupBy === 'sender' ? (k) => humanize(k) : (k) => k;
  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <DateRangePicker value={range} onChange={setRange} />
        <Select aria-label="Group by" value={groupBy} onChange={(e) => setGroupBy(e.target.value)} options={GROUPS} className="md:w-56" />
      </div>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        {[
          ['Sent', formatNumber(totals.sent)], ['Delivered', formatNumber(totals.delivered)], ['Read', formatNumber(totals.read)],
          ['Failed', formatNumber(totals.failed)], ['Charged', formatMoney(totals.charged)],
        ].map(([l, v]) => (
          <div key={l} className="card p-3"><p className="text-xs text-muted">{l}</p><p className="mt-1 text-lg font-semibold text-ink">{v}</p></div>
        ))}
      </div>
      {groupBy === 'day' && (
        <ChartCard
          title="Messages per day"
          loading={q.isLoading}
          data={rows.map((r) => ({ ...r, date: r.key }))}
          series={[{ key: 'sent', label: 'Sent' }, { key: 'delivered', label: 'Delivered' }, { key: 'read', label: 'Read' }, { key: 'failed', label: 'Failed' }]}
        />
      )}
      <Card padded={false}>
        <DataTable
          columns={[
            { key: 'key', header: GROUPS.find((g) => g.value === groupBy)?.label.replace('By ', '').replace(/^\w/, (c) => c.toUpperCase()), primary: true, render: (r) => label(r.key) },
            { key: 'sent', header: 'Sent', align: 'right', render: (r) => formatNumber(r.sent) },
            { key: 'delivered', header: 'Delivered', align: 'right', render: (r) => formatNumber(r.delivered) },
            { key: 'read', header: 'Read', align: 'right', render: (r) => formatNumber(r.read) },
            { key: 'failed', header: 'Failed', align: 'right', render: (r) => formatNumber(r.failed) },
            { key: 'charged', header: 'Charged', align: 'right', render: (r) => <Money value={r.charged} /> },
          ]}
          rows={rows}
          rowKey={(r) => r.key}
          loading={q.isLoading}
          error={q.error}
          onRetry={q.refetch}
          empty={<EmptyState compact title="No messages in this period" />}
        />
      </Card>
    </div>
  );
}
