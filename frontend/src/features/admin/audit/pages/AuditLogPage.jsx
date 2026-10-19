import { useQuery } from '@tanstack/react-query';
import * as staffApi from '../../../../api/endpoints/staff';
import { useUrlState } from '../../../../hooks/useUrlState';
import { DateRangePicker, FilterBar, Input, PageHeader, Select } from '../../../../components/ui';
import AuditTable from '../components/AuditTable';

export const AUDIT_ACTIONS = [
  'WALLET_ADJUST', 'TOPUP_APPROVE', 'TOPUP_REJECT', 'SETTINGS_UPDATE', 'LOGIN_AS_PARTNER', 'SENDER_CONNECT', 'SENDER_DISCONNECT',
  'SENDER_ASSIGN', 'SENDER_UNASSIGN', 'API_KEY_CREATE', 'API_KEY_REGENERATE', 'PARTNER_CREATE', 'PARTNER_UPDATE', 'PARTNER_STATUS',
  'RATE_PLAN_ASSIGN', 'OWN_NUMBER_TOGGLE', 'STAFF_CREATE', 'STAFF_UPDATE', 'STAFF_STATUS', 'ROLE_CREATE', 'ROLE_UPDATE', 'PASSWORD_CHANGE',
  'PASSWORD_RESET', 'TWO_FACTOR_ENABLE', 'TWO_FACTOR_DISABLE',
];

const ENTITY_TYPES = ['PARTNER', 'WALLET', 'TOPUP_REQUEST', 'PAYMENT_ORDER', 'SETTINGS', 'SENDER', 'WABA', 'TEMPLATE', 'USER', 'ROLE', 'API_KEY', 'RATE_PLAN', 'CREDIT_PACK', 'CAMPAIGN'];

export default function AuditLogPage() {
  const [state, update] = useUrlState({ page: 0, action: '', entityType: '', entityId: '', partnerId: '', from: '', to: '' });
  const q = useQuery({
    queryKey: ['admin', 'audit', state],
    queryFn: () => staffApi.listAuditLogs({ ...state, size: 25 }),
    placeholderData: (p) => p,
  });
  return (
    <>
      <PageHeader title="Audit log" subtitle="Who changed what and when. Money actions, approvals, settings and logins as partner are recorded." />
      <FilterBar>
        <Select
          aria-label="Action"
          value={state.action}
          onChange={(e) => update({ action: e.target.value })}
          placeholder="All actions"
          options={AUDIT_ACTIONS.map((a) => ({ value: a, label: a.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase()) }))}
        />
        <Select
          aria-label="Record type"
          value={state.entityType}
          onChange={(e) => update({ entityType: e.target.value })}
          placeholder="All record types"
          options={ENTITY_TYPES.map((t) => ({ value: t, label: t.replace(/_/g, ' ').toLowerCase() }))}
        />
        <Input aria-label="Record id" placeholder="Record id" value={state.entityId} onChange={(e) => update({ entityId: e.target.value })} />
        <Input aria-label="Partner id" placeholder="Partner id" inputMode="numeric" value={state.partnerId} onChange={(e) => update({ partnerId: e.target.value.replace(/\D/g, '') })} />
      </FilterBar>
      <DateRangePicker className="mb-4" value={{ from: state.from, to: state.to }} onChange={(r) => update(r)} allowClear />
      <AuditTable query={q} onPageChange={(p) => update({ page: p })} showPartner />
    </>
  );
}
