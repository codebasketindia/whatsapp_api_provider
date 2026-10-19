import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MoreVertical, Plus, Trash2, Upload, Users } from 'lucide-react';
import toast from 'react-hot-toast';
import * as contactsApi from '../../../../api/endpoints/contacts';
import { getErrorMessage } from '../../../../api/axios';
import {
  Badge, Button, ConfirmDialog, IconButton, DataTable, DateTime, DropdownMenu, EmptyState, FilterBar, Modal, PageHeader, SearchInput, Select, Textarea,
} from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatPhone } from '../../../../lib/format';
import ContactDrawer from '../components/ContactDrawer';
import ContactsSubnav from '../components/ContactsSubnav';

export default function ContactsPage() {
  const qc = useQueryClient();
  const [f, update] = useUrlState({ page: 0, search: '', tag: '', groupId: '', optedOut: '' });
  const params = { page: f.page, size: 20, search: f.search, tag: f.tag, groupId: f.groupId, optedOut: f.optedOut };
  const q = useQuery({ queryKey: ['partner', 'contacts', params], queryFn: () => contactsApi.listContacts(params), placeholderData: (p) => p });
  const tags = useQuery({ queryKey: ['partner', 'contacts', 'tags'], queryFn: contactsApi.listContactTags });
  const groups = useQuery({ queryKey: ['partner', 'contact-groups', 'all'], queryFn: () => contactsApi.listContactGroups({ size: 100 }) });
  const [selected, setSelected] = useState([]);
  const [drawer, setDrawer] = useState({ open: false, contact: null });
  const [confirm, setConfirm] = useState(null); // {kind, contact?}
  const [reason, setReason] = useState('');
  const [groupModal, setGroupModal] = useState(false);
  const [targetGroup, setTargetGroup] = useState('');

  const refresh = () => qc.invalidateQueries({ queryKey: ['partner', 'contacts'] });
  const done = (msg) => ({
    onSuccess: () => {
      toast.success(msg);
      setConfirm(null);
      setReason('');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const bulkDelete = useMutation({ mutationFn: () => contactsApi.bulkDeleteContacts(selected), ...done('Contacts deleted') });
  const del = useMutation({ mutationFn: (id) => contactsApi.deleteContact(id), ...done('Contact deleted') });
  const optOut = useMutation({ mutationFn: (id) => contactsApi.optOutContact(id, reason || undefined), ...done('Contact opted out of marketing') });
  const optIn = useMutation({ mutationFn: (id) => contactsApi.optInContact(id), ...done('Contact opted in again') });
  const addToGroup = useMutation({
    mutationFn: () => contactsApi.addGroupMembers(targetGroup, selected),
    onSuccess: (r) => {
      toast.success(`${r?.added ?? selected.length} added to the group`);
      setGroupModal(false);
      setSelected([]);
      qc.invalidateQueries({ queryKey: ['partner', 'contact-groups'] });
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const rows = q.data?.content || [];
  const allSelected = rows.length > 0 && rows.every((r) => selected.includes(r.id));
  const toggle = (id) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  const actions = (r) => [
    { label: 'Edit', onClick: () => setDrawer({ open: true, contact: r }) },
    { label: 'Send message', to: `/partner/send?to=${r.phone}` },
    r.optedOut ? { label: 'Opt in again', onClick: () => setConfirm({ kind: 'optin', contact: r }) } : { label: 'Opt out of marketing', onClick: () => setConfirm({ kind: 'optout', contact: r }) },
    { label: 'Delete', danger: true, onClick: () => setConfirm({ kind: 'delete', contact: r }) },
  ];

  const columns = [
    {
      key: 'select',
      header: <input type="checkbox" aria-label="Select all" className="h-4 w-4" checked={allSelected} onChange={() => setSelected(allSelected ? [] : rows.map((r) => r.id))} />,
      hideOnMobile: true,
      render: (r) => <input type="checkbox" aria-label={`Select ${r.name || r.phone}`} className="h-4 w-4" checked={selected.includes(r.id)} onClick={(e) => e.stopPropagation()} onChange={() => toggle(r.id)} />,
    },
    {
      key: 'name',
      header: 'Contact',
      primary: true,
      render: (r) => (
        <div>
          <p className="font-medium text-ink">{r.name || 'No name'}</p>
          <p className="text-xs text-muted">{formatPhone(r.phone)}</p>
        </div>
      ),
    },
    {
      key: 'tags',
      header: 'Tags',
      render: (r) => (
        <div className="flex flex-wrap justify-end gap-1 md:justify-start">
          {(r.tags || []).slice(0, 4).map((t) => <Badge key={t} tone="brand">{t}</Badge>)}
          {(r.tags || []).length > 4 && <Badge tone="gray">+{r.tags.length - 4}</Badge>}
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Marketing',
      render: (r) => (r.optedOut ? <Badge tone="red" dot>Opted out</Badge> : r.isBlocked ? <Badge tone="red" dot>Blocked</Badge> : <Badge tone="green" dot>Subscribed</Badge>),
    },
    { key: 'lastInboundAt', header: 'Last message from them', hideOnMobile: true, render: (r) => <DateTime value={r.lastInboundAt} mode="relative" /> },
    {
      key: 'actions',
      header: '',
      align: 'right',
      hideOnMobile: true,
      render: (r) => <span onClick={(e) => e.stopPropagation()}><DropdownMenu trigger={({ toggle }) => <IconButton icon={MoreVertical} label="Actions" onClick={toggle} />} items={actions(r)} /></span>,
    },
  ];

  const c = confirm?.contact;
  return (
    <>
      <PageHeader
        title="Contacts"
        subtitle={q.data ? `${q.data.totalElements} contacts` : undefined}
        actions={
          <>
            <Button variant="secondary" leftIcon={Upload} to="/partner/contacts/import">Import</Button>
            <Button leftIcon={Plus} onClick={() => setDrawer({ open: true, contact: null })}>Add contact</Button>
          </>
        }
      />
      <ContactsSubnav />
      <FilterBar>
        <SearchInput value={f.search} onSearch={(v) => update({ search: v })} placeholder="Search name, number or email" />
        <Select aria-label="Tag" value={f.tag} onChange={(e) => update({ tag: e.target.value })} options={(tags.data || []).map((t) => ({ value: t.tag, label: `${t.tag} (${t.count})` }))} placeholder="All tags" />
        <Select aria-label="Group" value={f.groupId} onChange={(e) => update({ groupId: e.target.value })} options={(groups.data?.content || []).map((g) => ({ value: g.id, label: g.name }))} placeholder="All groups" />
        <Select aria-label="Marketing" value={f.optedOut} onChange={(e) => update({ optedOut: e.target.value })} options={[{ value: 'false', label: 'Subscribed' }, { value: 'true', label: 'Opted out' }]} placeholder="Everyone" />
      </FilterBar>
      {selected.length > 0 && (
        <div className="sticky top-0 z-10 mb-3 flex flex-wrap items-center gap-2 rounded-xl bg-brand-50 p-3 text-sm dark:bg-brand-500/10">
          <span className="font-medium">{selected.length} selected</span>
          <Button size="sm" variant="secondary" leftIcon={Users} onClick={() => setGroupModal(true)}>Add to group</Button>
          <Button size="sm" variant="danger-outline" leftIcon={Trash2} onClick={() => setConfirm({ kind: 'bulk' })}>Delete</Button>
          <Button size="sm" variant="ghost" onClick={() => setSelected([])}>Clear</Button>
        </div>
      )}
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(r) => setDrawer({ open: true, contact: r })}
        mobileActions={(r) => (
          <>
            <Button size="sm" variant="secondary" onClick={() => toggle(r.id)}>{selected.includes(r.id) ? 'Unselect' : 'Select'}</Button>
            <DropdownMenu trigger={<span className="inline-flex min-h-[36px] items-center rounded-lg border border-line px-3 text-sm">More</span>} items={actions(r)} />
          </>
        )}
        empty={
          <EmptyState
            icon={Users}
            title="No contacts yet"
            text="Add people one by one or import a CSV / Excel file."
            action={
              <>
                <Button onClick={() => setDrawer({ open: true, contact: null })}>Add contact</Button>
                <Button variant="secondary" to="/partner/contacts/import">Import file</Button>
              </>
            }
          />
        }
      />
      <ContactDrawer open={drawer.open} contact={drawer.contact} onClose={() => setDrawer({ open: false, contact: null })} />
      <ConfirmDialog
        open={confirm?.kind === 'bulk'}
        onClose={() => setConfirm(null)}
        onConfirm={() => bulkDelete.mutate(undefined, { onSuccess: () => setSelected([]) })}
        loading={bulkDelete.isPending}
        danger
        title={`Delete ${selected.length} contacts?`}
        message="Their message history stays in reports."
        confirmLabel="Delete"
      />
      <ConfirmDialog
        open={confirm?.kind === 'delete'}
        onClose={() => setConfirm(null)}
        onConfirm={() => del.mutate(c.id)}
        loading={del.isPending}
        danger
        title="Delete this contact?"
        message={c ? `${c.name || ''} ${formatPhone(c.phone)}` : ''}
        confirmLabel="Delete"
      />
      <ConfirmDialog
        open={confirm?.kind === 'optout'}
        onClose={() => setConfirm(null)}
        onConfirm={() => optOut.mutate(c.id)}
        loading={optOut.isPending}
        title="Stop marketing messages?"
        message="This contact will be skipped in marketing campaigns. Utility and authentication messages still work."
        confirmLabel="Opt out"
      >
        <Textarea label="Reason (optional)" rows={2} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} />
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm?.kind === 'optin'}
        onClose={() => setConfirm(null)}
        onConfirm={() => optIn.mutate(c.id)}
        loading={optIn.isPending}
        title="Opt this contact in again?"
        message="Only do this if the customer clearly asked to receive marketing messages again."
        confirmLabel="Yes, the customer agreed"
      />
      <Modal
        open={groupModal}
        onClose={() => setGroupModal(false)}
        title="Add to group"
        footer={
          <>
            <Button variant="secondary" onClick={() => setGroupModal(false)}>Cancel</Button>
            <Button onClick={() => addToGroup.mutate()} loading={addToGroup.isPending} disabled={!targetGroup}>Add {selected.length}</Button>
          </>
        }
      >
        {(groups.data?.content || []).length === 0 ? (
          <EmptyState compact title="No groups yet" action={<Button size="sm" to="/partner/contacts/groups">Create a group</Button>} />
        ) : (
          <Select label="Group" value={targetGroup} onChange={(e) => setTargetGroup(e.target.value)} options={(groups.data?.content || []).map((g) => ({ value: g.id, label: `${g.name} (${g.memberCount})` }))} placeholder="Choose a group" />
        )}
      </Modal>
    </>
  );
}
