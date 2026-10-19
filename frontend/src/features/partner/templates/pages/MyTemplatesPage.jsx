import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { BookOpen, FileText, Plus, RefreshCw } from 'lucide-react';
import toast from 'react-hot-toast';
import * as templatesApi from '../../../../api/endpoints/templates';
import { getErrorMessage } from '../../../../api/axios';
import {
  Badge, Button, DataTable, DateTime, EmptyState, FilterBar, PageHeader, SearchInput, Select, StatusBadge,
} from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { TEMPLATE_CATEGORIES } from '../../../../lib/constants';
import { humanize } from '../../../../lib/format';
import { useWabaOptions } from '../components/useWabaOptions';

const STATUSES = ['DRAFT', 'PENDING_REVIEW', 'PENDING', 'APPROVED', 'REJECTED', 'INTERNAL_REJECTED', 'PAUSED', 'DISABLED'];
const STATUS_LABEL = { PENDING_REVIEW: 'Waiting for our review', PENDING: 'With Meta for review', INTERNAL_REJECTED: 'Rejected by us' };
const QUALITY_TONE = { GREEN: 'green', YELLOW: 'amber', RED: 'red' };

export default function MyTemplatesPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [f, update] = useUrlState({ page: 0, status: '', category: '', search: '', wabaId: '' });
  const params = { page: f.page, size: 20, status: f.status, category: f.category, search: f.search, wabaId: f.wabaId };
  const q = useQuery({ queryKey: ['partner', 'templates', params], queryFn: () => templatesApi.listMyTemplates(params), placeholderData: (p) => p });
  const wabas = useWabaOptions();
  const sync = useMutation({
    mutationFn: async () => {
      const own = wabas.options.filter((w) => w.own);
      const results = await Promise.all(own.map((w) => templatesApi.syncMyTemplates(w.value)));
      return results.reduce((acc, r) => ({ created: acc.created + (r?.created || 0), updated: acc.updated + (r?.updated || 0) }), { created: 0, updated: 0 });
    },
    onSuccess: (r) => {
      toast.success(`Synced from Meta: ${r.created} new, ${r.updated} updated`);
      qc.invalidateQueries({ queryKey: ['partner', 'templates'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const hasOwn = wabas.options.some((w) => w.own);
  const columns = [
    {
      key: 'name',
      header: 'Template',
      primary: true,
      render: (r) => (
        <div>
          <p className="font-medium text-ink">{r.name}</p>
          <p className="text-xs text-muted">{r.language}</p>
        </div>
      ),
    },
    { key: 'category', header: 'Category', render: (r) => humanize(r.category) },
    {
      key: 'status',
      header: 'Status',
      render: (r) => (
        <div>
          <StatusBadge status={r.status} label={STATUS_LABEL[r.status]} />
          {r.rejectedReason && ['REJECTED', 'INTERNAL_REJECTED'].includes(r.status) && <p className="mt-1 max-w-xs text-xs text-red-600">{r.rejectedReason}</p>}
        </div>
      ),
    },
    {
      key: 'quality',
      header: 'Quality',
      render: (r) => (r.qualityScore && r.qualityScore !== 'UNKNOWN' ? <Badge tone={QUALITY_TONE[r.qualityScore] || 'gray'} dot>{humanize(r.qualityScore)}</Badge> : <span className="text-muted">—</span>),
    },
    { key: 'updated', header: 'Updated', hideOnMobile: true, render: (r) => <DateTime value={r.lastSyncedAt || r.submittedAt || r.createdAt} mode="relative" /> },
  ];
  return (
    <>
      <PageHeader
        title="Templates"
        subtitle="Approved templates let you message anyone, any time"
        actions={
          <>
            {hasOwn && <Button variant="secondary" leftIcon={RefreshCw} onClick={() => sync.mutate()} loading={sync.isPending}>Sync from Meta</Button>}
            <Button variant="secondary" leftIcon={BookOpen} to="/partner/templates/library">Template library</Button>
            <Button leftIcon={Plus} to="/partner/templates/new">New template</Button>
          </>
        }
      />
      <FilterBar>
        <SearchInput value={f.search} onSearch={(v) => update({ search: v })} placeholder="Search by name" />
        <Select aria-label="Status" value={f.status} onChange={(e) => update({ status: e.target.value })} options={STATUSES.map((s) => ({ value: s, label: STATUS_LABEL[s] || humanize(s) }))} placeholder="All statuses" />
        <Select aria-label="Category" value={f.category} onChange={(e) => update({ category: e.target.value })} options={TEMPLATE_CATEGORIES.map((c) => ({ value: c, label: humanize(c) }))} placeholder="All categories" />
        {wabas.options.length > 1 && (
          <Select aria-label="WhatsApp account" value={f.wabaId} onChange={(e) => update({ wabaId: e.target.value })} options={wabas.options} placeholder="All accounts" />
        )}
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(r) => navigate(`/partner/templates/${r.id}`)}
        empty={
          <EmptyState
            icon={FileText}
            title="No templates yet"
            text="Create a template or start from a ready-made one in the library."
            action={
              <>
                <Button to="/partner/templates/new">New template</Button>
                <Button variant="secondary" to="/partner/templates/library">Browse library</Button>
              </>
            }
          />
        }
      />
    </>
  );
}
