import { Area, AreaChart, Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Card, EmptyState, Skeleton } from '../../../components/ui';
import { BarChart3 } from 'lucide-react';
import { formatDate } from '../../../lib/format';

export const SERIES_COLORS = {
  sent: '#3b82f6',
  delivered: '#25D366',
  read: '#128C7E',
  failed: '#ef4444',
  revenue: '#25D366',
  charged: '#128C7E',
  spend: '#128C7E',
  default: '#6b7280',
};

const shortDate = (d) => {
  if (!d) return '';
  const s = String(d);
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) {
    const [, m, day] = s.split('-');
    return `${Number(day)}/${Number(m)}`;
  }
  return s;
};

/**
 * Time-series chart card. data: [{ date, ...series }], series: [{ key, label, color? }].
 * type: 'area' | 'bar'. valueFormatter formats tooltip values.
 */
export default function ChartCard({ title, subtitle, data = [], series, type = 'area', loading, valueFormatter, xKey = 'date', actions, height = 260 }) {
  const Chart = type === 'bar' ? BarChart : AreaChart;
  return (
    <Card title={title} subtitle={subtitle} actions={actions}>
      {loading ? (
        <Skeleton className="w-full" style={{ height }} />
      ) : data.length === 0 ? (
        <EmptyState compact icon={BarChart3} title="No data for this period" />
      ) : (
        <div style={{ height }} className="w-full">
          <ResponsiveContainer width="100%" height="100%">
            <Chart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="currentColor" className="text-line" opacity={0.4} />
              <XAxis dataKey={xKey} tickFormatter={xKey === 'date' ? shortDate : undefined} tick={{ fontSize: 12 }} stroke="currentColor" className="text-muted" />
              <YAxis tick={{ fontSize: 12 }} width={56} stroke="currentColor" className="text-muted" tickFormatter={(v) => (valueFormatter ? valueFormatter(v, true) : v)} />
              <Tooltip
                formatter={(v) => (valueFormatter ? valueFormatter(v) : v)}
                labelFormatter={(l) => (xKey === 'date' ? formatDate(l) : l)}
                contentStyle={{ borderRadius: 8, fontSize: 13 }}
              />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              {series.map((s) =>
                type === 'bar' ? (
                  <Bar key={s.key} dataKey={s.key} name={s.label} fill={s.color || SERIES_COLORS[s.key] || SERIES_COLORS.default} radius={[4, 4, 0, 0]} />
                ) : (
                  <Area
                    key={s.key}
                    type="monotone"
                    dataKey={s.key}
                    name={s.label}
                    stroke={s.color || SERIES_COLORS[s.key] || SERIES_COLORS.default}
                    fill={s.color || SERIES_COLORS[s.key] || SERIES_COLORS.default}
                    fillOpacity={0.12}
                    strokeWidth={2}
                  />
                ),
              )}
            </Chart>
          </ResponsiveContainer>
        </div>
      )}
    </Card>
  );
}
