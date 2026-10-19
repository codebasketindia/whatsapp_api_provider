import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { AlertTriangle } from 'lucide-react';
import { Alert, Card, DateRangePicker, EmptyState, ErrorState, InfoTip, KeyValue, PageLoader, Skeleton, StatusBadge } from '../../../../../components/ui';
import QualityBadge from '../../../../../components/whatsapp/QualityBadge';
import { addDaysToDateParam, formatMoney, humanize, toIstDateParam, toNumber } from '../../../../../lib/format';

/** Number health (T10) + usage analytics (T11). */
export default function HealthTab({ tools, sender }) {
  const [range, setRange] = useState(() => {
    const to = toIstDateParam();
    return { from: addDaysToDateParam(to, -13), to };
  });
  const h = useQuery({ queryKey: [tools.scope, 'senders', sender.id, 'health'], queryFn: () => tools.getHealth(sender.id) });
  const a = useQuery({
    queryKey: [tools.scope, 'senders', sender.id, 'analytics', range],
    queryFn: () => tools.getAnalytics(sender.id, { ...range, granularity: 'DAILY' }),
    placeholderData: (p) => p,
  });
  const metaCost = useMemo(() => (a.data?.meta || []).reduce((s, r) => s + toNumber(r.cost), 0), [a.data]);

  if (h.isLoading) return <PageLoader />;
  if (h.isError) return <ErrorState error={h.error} onRetry={h.refetch} />;
  const d = h.data;
  const canSend = d.health?.canSendMessage;
  return (
    <div className="space-y-5">
      {d.alerts?.length > 0 && (
        <div className="space-y-2">
          {d.alerts.map((al, i) => (
            <Alert key={i} variant="warning">{typeof al === 'string' ? al : al.message || al.title}</Alert>
          ))}
        </div>
      )}
      <Card title="Health">
        <KeyValue
          columns={3}
          items={[
            { label: 'Quality', value: <QualityBadge rating={d.quality} /> },
            { label: 'Messaging limit', value: d.tier ? humanize(d.tier) : '—' },
            { label: 'Throughput', value: d.throughput ? humanize(d.throughput) : '—' },
            { label: 'Can send messages', value: <StatusBadge status={canSend === 'AVAILABLE' || canSend === true ? 'ACTIVE' : canSend ? 'LIMITED' : 'UNKNOWN'} label={humanize(String(canSend ?? 'Unknown'))} /> },
            { label: 'Display name review', value: <StatusBadge status={d.nameStatus || 'UNKNOWN'} /> },
            { label: 'Meta access', value: <StatusBadge status={d.tokenStatus || 'UNKNOWN'} /> },
          ]}
        />
        <p className="mt-3 flex flex-wrap items-center gap-3 text-xs text-muted">
          <span className="inline-flex items-center gap-1">What is quality? <InfoTip term="QUALITY_RATING" /></span>
          <span className="inline-flex items-center gap-1">Messaging limit <InfoTip term="MESSAGING_LIMIT" /></span>
          <span className="inline-flex items-center gap-1">Throughput <InfoTip term="THROUGHPUT" /></span>
        </p>
        {d.health?.errors?.length > 0 && (
          <ul className="mt-4 space-y-2">
            {d.health.errors.map((e, i) => (
              <li key={i} className="flex gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-500/10 dark:text-amber-200">
                <AlertTriangle className="h-5 w-5 shrink-0" />
                <span>
                  {e.error_description || e.description || e.message || JSON.stringify(e)}
                  {e.possible_solution && <span className="block text-xs opacity-80">{e.possible_solution}</span>}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card title="Usage" actions={<span className="text-sm text-muted">Meta cost: {formatMoney(metaCost)}</span>}>
        <DateRangePicker value={range} onChange={setRange} className="mb-4" />
        {a.isError ? (
          <ErrorState error={a.error} onRetry={a.refetch} />
        ) : a.isLoading ? (
          <Skeleton className="h-60 w-full" />
        ) : !a.data?.ours?.length ? (
          <EmptyState compact title="No messages in this period" />
        ) : (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={a.data.ours} margin={{ left: -20, right: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--line))" />
                <XAxis dataKey="date" fontSize={12} tickFormatter={(v) => String(v).slice(5)} />
                <YAxis allowDecimals={false} fontSize={12} />
                <Tooltip />
                <Legend />
                <Bar dataKey="delivered" name="Delivered" stackId="a" fill="#25D366" />
                <Bar dataKey="read" name="Read" stackId="a" fill="#0ea5e9" />
                <Bar dataKey="failed" name="Failed" stackId="a" fill="#dc2626" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>
    </div>
  );
}
