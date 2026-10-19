import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { KeyRound, MoreVertical, Pencil, Plus, ShieldCheck, UserCheck, UserX, Users } from 'lucide-react';
import * as staffApi from '../../../../api/endpoints/staff';
import { getErrorMessage } from '../../../../api/axios';
import { useAuth } from '../../../../auth/useAuth';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatPhone, humanize } from '../../../../lib/format';
import {
  Avatar, Badge, Button, ConfirmDialog, DataTable, DateTime, DropdownMenu, EmptyState, FilterBar, IconButton, PageHeader, SearchInput,
  Select, StatusBadge,
} from '../../../../components/ui';
import StaffFormDrawer from '../components/StaffFormDrawer';

export default function StaffPage() {
  const { user: me } = useAuth();
  const qc = useQueryClient();
  const [state, update] = useUrlState({ page: 0, search: '', status: '' });
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [confirm, setConfirm] = useState(null); // { type: 'status'|'reset', staff }

  const q = useQuery({
    queryKey: ['admin', 'staff', state],
    queryFn: () => staffApi.listStaff({ page: state.page, size: 20, search: state.search, status: state.status }),
    placeholderData: (prev) => prev,
  });

  const statusM = useMutation({
    mutationFn: ({ uid, status }) => staffApi.setStaffStatus(uid, status),
    onSuccess: (u) => {
      toast.success(u.status === 'ACTIVE' ? 'Staff member enabled' : 'Staff member disabled and logged out');
      qc.invalidateQueries({ queryKey: ['admin', 'staff'] });
      setConfirm(null);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const resetM = useMutation({
    mutationFn: (uid) => staffApi.resetStaffPassword(uid),
    onSuccess: () => {
      toast.success('A temporary password has been emailed');
      setConfirm(null);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const openAdd = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const actions = (s) => {
    const self = s.uid === me?.uid;
    return [
      { label: 'Edit', icon: Pencil, onClick: () => { setEditing(s); setFormOpen(true); }, disabled: s.isSuperAdmin && !me?.isSuperAdmin },
      { label: 'Send new password', icon: KeyRound, onClick: () => setConfirm({ type: 'reset', staff: s }), hidden: self },
      s.status === 'ACTIVE'
        ? { label: 'Disable', icon: UserX, danger: true, onClick: () => setConfirm({ type: 'status', staff: s }), hidden: self || s.isSuperAdmin }
        : { label: 'Enable', icon: UserCheck, onClick: () => setConfirm({ type: 'status', staff: s }), hidden: self },
    ];
  };

  const columns = [
    {
      key: 'name',
      header: 'Name',
      primary: true,
      render: (s) => (
        <span className="flex items-center gap-3">
          <Avatar name={s.name} size="sm" />
          <span className="min-w-0">
            <span className="block font-medium text-ink">
              {s.name} {s.uid === me?.uid && <span className="text-xs text-muted">(you)</span>}
            </span>
            <span className="block truncate text-xs text-muted">{s.email}</span>
          </span>
        </span>
      ),
    },
    { key: 'mobile', header: 'Mobile', render: (s) => (s.mobile ? formatPhone(s.mobile) : '—') },
    {
      key: 'role',
      header: 'Role',
      render: (s) => (s.isSuperAdmin ? <Badge tone="brand">Super admin</Badge> : s.role?.name || humanize(s.role?.code)),
    },
    { key: 'twoFactor', header: '2-step login', render: (s) => (s.twoFactorEnabled ? <Badge tone="green">On</Badge> : <Badge tone="gray">Off</Badge>) },
    { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.status} /> },
    { key: 'lastLoginAt', header: 'Last login', hideOnMobile: true, render: (s) => <DateTime value={s.lastLoginAt} mode="relative" /> },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      align: 'right',
      hideOnMobile: true,
      render: (s) => (
        <DropdownMenu trigger={({ toggle }) => <IconButton icon={MoreVertical} label={`Actions for ${s.name}`} onClick={toggle} />} items={actions(s)} />
      ),
    },
  ];

  const c = confirm;
  return (
    <>
      <PageHeader
        title="Staff"
        subtitle="People who can log in to the admin panel."
        actions={
          <>
            <Button variant="secondary" leftIcon={ShieldCheck} to="/admin/staff/roles">
              Roles & permissions
            </Button>
            <Button leftIcon={Plus} onClick={openAdd}>
              Add staff
            </Button>
          </>
        }
      />
      <FilterBar>
        <SearchInput className="sm:flex-1" value={state.search} onSearch={(v) => update({ search: v })} placeholder="Search by name or email" />
        <Select
          aria-label="Status"
          value={state.status}
          onChange={(e) => update({ status: e.target.value })}
          placeholder="All statuses"
          options={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'DISABLED', label: 'Disabled' },
          ]}
        />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        rowKey={(s) => s.uid}
        onPageChange={(p) => update({ page: p })}
        mobileActions={(s) =>
          actions(s)
            .filter((a) => !a.hidden)
            .map((a) => (
              <Button key={a.label} size="sm" variant={a.danger ? 'danger-outline' : 'secondary'} leftIcon={a.icon} onClick={a.onClick} disabled={a.disabled}>
                {a.label}
              </Button>
            ))
        }
        empty={
          <EmptyState
            icon={Users}
            title={state.search || state.status ? 'No staff match your filters' : 'No staff yet'}
            text="Add team members and give them only the access they need."
            action={<Button leftIcon={Plus} onClick={openAdd}>Add staff</Button>}
          />
        }
      />
      <StaffFormDrawer open={formOpen} onClose={() => setFormOpen(false)} staff={editing} />
      <ConfirmDialog
        open={c?.type === 'status'}
        onClose={() => setConfirm(null)}
        loading={statusM.isPending}
        danger={c?.staff?.status === 'ACTIVE'}
        title={c?.staff?.status === 'ACTIVE' ? `Disable ${c?.staff?.name}?` : `Enable ${c?.staff?.name}?`}
        message={
          c?.staff?.status === 'ACTIVE'
            ? 'They will be logged out immediately and cannot log in until enabled again.'
            : 'They will be able to log in again with their existing password.'
        }
        confirmLabel={c?.staff?.status === 'ACTIVE' ? 'Disable' : 'Enable'}
        onConfirm={() => statusM.mutate({ uid: c.staff.uid, status: c.staff.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}
      />
      <ConfirmDialog
        open={c?.type === 'reset'}
        onClose={() => setConfirm(null)}
        loading={resetM.isPending}
        title={`Send a new password to ${c?.staff?.name}?`}
        message={`A temporary password will be emailed to ${c?.staff?.email}. They must change it after logging in.`}
        confirmLabel="Send password"
        onConfirm={() => resetM.mutate(c.staff.uid)}
      />
    </>
  );
}
