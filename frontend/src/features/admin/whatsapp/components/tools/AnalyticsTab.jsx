import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { addDaysToDateParam, formatMoney, formatNumber, humanize, toIstDateParam, toNumber } from '../../../../../lib/format';
import { Card, DataTable, DateRangePicker, EmptyState, Money, QueryState } from '../../../../../components/ui';
import ChartCard from '../../../shared/ChartCard';

/** T11: our delivery stats per day + Meta's pricing analytics for the number. */
export default function AnalyticsTab({ sender, tools }) {
  const today = toIstDateParam();
  const [range, setRange] = useState({ from: addDaysToDateParam(today, -29), to: today });
  const q = useQuery({
    queryKey: [tools.scope, 'senders', sender.id, 'analytics', range],
    queryFn: () => tools.getAnalytics(sender.id, { ...range, granularity: 'DAILY' }),
    placeholderData: (p) => p,
  });
  const ours = useMemo(() => (q.data?.ours || []).map((r) => ({ ...r, charged: toNumber(r.charged) })), [q.data]);
  const meta = q.data?.meta || [];
  const metaTotal = meta.reduce((t, r) => ({ volume: t.volume + toNumber(r.volume), cost: t.cost + toNumber(r.cost) }), { volume: 0, cost: 0 });
  return (
    <div className="space-y-4">
      <DateRangePicker value={range} onChange={setRange} />
      <QueryState query={q}>
        {() => (
          <>
            <ChartCard
              title="Messages from this number"
              data={ours}
              series={[{ key: 'sent', label: 'Sent' }, { key: 'delivered', label: 'Delivered' }, { key: 'read', label: 'Read' }, { key: 'failed', label: 'Failed' }]}
            />
            <ChartCard title="Charged to partner" type="bar" data={ours} series={[{ key: 'charged', label: 'Charged (₹)' }]} valueFormatter={(v, axis) => (axis ? `₹${formatNumber(v)}` : formatMoney(v))} />
            <Card title="Meta pricing analytics" subtitle={`${formatNumber(metaTotal.volume)} billable messages · ${formatMoney(metaTotal.cost)} Meta cost`} padded={false} bodyClassName="p-4">
              <DataTable
                columns={[
                  { key: 'date', header: 'Date', primary: true },
                  { key: 'category', header: 'Category', render: (r) => humanize(r.category) },
                  { key: 'country', header: 'Country' },
                  { key: 'volume', header: 'Messages', align: 'right', render: (r) => formatNumber(r.volume) },
                  { key: 'cost', header: 'Meta cost', align: 'right', render: (r) => <Money value={r.cost} precise /> },
                ]}
                rows={meta}
                rowKey={(r) => `${r.date}-${r.category}-${r.country}`}
                empty={<EmptyState compact title="No Meta pricing data" text="Meta publishes pricing analytics with a delay of up to a day." />}
              />
            </Card>
          </>
        )}
      </QueryState>
    </div>
  );
}
