import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download, FileSpreadsheet } from 'lucide-react';
import toast from 'react-hot-toast';
import { categoryReport, countryReport, partnerReport, reconciliationReport, revenueReport } from '../../../../api/endpoints/dashboard';
import { getErrorMessage } from '../../../../api/axios';
import { useUrlState } from '../../../../hooks/useUrlState';
import { addDaysToDateParam, formatMoney, formatNumber, humanize, toIstDateParam, toNumber } from '../../../../lib/format';
import { Button, Card, DataTable, DateRangePicker, EmptyState, Money, PageHeader, Select, Tabs } from '../../../../components/ui';
import ChartCard from '../../shared/ChartCard';

const TABS = [
  { value: 'partners', label: 'Partners' },
  { value: 'categories', label: 'Categories' },
  { value: 'countries', label: 'Countries' },
  { value: 'revenue', label: 'Revenue' },
  { value: 'reconciliation', label: 'Meta reconciliation' },
];

const rowsOf = (d) => (Array.isArray(d) ? d : d?.content || d?.rows || []);
const sum = (rows, k) => rows.reduce((t, r) => t + toNumber(r[k]), 0);
const moneyAxis = (v, axis) => (axis ? `₹${formatNumber(v)}` : formatMoney(v));

export default function ReportsPage() {
  const today = toIstDateParam();
  const [state, update] = useUrlState({ tab: 'partners', from: addDaysToDateParam(today, -29), to: today, groupBy: 'day', page: 0 });
  const [downloading, setDownloading] = useState(null);
  const report = { partners: partnerReport, categories: categoryReport, countries: countryReport, revenue: revenueReport, reconciliation: reconciliationReport }[state.tab] || partnerReport;
  const params = { from: state.from, to: state.to, ...(state.tab === 'revenue' ? { groupBy: state.groupBy } : {}), ...(state.tab === 'partners' ? { page: state.page, size: 50 } : {}) };
  const q = useQuery({ queryKey: ['admin', 'reports', state.tab, params], queryFn: () => report.get(params), placeholderData: (p) => p });
  const rows = rowsOf(q.data);
  const download = async (format) => {
    setDownloading(format);
    try {
      const { page, size, ...p } = params;
      await report.download(p, format);
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setDownloading(null);
    }
  };
  const common = { loading: q.isLoading, error: q.error, onRetry: q.refetch, empty: <EmptyState compact title="No data for this period" /> };

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Usage, revenue and Meta cost. Dates are in IST."
        actions={
          <>
            <Button variant="secondary" leftIcon={FileSpreadsheet} loading={downloading === 'xlsx'} onClick={() => download('xlsx')}>Excel</Button>
            <Button variant="secondary" leftIcon={Download} loading={downloading === 'pdf'} onClick={() => download('pdf')}>PDF</Button>
          </>
        }
      />
      <Tabs tabs={TABS} value={state.tab} onChange={(v) => update({ tab: v })} className="mb-4" />
      <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <DateRangePicker value={{ from: state.from, to: state.to }} onChange={(r) => update(r)} />
        {state.tab === 'revenue' && (
          <Select aria-label="Group by" className="md:w-48" value={state.groupBy} onChange={(e) => update({ groupBy: e.target.value })} options={[{ value: 'day', label: 'By day' }, { value: 'month', label: 'By month' }]} />
        )}
      </div>

      {state.tab === 'partners' && (
        <Card padded={false} bodyClassName="p-4">
          <Totals items={[['Messages', formatNumber(sum(rows, 'messages'))], ['Charged', formatMoney(sum(rows, 'charged'))], ['Refunds', formatMoney(sum(rows, 'refunds'))], ['Top-ups', formatMoney(sum(rows, 'topups'))]]} />
          <DataTable
            {...common}
            columns={[
              { key: 'partner', header: 'Partner', primary: true, render: (r) => r.partner?.businessName || r.partnerName || r.partner },
              { key: 'messages', header: 'Messages', align: 'right', render: (r) => formatNumber(r.messages) },
              { key: 'byCategory', header: 'By category', hideOnMobile: true, render: (r) => Object.entries(r.byCategory || {}).map(([k, v]) => `${humanize(k)}: ${formatNumber(v)}`).join(' · ') || '—' },
              { key: 'charged', header: 'Charged', align: 'right', render: (r) => <Money value={r.charged} /> },
              { key: 'refunds', header: 'Refunds', align: 'right', hideOnMobile: true, render: (r) => <Money value={r.refunds} /> },
              { key: 'topups', header: 'Top-ups', align: 'right', hideOnMobile: true, render: (r) => <Money value={r.topups} /> },
            ]}
            rows={Array.isArray(q.data) ? rows : undefined}
            page={Array.isArray(q.data) ? undefined : q.data}
            rowKey={(r) => r.partner?.id ?? r.partnerId ?? JSON.stringify(r.partner)}
            onPageChange={(p) => update({ page: p })}
          />
        </Card>
      )}

      {(state.tab === 'categories' || state.tab === 'countries') && (
        <div className="space-y-4">
          <ChartCard
            title={state.tab === 'categories' ? 'Messages by category' : 'Messages by country'}
            type="bar"
            xKey="key"
            loading={q.isLoading}
            data={rows.map((r) => ({ key: state.tab === 'categories' ? humanize(r.category) : r.country, count: toNumber(r.count), charged: toNumber(r.charged) }))}
            series={[{ key: 'count', label: 'Messages', color: '#25D366' }]}
          />
          <Card padded={false} bodyClassName="p-4">
            <DataTable
              {...common}
              rows={rows}
              rowKey={(r) => r.category || r.country}
              columns={[
                { key: 'k', header: state.tab === 'categories' ? 'Category' : 'Country', primary: true, render: (r) => (state.tab === 'categories' ? humanize(r.category) : r.country) },
                { key: 'count', header: 'Messages', align: 'right', render: (r) => formatNumber(r.count) },
                { key: 'charged', header: 'Charged', align: 'right', render: (r) => <Money value={r.charged} /> },
              ]}
            />
          </Card>
        </div>
      )}

      {state.tab === 'revenue' && (
        <div className="space-y-4">
          <Totals items={[['Gateway collections', formatMoney(sum(rows, 'gatewayCollections'))], ['Manual top-ups', formatMoney(sum(rows, 'manualTopups'))], ['GST collected', formatMoney(sum(rows, 'gst'))], ['Message charges', formatMoney(sum(rows, 'messageCharges'))], ['Platform fees', formatMoney(sum(rows, 'platformFees'))]]} />
          <ChartCard
            title="Revenue"
            type="bar"
            xKey={state.groupBy === 'day' ? 'date' : 'period'}
            loading={q.isLoading}
            data={rows.map((r) => ({ date: r.period, period: r.period, messageCharges: toNumber(r.messageCharges), platformFees: toNumber(r.platformFees) }))}
            series={[{ key: 'messageCharges', label: 'Message charges', color: '#25D366' }, { key: 'platformFees', label: 'Platform fees', color: '#128C7E' }]}
            valueFormatter={moneyAxis}
          />
          <Card padded={false} bodyClassName="p-4">
            <DataTable
              {...common}
              rows={rows}
              rowKey={(r) => r.period}
              columns={[
                { key: 'period', header: 'Period', primary: true },
                { key: 'gatewayCollections', header: 'Gateway', align: 'right', render: (r) => <Money value={r.gatewayCollections} /> },
                { key: 'manualTopups', header: 'Manual top-ups', align: 'right', render: (r) => <Money value={r.manualTopups} /> },
                { key: 'gst', header: 'GST', align: 'right', hideOnMobile: true, render: (r) => <Money value={r.gst} /> },
                { key: 'messageCharges', header: 'Message charges', align: 'right', render: (r) => <Money value={r.messageCharges} /> },
                { key: 'platformFees', header: 'Platform fees', align: 'right', hideOnMobile: true, render: (r) => <Money value={r.platformFees} /> },
              ]}
            />
          </Card>
        </div>
      )}

      {state.tab === 'reconciliation' && (
        <div className="space-y-4">
          <Totals
            items={[
              ['Meta messages', formatNumber(sum(rows, 'metaVolume'))],
              ['Meta cost', formatMoney(sum(rows, 'metaCost'))],
              ['Our messages', formatNumber(sum(rows, 'ourCount'))],
              ['Our charges', formatMoney(sum(rows, 'ourCharge'))],
              ['Profit', formatMoney(sum(rows, 'profit'))],
            ]}
          />
          <p className="text-sm text-muted">Meta cost comes from Meta pricing analytics (published with a delay of about a day). Differences in message counts usually mean free messages or messages still in progress.</p>
          <Card padded={false} bodyClassName="p-4">
            <DataTable
              {...common}
              rows={rows}
              rowKey={(r) => `${r.date}-${r.sender}-${r.country}-${r.category}`}
              columns={[
                { key: 'date', header: 'Date', primary: true },
                { key: 'sender', header: 'Number', hideOnMobile: true },
                { key: 'country', header: 'Country' },
                { key: 'category', header: 'Category', render: (r) => humanize(r.category) },
                { key: 'metaVolume', header: 'Meta msgs', align: 'right', render: (r) => formatNumber(r.metaVolume) },
                { key: 'metaCost', header: 'Meta cost', align: 'right', render: (r) => <Money value={r.metaCost} /> },
                { key: 'ourCount', header: 'Our msgs', align: 'right', hideOnMobile: true, render: (r) => formatNumber(r.ourCount) },
                { key: 'ourCharge', header: 'Our charge', align: 'right', render: (r) => <Money value={r.ourCharge} /> },
                { key: 'profit', header: 'Profit', align: 'right', render: (r) => <Money value={r.profit} signed /> },
              ]}
            />
          </Card>
        </div>
      )}
    </>
  );
}

function Totals({ items }) {
  return (
    <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-5">
      {items.map(([l, v]) => (
        <div key={l} className="card p-3">
          <p className="text-xs text-muted">{l}</p>
          <p className="mt-1 text-lg font-semibold text-ink">{v}</p>
        </div>
      ))}
    </div>
  );
}
