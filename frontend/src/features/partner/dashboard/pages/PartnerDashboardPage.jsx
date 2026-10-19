import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Area, AreaChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { CheckCheck, Eye, Megaphone, Plus, Send, Smartphone, Wallet, XCircle } from 'lucide-react';
import * as dashboardApi from '../../../../api/endpoints/dashboard';
import { useAuth } from '../../../../auth/useAuth';
import { Alert, Button, Card, EmptyState, ErrorState, PageHeader, Skeleton, StatCard, StatusBadge } from '../../../../components/ui';
import QualityBadge from '../../../../components/whatsapp/QualityBadge';
import { addDaysToDateParam, formatMoney, formatNumber, formatPercent, toIstDateParam } from '../../../../lib/format';

function shortDate(d) {
  const [, m, day] = String(d).split('-');
  return `${Number(day)}/${Number(m)}`;
}

export default function PartnerDashboardPage() {
  const { user } = useAuth();
  const [days, setDays] = useState(7);
  const range = useMemo(() => {
    const to = toIstDateParam();
    return { from: addDaysToDateParam(to, -(days - 1)), to };
  }, [days]);
  const q = useQuery({ queryKey: ['partner', 'dashboard'], queryFn: dashboardApi.getPartnerDashboard, refetchInterval: 60_000 });
  const chart = useQuery({
    queryKey: ['partner', 'dashboard', 'chart', range],
    queryFn: () => dashboardApi.getPartnerDashboardChart(range),
    placeholderData: (p) => p,
  });

  const d = q.data;
  const today = d?.today || {};
  const name = user?.partner?.businessName || user?.name || '';

  return (
    <>
      <PageHeader title={`Hello${name ? `, ${name}` : ''}`} subtitle="Here is how your WhatsApp messaging is doing today." />
      {q.isError && <ErrorState error={q.error} onRetry={q.refetch} />}

      {d?.lowBalance && (
        <Alert variant="warning" className="mb-4" title="Your balance is low" action={<Button size="sm" to="/partner/wallet/add">Add money</Button>}>
          Messages stop when the balance runs out. Add money to keep sending.
        </Alert>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <section className="card overflow-hidden lg:col-span-1">
          <div className="bg-gradient-to-br from-brand-600 to-brand-800 p-5 text-white">
            <p className="flex items-center gap-2 text-sm text-white/80">
              <Wallet className="h-4 w-4" aria-hidden /> Wallet balance
            </p>
            {q.isLoading ? (
              <Skeleton className="mt-2 h-10 w-40 bg-white/30" />
            ) : (
              <p className="mt-1 text-4xl font-bold tabular-nums">{formatMoney(d?.wallet?.balance)}</p>
            )}
            {d?.wallet && Number(d.wallet.held) > 0 && (
              <p className="mt-1 text-sm text-white/80">{formatMoney(d.wallet.held)} on hold for messages in progress</p>
            )}
            <div className="mt-4 flex gap-2">
              <Button to="/partner/wallet/add" className="bg-white text-brand-800 hover:bg-white/90" leftIcon={Plus}>
                Add money
              </Button>
              <Button to="/partner/wallet" variant="ghost" className="text-white hover:bg-white/15">
                Passbook
              </Button>
            </div>
          </div>
        </section>

        <div className="grid grid-cols-2 gap-3 lg:col-span-2">
          <StatCard label="Sent today" icon={Send} value={formatNumber(today.sent)} loading={q.isLoading} />
          <StatCard label="Delivered today" icon={CheckCheck} tone="green" value={formatNumber(today.delivered)} loading={q.isLoading} />
          <StatCard label="Read today" icon={Eye} tone="blue" value={formatNumber(today.read)} loading={q.isLoading} />
          <StatCard label="Failed today" icon={XCircle} tone="red" value={formatNumber(today.failed)} loading={q.isLoading} />
          <StatCard
            label="Spent today"
            icon={Wallet}
            tone="amber"
            value={formatMoney(today.spend)}
            loading={q.isLoading}
          />
          <StatCard
            label="Delivery rate (7 days)"
            icon={CheckCheck}
            tone="green"
            value={d?.deliveryRate7d === null || d?.deliveryRate7d === undefined ? '—' : formatPercent(d.deliveryRate7d)}
            hint="Delivered or read out of all sent"
            loading={q.isLoading}
          />
        </div>
      </div>

      <div className="mt-5 grid gap-5 lg:grid-cols-3">
        <Card
          className="lg:col-span-2"
          title="Messages"
          actions={
            <div className="flex gap-1">
              {[7, 30].map((n) => (
                <Button key={n} size="sm" variant={days === n ? 'primary' : 'secondary'} onClick={() => setDays(n)}>
                  {n} days
                </Button>
              ))}
            </div>
          }
        >
          {chart.isError ? (
            <ErrorState error={chart.error} onRetry={chart.refetch} />
          ) : chart.isLoading ? (
            <Skeleton className="h-64 w-full" />
          ) : !chart.data?.length ? (
            <EmptyState compact icon={Send} title="No messages yet" text="Your daily message chart will appear here." />
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chart.data} margin={{ left: -20, right: 8, top: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--line))" />
                  <XAxis dataKey="date" tickFormatter={shortDate} fontSize={12} />
                  <YAxis allowDecimals={false} fontSize={12} />
                  <Tooltip labelFormatter={(l) => `Date: ${l}`} />
                  <Legend />
                  <Area type="monotone" dataKey="sent" name="Sent" stroke="#64748b" fill="#64748b33" />
                  <Area type="monotone" dataKey="delivered" name="Delivered" stroke="#25D366" fill="#25D36633" />
                  <Area type="monotone" dataKey="read" name="Read" stroke="#0ea5e9" fill="#0ea5e933" />
                  <Area type="monotone" dataKey="failed" name="Failed" stroke="#dc2626" fill="#dc262622" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        <Card title="My numbers" actions={<Button size="sm" variant="link" to="/partner/senders">View all</Button>}>
          {q.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : !d?.senders?.length ? (
            <EmptyState
              compact
              icon={Smartphone}
              title="No number yet"
              text="Connect your own WhatsApp number or ask us for one."
              action={<Button size="sm" to="/partner/senders">Set up a number</Button>}
            />
          ) : (
            <ul className="divide-y divide-line">
              {d.senders.map((s) => (
                <li key={s.id}>
                  <Link to={`/partner/senders/${s.id}`} className="flex min-h-tap items-center justify-between gap-2 py-2 hover:text-brand-700">
                    <span className="min-w-0 truncate text-sm font-medium text-ink">{s.display}</span>
                    <span className="flex shrink-0 flex-col items-end gap-1">
                      <StatusBadge status={s.status} />
                      <QualityBadge rating={s.quality} />
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Button to="/partner/send" variant="secondary" leftIcon={Send} size="lg">Send message</Button>
        <Button to="/partner/campaigns/new" variant="secondary" leftIcon={Megaphone} size="lg">New campaign</Button>
        <Button to="/partner/templates/new" variant="secondary" size="lg">New template</Button>
        <Button to="/partner/inbox" variant="secondary" size="lg">Open inbox</Button>
      </div>
    </>
  );
}
