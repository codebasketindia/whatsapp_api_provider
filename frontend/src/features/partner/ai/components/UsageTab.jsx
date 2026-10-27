import { useQuery } from '@tanstack/react-query';
import { Bot, IndianRupee, MessageSquareReply, UserRound } from 'lucide-react';
import * as aiApi from '../../../../api/endpoints/ai';
import { DataTable, DateRangePicker, DateTime, EmptyState, FilterBar, Select, StatCard, StatusBadge, dateRangePresets } from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatMoney, formatNumber, formatPhone } from '../../../../lib/format';
import { PAGE_SIZE } from '../../../../lib/constants';
import { senderLabel, useMySenders } from '../../common/useSenders';
import { OUTCOME_LABEL } from './aiHelpers';

const OUTCOME_STATUS = { REPLIED: 'SUCCESS', FALLBACK: 'WARNING', HANDOVER: 'PENDING', SKIPPED: 'SKIPPED', ERROR: 'ERROR' };

export default function UsageTab() {
  const last7 = dateRangePresets().find((p) => p.key === '7d');
  const [f, update] = useUrlState({ from: last7.from, to: last7.to, outcome: '', page: 0 });
  const senders = useMySenders();
  const params = { from: f.from, to: f.to, outcome: f.outcome, page: f.page, size: PAGE_SIZE };
  const q = useQuery({ queryKey: ['partner', 'ai', 'usage', params], queryFn: () => aiApi.getAiUsage(params), placeholderData: (p) => p });
  const s = q.data?.summary || {};
  const senderName = (id) => {
    const x = (senders.data || []).find((y) => y.id === id);
    return x ? senderLabel(x) : '';
  };

  const columns = [
    { key: 'createdAt', header: 'When', primary: true, render: (r) => <DateTime value={r.createdAt} /> },
    { key: 'waId', header: 'Customer', render: (r) => <span><span className="block">{formatPhone(r.waId)}</span><span className="text-xs text-muted">{senderName(r.senderId)}</span></span> },
    { key: 'outcome', header: 'Result', render: (r) => <span><StatusBadge status={OUTCOME_STATUS[r.outcome] || r.outcome} label={OUTCOME_LABEL[r.outcome] || r.outcome} />{r.reason && <span className="mt-1 block text-xs text-muted">{r.reason}</span>}</span> },
    {
      key: 'preview',
      header: 'Conversation',
      render: (r) => (
        <span className="block max-w-sm text-sm">
          <span className="line-clamp-2"><span className="text-muted">Customer: </span>{r.inboundPreview || '—'}</span>
          {r.replyPreview && <span className="mt-0.5 line-clamp-2"><span className="text-muted">Reply: </span>{r.replyPreview}</span>}
        </span>
      ),
    },
    { key: 'tokens', header: 'Tokens', align: 'right', hideOnMobile: true, render: (r) => formatNumber((r.inputTokens || 0) + (r.outputTokens || 0)) },
    { key: 'charged', header: 'Charged', align: 'right', render: (r) => formatMoney(r.charged, { precise: true }) },
  ];

  return (
    <>
      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="AI replies" value={formatNumber(s.replies ?? 0)} icon={Bot} tone="green" loading={q.isLoading} />
        <StatCard label="Fallbacks sent" value={formatNumber(s.fallbacks ?? 0)} icon={MessageSquareReply} tone="amber" loading={q.isLoading} />
        <StatCard label="Handed to you" value={formatNumber(s.handovers ?? 0)} icon={UserRound} tone="blue" loading={q.isLoading} />
        <StatCard label="Charged" value={formatMoney(s.charged ?? 0)} icon={IndianRupee} loading={q.isLoading} />
      </div>
      <FilterBar className="mb-4">
        <DateRangePicker label="Date" value={{ from: f.from, to: f.to }} onChange={(r) => update({ from: r.from, to: r.to })} />
        <Select
          label="Result"
          value={f.outcome}
          onChange={(e) => update({ outcome: e.target.value })}
          placeholder="All results"
          options={Object.entries(OUTCOME_LABEL).map(([value, label]) => ({ value, label }))}
        />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data?.page}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        empty={<EmptyState compact icon={Bot} title="No AI activity in this period" text="When the AI answers customers, every reply and its cost shows here." />}
      />
    </>
  );
}
