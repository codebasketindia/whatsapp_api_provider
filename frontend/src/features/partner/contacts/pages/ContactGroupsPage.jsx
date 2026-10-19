import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2, UserMinus, UserPlus, Users } from 'lucide-react';
import toast from 'react-hot-toast';
import * as contactsApi from '../../../../api/endpoints/contacts';
import { getErrorMessage } from '../../../../api/axios';
import {
  Button, ConfirmDialog, DataTable, Drawer, EmptyState, IconButton, Input, Modal, PageHeader, Pagination, SearchInput, Skeleton, Textarea,
} from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatNumber, formatPhone } from '../../../../lib/format';
import ContactsSubnav from '../components/ContactsSubnav';

function GroupForm({ group, onClose }) {
  const qc = useQueryClient();
  const [name, setName] = useState(group?.name || '');
  const [description, setDescription] = useState(group?.description || '');
  const m = useMutation({
    mutationFn: () => (group?.id ? contactsApi.updateContactGroup(group.id, { name, description }) : contactsApi.createContactGroup({ name, description })),
    onSuccess: () => {
      toast.success(group?.id ? 'Group updated' : 'Group created');
      qc.invalidateQueries({ queryKey: ['partner', 'contact-groups'] });
      onClose();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  return (
    <Modal
      open
      onClose={onClose}
      title={group?.id ? 'Edit group' : 'New group'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={() => m.mutate()} loading={m.isPending} disabled={!name.trim()}>Save</Button>
        </>
      }
    >
      <div className="space-y-4">
        <Input label="Group name" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} required />
        <Textarea label="Description (optional)" rows={2} maxLength={255} value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
    </Modal>
  );
}

function MembersDrawer({ group, onClose }) {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState('');
  const members = useQuery({
    queryKey: ['partner', 'contacts', { groupId: group.id, page }],
    queryFn: () => contactsApi.listContacts({ groupId: group.id, page, size: 20 }),
    placeholderData: (p) => p,
  });
  const candidates = useQuery({
    queryKey: ['partner', 'contacts', { search, size: 10 }],
    queryFn: () => contactsApi.listContacts({ search, size: 10 }),
    enabled: adding,
  });
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['partner', 'contacts'] });
    qc.invalidateQueries({ queryKey: ['partner', 'contact-groups'] });
  };
  const add = useMutation({
    mutationFn: (id) => contactsApi.addGroupMembers(group.id, [id]),
    onSuccess: () => {
      toast.success('Added');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id) => contactsApi.removeGroupMembers(group.id, [id]),
    onSuccess: () => {
      toast.success('Removed from group');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const memberIds = new Set((members.data?.content || []).map((c) => c.id));
  return (
    <Drawer open onClose={onClose} title={group.name} description={`${formatNumber(group.memberCount)} members`}>
      <div className="mb-4 flex justify-end">
        <Button size="sm" variant={adding ? 'secondary' : 'primary'} leftIcon={UserPlus} onClick={() => setAdding((v) => !v)}>
          {adding ? 'Done adding' : 'Add people'}
        </Button>
      </div>
      {adding && (
        <div className="mb-5 rounded-xl border border-line p-3">
          <SearchInput value={search} onSearch={setSearch} placeholder="Search contacts to add" autoFocus />
          <ul className="mt-2 divide-y divide-line">
            {candidates.isLoading ? (
              <Skeleton className="h-10 w-full" />
            ) : (
              (candidates.data?.content || []).map((c) => (
                <li key={c.id} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <span className="min-w-0 truncate">{c.name || 'No name'} · {formatPhone(c.phone)}</span>
                  <Button size="sm" variant="secondary" disabled={memberIds.has(c.id)} loading={add.isPending && add.variables === c.id} onClick={() => add.mutate(c.id)}>
                    {memberIds.has(c.id) ? 'Added' : 'Add'}
                  </Button>
                </li>
              ))
            )}
          </ul>
        </div>
      )}
      {members.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : !members.data?.content?.length ? (
        <EmptyState compact icon={Users} title="No members yet" />
      ) : (
        <>
          <ul className="divide-y divide-line">
            {members.data.content.map((c) => (
              <li key={c.id} className="flex items-center justify-between gap-2 py-2">
                <div className="min-w-0 text-sm">
                  <p className="truncate font-medium text-ink">{c.name || 'No name'}</p>
                  <p className="text-xs text-muted">{formatPhone(c.phone)}</p>
                </div>
                <IconButton icon={UserMinus} label="Remove from group" variant="danger" onClick={() => remove.mutate(c.id)} loading={remove.isPending && remove.variables === c.id} />
              </li>
            ))}
          </ul>
          <Pagination page={members.data.page} totalPages={members.data.totalPages} totalElements={members.data.totalElements} onPageChange={setPage} />
        </>
      )}
    </Drawer>
  );
}

export default function ContactGroupsPage() {
  const qc = useQueryClient();
  const [f, update] = useUrlState({ page: 0, search: '' });
  const params = { page: f.page, size: 20, search: f.search };
  const q = useQuery({ queryKey: ['partner', 'contact-groups', params], queryFn: () => contactsApi.listContactGroups(params), placeholderData: (p) => p });
  const [form, setForm] = useState(null);
  const [members, setMembers] = useState(null);
  const [del, setDel] = useState(null);
  const remove = useMutation({
    mutationFn: (id) => contactsApi.deleteContactGroup(id),
    onSuccess: () => {
      toast.success('Group deleted. Contacts are kept.');
      setDel(null);
      qc.invalidateQueries({ queryKey: ['partner', 'contact-groups'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const columns = [
    {
      key: 'name',
      header: 'Group',
      primary: true,
      render: (r) => (
        <div>
          <p className="font-medium text-ink">{r.name}</p>
          {r.description && <p className="text-xs text-muted">{r.description}</p>}
        </div>
      ),
    },
    { key: 'memberCount', header: 'Members', align: 'right', render: (r) => formatNumber(r.memberCount) },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (r) => (
        <span className="inline-flex gap-1" onClick={(e) => e.stopPropagation()}>
          <IconButton icon={Pencil} label="Edit group" onClick={() => setForm(r)} />
          <IconButton icon={Trash2} label="Delete group" variant="danger" onClick={() => setDel(r)} />
        </span>
      ),
    },
  ];
  return (
    <>
      <PageHeader title="Groups" subtitle="Organise contacts to send campaigns to the right people" actions={<Button leftIcon={Plus} onClick={() => setForm({})}>New group</Button>} />
      <ContactsSubnav />
      <div className="mb-4 max-w-md">
        <SearchInput value={f.search} onSearch={(v) => update({ search: v })} placeholder="Search groups" />
      </div>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(r) => setMembers(r)}
        empty={<EmptyState icon={Users} title="No groups yet" text="For example: VIP customers, Mumbai store, Wholesale." action={<Button onClick={() => setForm({})}>Create group</Button>} />}
      />
      {form && <GroupForm group={form} onClose={() => setForm(null)} />}
      {members && <MembersDrawer group={members} onClose={() => setMembers(null)} />}
      <ConfirmDialog
        open={Boolean(del)}
        onClose={() => setDel(null)}
        onConfirm={() => remove.mutate(del.id)}
        loading={remove.isPending}
        danger
        title={`Delete group "${del?.name}"?`}
        message="The contacts themselves are not deleted."
        confirmLabel="Delete group"
      />
    </>
  );
}
