import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle, Building2, CheckCheck, Eye, IndianRupee, Megaphone, MessageSquare, Send, Smartphone, Wallet, XCircle,
} from 'lucide-react';
import * as dashboardApi from '../../../../api/endpoints/dashboard';
import { useAuth } from '../../../../auth/useAuth';
import { addDaysToDateParam, formatMoney, formatNumber, formatPercent, toIstDateParam, toNumber } from '../../../../lib/format';
import { Button, Card, DateRangePicker, EmptyState, ErrorState, PageHeader, StatCard } from '../../../../components/ui';
import QualityBadge from '../../../../components/whatsapp/QualityBadge';
import ChartCard from '../../shared/ChartCard';

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const { user, hasPermission } = useAuth();
  const canView = hasPermission('DASHBOARD_VIEW');
  const today = toIstDateParam();
  const [range, setRange] = useState({ from: addDaysToDateParam(today, -13), to: today });
  const q = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: dashboardApi.getAdminDashboard, enabled: canView, refetchInterval: 60_000 });
  const msgQ = useQuery({ queryKey: ['admin', 'dashboard', 'chart', 'messages', range], queryFn: () => dashboardApi.getAdminDashboardChart({ ...range, metric: 'messages' }), enabled: canView, placeholderData: (p) => p });
  const revQ = useQuery({ queryKey: ['admin', 'dashboard', 'chart', 'revenue', range], queryFn: () => dashboardApi.getAdminDashboardChart({ ...range, metric: 'revenue' }), enabled: canView, placeholderData: (p) => p });

  if (!canView) {
    return (
      <>
        <PageHeader title={`Welcome, ${user?.name?.split(' ')[0] || ''}`} />
        <div className="card"><EmptyState title="Use the menu to get started" text="Your role does not include the dashboard. The menu shows the areas you can work on." /></div>
      </>
    );
  }
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const d = q.data || {};
  const t = d.today || {};
  const outbound = toNumber(t.sent) + toNumber(t.delivered) + toNumber(t.read) + toNumber(t.failed);
  const deliveredPct = outbound ? ((toNumber(t.delivered) + toNumber(t.read)) / outbound) * 100 : null;
  const loading = q.isLoading;
  const alerts = d.senderAlerts || [];

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Today at a glance (IST)." />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Partners" icon={Building2} loading={loading} value={formatNumber(d.partners?.total)} hint={`${formatNumber(d.partners?.active)} active`} onClick={() => navigate('/admin/partners')} />
        <StatCard label="Total wallet balance" icon={Wallet} loading={loading} value={formatMoney(d.walletTotal)} hint="All partners" />
        <StatCard label="Revenue today" icon={IndianRupee} tone="green" loading={loading} value={formatMoney(d.revenue?.today)} hint={`This month ${formatMoney(d.revenue?.month)}`} />
        <StatCard label="Pending top-ups" icon={AlertTriangle} tone={toNumber(d.pendingTopups) > 0 ? 'amber' : 'default'} loading={loading} value={formatNumber(d.pendingTopups)} hint="Waiting for approval" onClick={hasPermission('TOPUP_VIEW') ? () => navigate('/admin/topups') : undefined} />
        <StatCard label="Sent today" icon={Send} tone="blue" loading={loading} value={formatNumber(t.sent)} />
        <StatCard label="Delivered today" icon={CheckCheck} tone="green" loading={loading} value={formatNumber(t.delivered)} hint={deliveredPct == null ? undefined : `${formatPercent(deliveredPct)} delivery rate`} />
        <StatCard label="Read today" icon={Eye} loading={loading} value={formatNumber(t.read)} />
        <StatCard label="Failed today" icon={XCircle} tone={toNumber(t.failed) > 0 ? 'red' : 'default'} loading={loading} value={formatNumber(t.failed)} onClick={hasPermission('MESSAGE_LOG_VIEW') ? () => navigate('/admin/messages?status=FAILED') : undefined} />
      </div>

      <div className="my-4">
        <DateRangePicker value={range} onChange={setRange} />
      </div>
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <ChartCard
          title="Messages"
          loading={msgQ.isLoading}
          data={msgQ.data || []}
          series={[{ key: 'sent', label: 'Sent' }, { key: 'delivered', label: 'Delivered' }, { key: 'read', label: 'Read' }, { key: 'failed', label: 'Failed' }]}
        />
        <ChartCard
          title="Revenue"
          type="bar"
          loading={revQ.isLoading}
          data={(revQ.data || []).map((r) => Object.fromEntries(Object.entries(r).map(([k, v]) => [k, k === 'date' ? v : toNumber(v)])))}
          series={revenueSeries(revQ.data)}
          valueFormatter={(v, axis) => (axis ? `₹${formatNumber(v)}` : formatMoney(v))}
        />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card title="Sender health alerts" subtitle="Numbers that need attention." className="lg:col-span-2">
          {loading ? null : alerts.length === 0 ? (
            <EmptyState compact icon={Smartphone} title="All numbers are healthy" />
          ) : (
            <ul className="divide-y divide-line">
              {alerts.map((a, i) => (
                <li key={a.senderId ?? a.id ?? i} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="font-medium text-ink">{a.displayPhoneNumber || a.sender?.displayPhoneNumber || a.title || 'Sender'}</p>
                    <p className="text-sm text-muted">{a.message || a.reason || a.partner?.businessName || ''}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {(a.qualityRating || a.quality) && <QualityBadge rating={a.qualityRating || a.quality} />}
                    {(a.senderId || a.id) && <Button size="sm" variant="secondary" to={`/admin/whatsapp/senders/${a.senderId || a.id}`}>Open</Button>}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card title="Quick links">
          <div className="grid grid-cols-1 gap-2">
            {[
              ['/admin/partners/new', 'Add partner', Building2, 'PARTNER_MANAGE'],
              ['/admin/topups', 'Review top-ups', Wallet, 'TOPUP_VIEW'],
              ['/admin/templates/review', 'Template review', MessageSquare, 'TEMPLATE_REVIEW'],
              ['/admin/campaigns', 'Campaigns', Megaphone, 'CAMPAIGN_VIEW'],
              ['/admin/whatsapp/senders', 'All senders', Smartphone, 'WHATSAPP_VIEW'],
            ]
              .filter(([, , , p]) => hasPermission(p))
              .map(([to, label, Icon]) => (
                <Link key={to} to={to} className="flex min-h-tap items-center gap-3 rounded-lg px-3 text-sm text-ink hover:bg-surface-2">
                  <Icon className="h-4 w-4 text-muted" /> {label}
                </Link>
              ))}
          </div>
        </Card>
      </div>
    </>
  );
}

const REVENUE_LABELS = { revenue: 'Revenue', messageCharges: 'Message charges', platformFees: 'Platform fees', topups: 'Top-ups', gatewayCollections: 'Gateway', manualTopups: 'Manual top-ups' };
function revenueSeries(data) {
  const keys = Object.keys((data || [])[0] || {}).filter((k) => k !== 'date');
  const list = keys.length ? keys : ['revenue'];
  const colors = ['#25D366', '#128C7E', '#3b82f6', '#f59e0b'];
  return list.map((k, i) => ({ key: k, label: REVENUE_LABELS[k] || k, color: colors[i % colors.length] }));
}
