import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { KeyRound, MoreVertical, Plus } from 'lucide-react';
import toast from 'react-hot-toast';
import * as developerApi from '../../../../api/endpoints/developer';
import { getErrorMessage } from '../../../../api/axios';
import {
  Alert, Button, ConfirmDialog, CopyButton, DataTable, DateTime, DropdownMenu, EmptyState, IconButton, Input, Modal, PasswordInput, StatusBadge,
} from '../../../../components/ui';
import TagInput from '../../common/TagInput';

const IP_RE = /^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$|^[0-9a-fA-F:]{2,39}$/;

function SecretModal({ data, onClose }) {
  return (
    <Modal open={Boolean(data)} onClose={onClose} title="Save your API secret now" dismissible footer={<Button onClick={onClose}>I have saved it</Button>}>
      {data && (
        <div className="space-y-4">
          <Alert variant="warning">This secret is shown only once. Store it safely (for example in your server's environment variables). If you lose it, generate a new one.</Alert>
          <div>
            <p className="label-base">API key</p>
            <div className="flex items-center gap-2 rounded-lg bg-surface-2 p-3 font-mono text-sm"><span className="min-w-0 flex-1 break-all">{data.apiKey}</span><CopyButton value={data.apiKey} /></div>
          </div>
          <div>
            <p className="label-base">API secret</p>
            <div className="flex items-center gap-2 rounded-lg bg-surface-2 p-3 font-mono text-sm"><span className="min-w-0 flex-1 break-all">{data.apiSecret}</span><CopyButton value={data.apiSecret} toastMessage="Secret copied" /></div>
          </div>
        </div>
      )}
    </Modal>
  );
}

function KeyForm({ initial, onClose, onCreated }) {
  const qc = useQueryClient();
  const [name, setName] = useState(initial?.name || '');
  const [ips, setIps] = useState(initial?.ipWhitelist || []);
  const badIp = ips.find((ip) => !IP_RE.test(ip));
  const m = useMutation({
    mutationFn: () => (initial?.id ? developerApi.updateApiKey(initial.id, { name, ipWhitelist: ips }) : developerApi.createApiKey({ name, ipWhitelist: ips })),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['partner', 'api-keys'] });
      toast.success(initial?.id ? 'Key updated' : 'Key created');
      onClose();
      if (!initial?.id) onCreated(data);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  return (
    <Modal
      open
      onClose={onClose}
      title={initial?.id ? 'Edit API key' : 'New API key'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={() => m.mutate()} loading={m.isPending} disabled={!name.trim() || Boolean(badIp)}>{initial?.id ? 'Save' : 'Create key'}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <Input label="Name" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} hint="For example: Website, CRM, Billing software" required />
        <TagInput label="Allowed IP addresses (optional)" value={ips} onChange={setIps} max={20} placeholder="203.0.113.10" hint={badIp ? `"${badIp}" is not a valid IP address` : 'Leave empty to allow calls from any IP. Recommended: add your server IPs.'} />
      </div>
    </Modal>
  );
}

export default function ApiKeysTab() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['partner', 'api-keys'], queryFn: developerApi.listApiKeys });
  const [form, setForm] = useState(null);
  const [secret, setSecret] = useState(null);
  const [regen, setRegen] = useState(null);
  const [password, setPassword] = useState('');
  const [del, setDel] = useState(null);
  const refresh = () => qc.invalidateQueries({ queryKey: ['partner', 'api-keys'] });
  const toggle = useMutation({
    mutationFn: (k) => developerApi.updateApiKey(k.id, { status: k.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }),
    onSuccess: (k) => {
      toast.success(k.status === 'ACTIVE' ? 'Key enabled' : 'Key disabled');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const regenerate = useMutation({
    mutationFn: () => developerApi.regenerateApiKey(regen.id, password),
    onSuccess: (data) => {
      setRegen(null);
      setPassword('');
      setSecret(data);
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id) => developerApi.deleteApiKey(id),
    onSuccess: () => {
      toast.success('Key deleted');
      setDel(null);
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const keys = q.data || [];
  const menu = (k) => [
    { label: 'Edit name / IPs', onClick: () => setForm(k) },
    { label: k.status === 'ACTIVE' ? 'Disable' : 'Enable', onClick: () => toggle.mutate(k) },
    { label: 'Generate new secret', onClick: () => setRegen(k) },
    { label: 'Delete', danger: true, onClick: () => setDel(k) },
  ];
  const columns = [
    {
      key: 'name',
      header: 'Key',
      primary: true,
      render: (k) => (
        <div>
          <p className="font-medium text-ink">{k.name}</p>
          <p className="flex items-center gap-1 font-mono text-xs text-muted">{k.apiKey} <CopyButton value={k.apiKey} /></p>
        </div>
      ),
    },
    { key: 'secret', header: 'Secret', render: (k) => <span className="font-mono text-xs">••••{k.secretLast4}</span> },
    { key: 'ips', header: 'Allowed IPs', render: (k) => (k.ipWhitelist?.length ? k.ipWhitelist.join(', ') : 'Any') },
    { key: 'status', header: 'Status', render: (k) => <StatusBadge status={k.status} /> },
    { key: 'used', header: 'Last used', render: (k) => (k.lastUsedAt ? <span><DateTime value={k.lastUsedAt} mode="relative" />{k.lastUsedIp ? ` · ${k.lastUsedIp}` : ''}</span> : 'Never') },
    {
      key: 'actions',
      header: '',
      align: 'right',
      hideOnMobile: true,
      render: (k) => <DropdownMenu trigger={({ toggle: t }) => <IconButton icon={MoreVertical} label="Key actions" onClick={t} />} items={menu(k)} />,
    },
  ];
  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted">Use a key and secret in the X-API-KEY and X-API-SECRET headers. Up to 5 keys.</p>
        <Button leftIcon={Plus} onClick={() => setForm({})} disabled={keys.length >= 5}>New API key</Button>
      </div>
      <DataTable
        columns={columns}
        rows={keys}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        mobileActions={(k) => <DropdownMenu trigger={<span className="inline-flex min-h-[36px] items-center rounded-lg border border-line px-3 text-sm">Actions</span>} items={menu(k)} align="left" />}
        empty={<EmptyState icon={KeyRound} title="No API keys yet" text="Create a key to send messages from your own software." action={<Button onClick={() => setForm({})}>Create API key</Button>} />}
      />
      {form && <KeyForm initial={form} onClose={() => setForm(null)} onCreated={setSecret} />}
      <SecretModal data={secret} onClose={() => setSecret(null)} />
      <Modal
        open={Boolean(regen)}
        onClose={() => setRegen(null)}
        title="Generate a new secret?"
        description="The old secret stops working immediately. Update your software right after."
        footer={
          <>
            <Button variant="secondary" onClick={() => setRegen(null)}>Cancel</Button>
            <Button variant="danger" onClick={() => regenerate.mutate()} loading={regenerate.isPending} disabled={!password}>Generate</Button>
          </>
        }
      >
        <PasswordInput label="Your login password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
      </Modal>
      <ConfirmDialog
        open={Boolean(del)}
        onClose={() => setDel(null)}
        onConfirm={() => remove.mutate(del.id)}
        loading={remove.isPending}
        danger
        title={`Delete "${del?.name}"?`}
        message="Software using this key will stop working."
        confirmWord="DELETE"
        confirmLabel="Delete key"
      />
    </>
  );
}
