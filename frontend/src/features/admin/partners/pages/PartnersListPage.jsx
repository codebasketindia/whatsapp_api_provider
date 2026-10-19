import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Building2, Plus } from 'lucide-react';
import * as partnersApi from '../../../../api/endpoints/partners';
import * as ratePlansApi from '../../../../api/endpoints/ratePlans';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatPhone } from '../../../../lib/format';
import PermissionGate from '../../../../auth/PermissionGate';
import {
  Badge, Button, DataTable, DateTime, EmptyState, FilterBar, Money, PageHeader, SearchInput, Select, StatusBadge,
} from '../../../../components/ui';

export default function PartnersListPage() {
  const navigate = useNavigate();
  const [state, update] = useUrlState({ page: 0, search: '', status: '', ratePlanId: '', lowBalance: '', sort: '' });
  const q = useQuery({
    queryKey: ['admin', 'partners', state],
    queryFn: () =>
      partnersApi.listPartners({
        page: state.page,
        size: 20,
        search: state.search,
        status: state.status,
        ratePlanId: state.ratePlanId,
        lowBalance: state.lowBalance || undefined,
        sort: state.sort,
      }),
    placeholderData: (p) => p,
  });
  const plansQ = useQuery({ queryKey: ['admin', 'rate-plans', 'all'], queryFn: () => ratePlansApi.listRatePlans({ size: 100 }), staleTime: 60_000 });
  const plans = plansQ.data?.content || [];

  const columns = [
    {
      key: 'businessName',
      header: 'Partner',
      primary: true,
      sortable: true,
      render: (p) => (
        <span className="min-w-0">
          <span className="block font-medium text-ink">{p.businessName}</span>
          <span className="block truncate text-xs text-muted">
            {p.partnerCode} · {p.contactName}
          </span>
        </span>
      ),
    },
    { key: 'contact', header: 'Contact', hideOnMobile: true, render: (p) => (
      <span className="text-sm">
        <span className="block">{p.email}</span>
        <span className="block text-xs text-muted">{formatPhone(p.mobile)}</span>
      </span>
    ) },
    { key: 'balance', header: 'Wallet', align: 'right', render: (p) => <Money value={p.wallet?.balance} /> },
    { key: 'ratePlan', header: 'Rate plan', hideOnMobile: true, render: (p) => p.ratePlan?.name || '—' },
    {
      key: 'flags',
      header: 'Access',
      hideOnMobile: true,
      render: (p) => (
        <span className="flex flex-wrap gap-1">
          {p.allowOwnNumber && <Badge tone="blue">Own number</Badge>}
          {p.kycVerified ? <Badge tone="green">KYC verified</Badge> : <Badge tone="gray">KYC pending</Badge>}
        </span>
      ),
    },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status} /> },
    { key: 'createdAt', header: 'Joined', sortable: true, hideOnMobile: true, render: (p) => <DateTime value={p.createdAt} mode="date" /> },
  ];

  const filtered = state.search || state.status || state.ratePlanId || state.lowBalance;
  return (
    <>
      <PageHeader
        title="Partners"
        subtitle="Businesses and developers who send messages through the platform."
        actions={
          <PermissionGate permission="PARTNER_MANAGE">
            <Button leftIcon={Plus} to="/admin/partners/new">Add partner</Button>
          </PermissionGate>
        }
      />
      <FilterBar>
        <SearchInput className="sm:flex-[2]" value={state.search} onSearch={(v) => update({ search: v })} placeholder="Search name, code, email or mobile" />
        <Select
          aria-label="Status"
          value={state.status}
          onChange={(e) => update({ status: e.target.value })}
          placeholder="All statuses"
          options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'SUSPENDED', label: 'Suspended' }]}
        />
        <Select
          aria-label="Rate plan"
          value={state.ratePlanId}
          onChange={(e) => update({ ratePlanId: e.target.value })}
          placeholder="All rate plans"
          options={plans.map((p) => ({ value: String(p.id), label: p.name }))}
        />
        <Select
          aria-label="Wallet"
          value={state.lowBalance}
          onChange={(e) => update({ lowBalance: e.target.value })}
          placeholder="Any balance"
          options={[{ value: 'true', label: 'Low balance only' }]}
        />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        sort={state.sort}
        onSortChange={(s) => update({ sort: s })}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(p) => navigate(`/admin/partners/${p.id}`)}
        empty={
          <EmptyState
            icon={Building2}
            title={filtered ? 'No partners match your filters' : 'No partners yet'}
            text={filtered ? 'Try clearing the filters.' : 'Add your first partner. They will get login details by email.'}
            action={
              <PermissionGate permission="PARTNER_MANAGE">
                <Button leftIcon={Plus} to="/admin/partners/new">Add partner</Button>
              </PermissionGate>
            }
          />
        }
      />
    </>
  );
}
