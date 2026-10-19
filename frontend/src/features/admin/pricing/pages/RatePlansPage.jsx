import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Copy, Plus, Tags } from 'lucide-react';
import * as ratePlansApi from '../../../../api/endpoints/ratePlans';
import { useUrlState } from '../../../../hooks/useUrlState';
import { Badge, Button, Checkbox, DataTable, EmptyState, FilterBar, Input, Modal, Money, PageHeader, Select, StatusBadge } from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';

export default function RatePlansPage() {
  const navigate = useNavigate();
  const [state, update] = useUrlState({ page: 0, status: 'ACTIVE', includeCustom: 'true' });
  const [clone, setClone] = useState(null);
  const [cloneName, setCloneName] = useState('');
  const q = useQuery({
    queryKey: ['admin', 'rate-plans', state],
    queryFn: () => ratePlansApi.listRatePlans({ ...state, size: 20 }),
    placeholderData: (p) => p,
  });
  const cloneM = useApiMutation({
    mutationFn: () => ratePlansApi.cloneRatePlan(clone.id, { name: cloneName.trim() }),
    success: 'Plan copied. Adjust the prices and save.',
    invalidate: [['admin', 'rate-plans']],
    onSuccess: (p) => navigate(`/admin/pricing/rate-plans/${p.id}`),
  });
  const inr = (p, cat) => p.items?.find((i) => i.countryCode === 'IN' && i.category === cat)?.price;
  const columns = [
    {
      key: 'name',
      header: 'Plan',
      primary: true,
      render: (p) => (
        <span>
          <span className="flex items-center gap-2 font-medium text-ink">
            {p.name} {p.isDefault && <Badge tone="brand">Default</Badge>} {p.isCustom && <Badge tone="blue">Custom</Badge>}
          </span>
          {p.description && <span className="block max-w-sm truncate text-xs text-muted">{p.description}</span>}
        </span>
      ),
    },
    { key: 'mk', header: 'Marketing (IN)', align: 'right', render: (p) => (inr(p, 'MARKETING') ? <Money value={inr(p, 'MARKETING')} precise /> : <Badge tone="amber">Not set</Badge>) },
    { key: 'ut', header: 'Utility (IN)', align: 'right', hideOnMobile: true, render: (p) => (inr(p, 'UTILITY') ? <Money value={inr(p, 'UTILITY')} precise /> : '—') },
    { key: 'au', header: 'Auth (IN)', align: 'right', hideOnMobile: true, render: (p) => (inr(p, 'AUTHENTICATION') ? <Money value={inr(p, 'AUTHENTICATION')} precise /> : '—') },
    { key: 'pc', header: 'Partners', align: 'right', render: (p) => p.partnerCount ?? '—' },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status} /> },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      align: 'right',
      hideOnMobile: true,
      render: (p) => <Button size="sm" variant="secondary" leftIcon={Copy} onClick={(e) => { e.stopPropagation(); setClone(p); setCloneName(`${p.name} (copy)`); }}>Copy</Button>,
    },
  ];
  return (
    <>
      <PageHeader
        title="Rate plans"
        subtitle="What partners pay per message, by category and country."
        actions={<Button leftIcon={Plus} to="/admin/pricing/rate-plans/new">New plan</Button>}
      />
      <FilterBar>
        <Select
          aria-label="Status"
          value={state.status}
          onChange={(e) => update({ status: e.target.value })}
          placeholder="All statuses"
          options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'ARCHIVED', label: 'Archived' }]}
        />
        <Checkbox label="Include custom (partner-specific) plans" checked={state.includeCustom === 'true'} onChange={(e) => update({ includeCustom: e.target.checked ? 'true' : 'false' })} />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(p) => navigate(`/admin/pricing/rate-plans/${p.id}`)}
        mobileActions={(p) => <Button size="sm" variant="secondary" leftIcon={Copy} onClick={() => { setClone(p); setCloneName(`${p.name} (copy)`); }}>Copy</Button>}
        empty={<EmptyState icon={Tags} title="No rate plans" action={<Button leftIcon={Plus} to="/admin/pricing/rate-plans/new">New plan</Button>} />}
      />
      <Modal
        open={Boolean(clone)}
        onClose={() => setClone(null)}
        title={`Copy "${clone?.name}"`}
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setClone(null)}>Cancel</Button>
            <Button disabled={cloneName.trim().length < 2} loading={cloneM.isPending} onClick={() => cloneM.mutate()}>Copy plan</Button>
          </>
        }
      >
        <Input label="New plan name" value={cloneName} onChange={(e) => setCloneName(e.target.value)} />
      </Modal>
    </>
  );
}
