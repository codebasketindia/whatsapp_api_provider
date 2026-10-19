import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link2, Phone, Plus } from 'lucide-react';
import * as metaApi from '../../../../api/endpoints/meta';
import PermissionGate from '../../../../auth/PermissionGate';
import { useUrlState } from '../../../../hooks/useUrlState';
import { humanize } from '../../../../lib/format';
import { glossary } from '../../../../lib/metaGlossary';
import { Badge, Button, DataTable, DateTime, Drawer, EmptyState, FilterBar, Input, PageHeader, PasswordInput, Select, StatusBadge } from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';

export default function WabaListPage() {
  const navigate = useNavigate();
  const [state, update] = useUrlState({ page: 0, ownerType: '', status: '', tokenStatus: '' });
  const [addOpen, setAddOpen] = useState(false);
  const q = useQuery({ queryKey: ['admin', 'wabas', state], queryFn: () => metaApi.listWabas({ ...state, size: 20 }), placeholderData: (p) => p });
  const columns = [
    {
      key: 'name',
      header: 'Account',
      primary: true,
      render: (w) => (
        <span>
          <span className="block font-medium text-ink">{w.name || 'Unnamed account'}</span>
          <span className="block font-mono text-xs text-muted">WABA {w.wabaId}</span>
        </span>
      ),
    },
    { key: 'owner', header: 'Owner', render: (w) => (w.ownerType === 'PLATFORM' ? <Badge tone="brand">Our account</Badge> : <span>{w.partner?.businessName || 'Partner'}</span>) },
    { key: 'billing', header: 'Billing', hideOnMobile: true, render: (w) => humanize(w.billingMode) },
    { key: 'token', header: 'Access token', render: (w) => <StatusBadge status={w.tokenStatus} /> },
    { key: 'review', header: 'Account review', hideOnMobile: true, render: (w) => (w.accountReviewStatus ? <StatusBadge status={w.accountReviewStatus} /> : '—') },
    { key: 'status', header: 'Status', render: (w) => <StatusBadge status={w.status} /> },
    { key: 'connectedAt', header: 'Connected', hideOnMobile: true, render: (w) => <DateTime value={w.connectedAt} mode="date" /> },
  ];
  return (
    <>
      <PageHeader
        title="WhatsApp accounts"
        subtitle="Our WhatsApp Business Accounts (WABAs) and those connected by partners."
        actions={
          <PermissionGate permission="WHATSAPP_MANAGE">
            <Button variant="secondary" leftIcon={Link2} to="/admin/whatsapp/connect">Manual connect</Button>
            <Button leftIcon={Plus} onClick={() => setAddOpen(true)}>Add our account</Button>
          </PermissionGate>
        }
      />
      <FilterBar>
        <Select aria-label="Owner" value={state.ownerType} onChange={(e) => update({ ownerType: e.target.value })} placeholder="All owners" options={[{ value: 'PLATFORM', label: 'Our accounts' }, { value: 'PARTNER', label: 'Partner accounts' }]} />
        <Select aria-label="Status" value={state.status} onChange={(e) => update({ status: e.target.value })} placeholder="All statuses" options={['ACTIVE', 'RESTRICTED', 'DISABLED', 'DISCONNECTED'].map((s) => ({ value: s, label: humanize(s) }))} />
        <Select aria-label="Token" value={state.tokenStatus} onChange={(e) => update({ tokenStatus: e.target.value })} placeholder="Any token status" options={['VALID', 'INVALID', 'EXPIRED', 'UNKNOWN'].map((s) => ({ value: s, label: humanize(s) }))} />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={(w) => navigate(`/admin/whatsapp/accounts/${w.id}`)}
        empty={<EmptyState icon={Phone} title="No WhatsApp accounts yet" text="Add your own WABA with a system-user token, or wait for partners to connect theirs." action={<PermissionGate permission="WHATSAPP_MANAGE"><Button leftIcon={Plus} onClick={() => setAddOpen(true)}>Add our account</Button></PermissionGate>} />}
      />
      <AddWabaDrawer open={addOpen} onClose={() => setAddOpen(false)} onDone={(w) => navigate(`/admin/whatsapp/accounts/${w.id}`)} />
    </>
  );
}

const schema = z.object({
  wabaId: z.string().trim().regex(/^\d{5,32}$/, 'WABA ID is a number from WhatsApp Manager'),
  accessToken: z.string().trim().min(20, 'Paste the permanent system-user access token'),
});

function AddWabaDrawer({ open, onClose, onDone }) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm({ resolver: zodResolver(schema), defaultValues: { wabaId: '', accessToken: '' } });
  const m = useApiMutation({
    mutationFn: (v) => metaApi.addPlatformWaba(v),
    success: (w) => `Account added with ${w?.senders?.length ?? 0} numbers`,
    invalidate: [['admin', 'wabas'], ['admin', 'senders']],
    onSuccess: (w) => { reset(); onClose(); onDone(w); },
  });
  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Add our WhatsApp account"
      description="We check the token, import its phone numbers as platform numbers and subscribe the account to webhooks."
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={m.isPending} onClick={handleSubmit((v) => m.mutate(v))}>Add account</Button></>}
    >
      <form className="space-y-4" onSubmit={handleSubmit((v) => m.mutate(v))} noValidate>
        <Input label="WhatsApp Business Account ID" info={glossary('WABA')} required error={errors.wabaId} {...register('wabaId')} />
        <PasswordInput label="System-user access token" required autoComplete="off" hint="Create it in Business Settings → System users with whatsapp_business_management and whatsapp_business_messaging. It is stored encrypted and never shown again." error={errors.accessToken} {...register('accessToken')} />
        <button type="submit" className="hidden" aria-hidden />
      </form>
    </Drawer>
  );
}
