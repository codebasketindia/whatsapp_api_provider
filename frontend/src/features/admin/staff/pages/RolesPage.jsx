import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import toast from 'react-hot-toast';
import { Lock, Pencil, Plus, ShieldCheck, Trash2 } from 'lucide-react';
import * as staffApi from '../../../../api/endpoints/staff';
import { getErrorMessage } from '../../../../api/axios';
import { humanize } from '../../../../lib/format';
import { Badge, Button, Card, Checkbox, ConfirmDialog, Drawer, EmptyState, ErrorState, Input, PageHeader, Skeleton, Textarea } from '../../../../components/ui';

const schema = z.object({
  code: z
    .string()
    .trim()
    .min(2, 'Enter a short code')
    .max(50)
    .regex(/^[A-Z][A-Z0-9_]*$/, 'Use CAPITAL letters, numbers and _ only (e.g. FINANCE)'),
  name: z.string().trim().min(2, 'Enter a name').max(100),
  description: z.string().max(255).optional(),
});

function groupPermissions(perms = []) {
  return perms.reduce((acc, p) => {
    const m = p.module || 'GENERAL';
    (acc[m] ||= []).push(p);
    return acc;
  }, {});
}

function RoleDrawer({ open, onClose, role, permissions }) {
  const qc = useQueryClient();
  const editing = Boolean(role);
  const [selected, setSelected] = useState(new Set());
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema), defaultValues: { code: '', name: '', description: '' } });

  useEffect(() => {
    if (!open) return;
    reset({ code: role?.code || '', name: role?.name || '', description: role?.description || '' });
    setSelected(new Set(role?.permissions || []));
  }, [open, role, reset]);

  const groups = useMemo(() => groupPermissions(permissions), [permissions]);

  const save = useMutation({
    mutationFn: (v) => {
      const body = { code: v.code.trim(), name: v.name.trim(), description: v.description || '', permissions: [...selected] };
      return editing ? staffApi.updateRole(role.id, body) : staffApi.createRole(body);
    },
    onSuccess: () => {
      toast.success(editing ? 'Role updated' : 'Role created');
      qc.invalidateQueries({ queryKey: ['admin', 'roles'] });
      onClose();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const toggle = (code) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });

  const toggleModule = (list, on) =>
    setSelected((prev) => {
      const next = new Set(prev);
      list.forEach((p) => (on ? next.add(p.code) : next.delete(p.code)));
      return next;
    });

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width="md:w-[560px]"
      title={editing ? `Edit role: ${role.name}` : 'New role'}
      description="Choose exactly what people with this role can see and do."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSubmit((v) => save.mutate(v))} loading={save.isPending} disabled={selected.size === 0}>
            {editing ? 'Save role' : 'Create role'}
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Role name" required placeholder="Finance team" error={errors.name} {...register('name')} />
          <Input label="Code" required placeholder="FINANCE" disabled={editing} error={errors.code} {...register('code')} />
        </div>
        <Textarea label="Description" rows={2} error={errors.description} {...register('description')} />
        <div>
          <p className="label-base">Permissions ({selected.size} selected)</p>
          <div className="space-y-3">
            {Object.entries(groups).map(([module, list]) => {
              const all = list.every((p) => selected.has(p.code));
              return (
                <div key={module} className="rounded-xl border border-line">
                  <div className="flex items-center justify-between border-b border-line bg-surface-2 px-3">
                    <span className="text-sm font-semibold text-ink">{humanize(module)}</span>
                    <button type="button" onClick={() => toggleModule(list, !all)} className="min-h-[40px] text-sm font-medium text-brand-700 dark:text-brand-300">
                      {all ? 'Clear' : 'Select all'}
                    </button>
                  </div>
                  <div className="px-3">
                    {list.map((p) => (
                      <Checkbox key={p.code} label={humanize(p.code)} description={p.description} checked={selected.has(p.code)} onChange={() => toggle(p.code)} />
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
          {selected.size === 0 && <p className="mt-2 text-xs text-red-600">Select at least one permission.</p>}
        </div>
        <button type="submit" className="hidden" aria-hidden />
      </form>
    </Drawer>
  );
}

export default function RolesPage() {
  const qc = useQueryClient();
  const rolesQ = useQuery({ queryKey: ['admin', 'roles'], queryFn: staffApi.listRoles });
  const permsQ = useQuery({ queryKey: ['admin', 'permissions'], queryFn: staffApi.listPermissions, staleTime: Infinity });
  const [drawer, setDrawer] = useState({ open: false, role: null });
  const [toDelete, setToDelete] = useState(null);

  const del = useMutation({
    mutationFn: (id) => staffApi.deleteRole(id),
    onSuccess: () => {
      toast.success('Role deleted');
      qc.invalidateQueries({ queryKey: ['admin', 'roles'] });
      setToDelete(null);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const roles = (rolesQ.data || []).filter((r) => r.code !== 'API_PARTNER' && r.userType !== 'PARTNER');
  const permissions = permsQ.data || [];
  const permLabel = (code) => humanize(code);

  return (
    <>
      <PageHeader
        title="Roles & permissions"
        subtitle="A role is a set of permissions. Assign roles to staff members."
        backTo="/admin/staff"
        backLabel="Staff"
        actions={
          <Button leftIcon={Plus} onClick={() => setDrawer({ open: true, role: null })} disabled={!permissions.length}>
            New role
          </Button>
        }
      />
      {rolesQ.isError || permsQ.isError ? (
        <ErrorState error={rolesQ.error || permsQ.error} onRetry={() => { rolesQ.refetch(); permsQ.refetch(); }} />
      ) : rolesQ.isLoading ? (
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="card space-y-3 p-5">
              <Skeleton className="h-5 w-1/3" />
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-4 w-1/2" />
            </div>
          ))}
        </div>
      ) : roles.length === 0 ? (
        <div className="card">
          <EmptyState icon={ShieldCheck} title="No roles yet" text="Create a role to give staff limited access." action={<Button leftIcon={Plus} onClick={() => setDrawer({ open: true, role: null })}>New role</Button>} />
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {roles.map((r) => {
            const fullAccess = r.code === 'SUPER_ADMIN';
            return (
              <Card
                key={r.id}
                title={
                  <span className="flex items-center gap-2">
                    {r.name}
                    {r.isSystem && <Badge tone="gray"><Lock className="h-3 w-3" /> System</Badge>}
                  </span>
                }
                subtitle={`${r.userCount ?? 0} staff · ${fullAccess ? 'all' : r.permissions?.length || 0} permissions`}
                actions={
                  !r.isSystem && (
                    <>
                      <Button size="sm" variant="secondary" leftIcon={Pencil} onClick={() => setDrawer({ open: true, role: r })}>
                        Edit
                      </Button>
                      <Button size="sm" variant="danger-outline" leftIcon={Trash2} onClick={() => setToDelete(r)} disabled={(r.userCount ?? 0) > 0} title={(r.userCount ?? 0) > 0 ? 'Move staff to another role first' : undefined}>
                        Delete
                      </Button>
                    </>
                  )
                }
              >
                {r.description && <p className="mb-3 text-sm text-muted">{r.description}</p>}
                {fullAccess ? (
                  <p className="text-sm text-ink">Full access to everything, including staff and settings.</p>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {(r.permissions || []).map((p) => (
                      <Badge key={p} tone="brand">
                        {permLabel(p)}
                      </Badge>
                    ))}
                    {!r.permissions?.length && <span className="text-sm text-muted">No permissions</span>}
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}
      <RoleDrawer open={drawer.open} role={drawer.role} permissions={permissions} onClose={() => setDrawer({ open: false, role: null })} />
      <ConfirmDialog
        open={Boolean(toDelete)}
        onClose={() => setToDelete(null)}
        onConfirm={() => del.mutate(toDelete.id)}
        loading={del.isPending}
        danger
        title={`Delete role "${toDelete?.name}"?`}
        message="This cannot be undone."
        confirmLabel="Delete role"
      />
    </>
  );
}
