import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Megaphone, Plus } from 'lucide-react';
import * as campaignsApi from '../../../../api/endpoints/campaigns';
import {
  Button, DataTable, DateRangePicker, DateTime, EmptyState, FilterBar, PageHeader, ProgressBar, SearchInput, Select, StatusBadge,
} from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatMoney, formatNumber } from '../../../../lib/format';
import { CAMPAIGN_STATUS_LABEL } from '../components/schedule';

export const campaignProgress = (c) => {
  const k = c.counts || {};
  const valid = k.valid || k.total || 0;
  if (!valid) return 0;
  return Math.min(100, (((k.sent || 0) + (k.delivered || 0) + (k.read || 0) + (k.failed || 0)) / valid) * 100);
};

export default function MyCampaignsPage() {
  const navigate = useNavigate();
  const [f, update] = useUrlState({ page: 0, status: '', search: '', from: '', to: '' });
  const params = { page: f.page, size: 20, status: f.status, search: f.search, from: f.from, to: f.to };
  const q = useQuery({
    queryKey: ['partner', 'campaigns', params],
    queryFn: () => campaignsApi.listMyCampaigns(params),
    placeholderData: (p) => p,
    refetchInterval: (query) => ((query.state.data?.content || []).some((c) => c.status === 'RUNNING') ? 10_000 : false),
  });
  const columns = [
    {
      key: 'name',
      header: 'Campaign',
      primary: true,
      render: (r) => (
        <div>
          <p className="font-medium text-ink">{r.name}</p>
          <p className="text-xs text-muted">{r.template?.name}</p>
        </div>
      ),
    },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} label={CAMPAIGN_STATUS_LABEL[r.status]} /> },
    {
      key: 'progress',
      header: 'Progress',
      render: (r) => (
        <div className="min-w-[140px]">
          <ProgressBar value={campaignProgress(r)} />
          <p className="mt-1 text-xs text-muted">
            {formatNumber(r.counts?.delivered + (r.counts?.read || 0))} delivered of {formatNumber(r.counts?.valid ?? r.counts?.total)}
          </p>
        </div>
      ),
    },
    { key: 'cost', header: 'Cost', align: 'right', render: (r) => formatMoney(r.chargedAmount || r.estimatedCost) },
    { key: 'when', header: 'When', hideOnMobile: true, render: (r) => <DateTime value={r.startedAt || r.scheduledAt || r.createdAt} /> },
  ];
  return (
    <>
      <PageHeader title="Campaigns" subtitle="Send a template to many people at once" actions={<Button leftIcon={Plus} to="/partner/campaigns/new">New campaign</Button>} />
      <FilterBar>
        <SearchInput value={f.search} onSearch={(v) => update({ search: v })} placeholder="Search campaigns" />
        <Select aria-label="Status" value={f.status} onChange={(e) => update({ status: e.target.value })} options={Object.entries(CAMPAIGN_STATUS_LABEL).map(([value, label]) => ({ value, label }))} placeholder="All statuses" />
        <DateRangePicker value={{ from: f.from, to: f.to }} onChange={(v) => update(v)} allowClear />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(r) => navigate(`/partner/campaigns/${r.id}`)}
        empty={<EmptyState icon={Megaphone} title="No campaigns yet" text="Upload a list or pick a contact group and send an approved template to everyone." action={<Button to="/partner/campaigns/new">Create campaign</Button>} />}
      />
    </>
  );
}
