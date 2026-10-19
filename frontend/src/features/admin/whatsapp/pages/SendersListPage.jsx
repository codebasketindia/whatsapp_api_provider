import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Link2, MoreVertical, Settings2, Smartphone, UserPlus, UserMinus } from 'lucide-react';
import * as metaApi from '../../../../api/endpoints/meta';
import { useAuth } from '../../../../auth/useAuth';
import PermissionGate from '../../../../auth/PermissionGate';
import { useUrlState } from '../../../../hooks/useUrlState';
import { Button, DataTable, DropdownMenu, EmptyState, FilterBar, IconButton, PageHeader, SearchInput, Select } from '../../../../components/ui';
import { senderColumns } from '../components/senderColumns';
import SenderAdminDialogs from '../components/SenderAdminDialogs';
import SenderHealthAlerts from '../components/SenderHealthAlerts';

export default function SendersListPage() {
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('WHATSAPP_MANAGE');
  const [state, update] = useUrlState({ page: 0, search: '', senderType: '', quality: '', status: '', assigned: '' });
  const [dialog, setDialog] = useState(null);
  const q = useQuery({ queryKey: ['admin', 'senders', state], queryFn: () => metaApi.listSenders({ ...state, size: 20 }), placeholderData: (p) => p });

  const menu = (s) =>
    [
      s.senderType === 'PLATFORM_SENDER' && !s.partner && { label: 'Assign to partner', icon: UserPlus, onClick: () => setDialog({ type: 'assign', sender: s }) },
      s.senderType === 'PLATFORM_SENDER' && s.partner && { label: 'Unassign', icon: UserMinus, danger: true, onClick: () => setDialog({ type: 'unassign', sender: s }) },
      { label: 'Sending settings', icon: Settings2, onClick: () => setDialog({ type: 'settings', sender: s }) },
    ].filter(Boolean);

  const columns = [
    ...senderColumns(),
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      align: 'right',
      hideOnMobile: true,
      render: (s) => canManage && <DropdownMenu trigger={({ toggle }) => <IconButton icon={MoreVertical} label="Actions" onClick={(e) => { e.stopPropagation(); toggle(); }} />} items={menu(s)} />,
    },
  ];
  return (
    <>
      <PageHeader
        title="All senders"
        subtitle="Every WhatsApp number on the platform: our numbers and partners' own numbers."
        actions={<PermissionGate permission="WHATSAPP_MANAGE"><Button variant="secondary" leftIcon={Link2} to="/admin/whatsapp/connect">Manual connect</Button></PermissionGate>}
      />
      <SenderHealthAlerts />
      <FilterBar>
        <SearchInput className="sm:flex-[2]" value={state.search} onSearch={(v) => update({ search: v })} placeholder="Search number or display name" />
        <Select aria-label="Type" value={state.senderType} onChange={(e) => update({ senderType: e.target.value })} placeholder="All types" options={[{ value: 'PLATFORM_SENDER', label: 'Platform numbers' }, { value: 'PARTNER_OWN_SENDER', label: 'Partner own numbers' }]} />
        <Select aria-label="Quality" value={state.quality} onChange={(e) => update({ quality: e.target.value })} placeholder="Any quality" options={[{ value: 'GREEN', label: 'High' }, { value: 'YELLOW', label: 'Medium' }, { value: 'RED', label: 'Low' }, { value: 'UNKNOWN', label: 'Not rated' }]} />
        <Select aria-label="Status" value={state.status} onChange={(e) => update({ status: e.target.value })} placeholder="All statuses" options={[{ value: 'CONNECTED', label: 'Connected' }, { value: 'PENDING_SETUP', label: 'Pending setup' }, { value: 'DISCONNECTED', label: 'Disconnected' }]} />
        <Select aria-label="Assignment" value={state.assigned} onChange={(e) => update({ assigned: e.target.value })} placeholder="Assigned or not" options={[{ value: 'true', label: 'Assigned' }, { value: 'false', label: 'Not assigned' }]} />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(s) => navigate(`/admin/whatsapp/senders/${s.id}`)}
        mobileActions={(s) => canManage && menu(s).map((m) => <Button key={m.label} size="sm" variant={m.danger ? 'danger-outline' : 'secondary'} leftIcon={m.icon} onClick={m.onClick}>{m.label}</Button>)}
        empty={<EmptyState icon={Smartphone} title="No numbers found" text="Add our WhatsApp account or wait for partners to connect their numbers." action={<Button variant="secondary" to="/admin/whatsapp/accounts">WhatsApp accounts</Button>} />}
      />
      <SenderAdminDialogs dialog={dialog} onClose={() => setDialog(null)} />
    </>
  );
}
