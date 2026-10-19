import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { KeyRound } from 'lucide-react';
import * as partnersApi from '../../../../api/endpoints/partners';
import { useAuth } from '../../../../auth/useAuth';
import { Badge, Button, Card, ConfirmDialog, DataTable, DateTime, EmptyState, Input, Modal, StatusBadge } from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';

export default function ApiTab({ partner }) {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('PARTNER_MANAGE');
  const [toggle, setToggle] = useState(null);
  const [limitKey, setLimitKey] = useState(null);
  const [limit, setLimit] = useState('');
  const q = useQuery({ queryKey: ['admin', 'partners', 'api-keys', partner.id], queryFn: () => partnersApi.listPartnerApiKeys(partner.id) });
  const m = useApiMutation({
    mutationFn: ({ key, body }) => partnersApi.updatePartnerApiKey(partner.id, key.id, body),
    success: 'API key updated',
    invalidate: [['admin', 'partners', 'api-keys', partner.id]],
    onSuccess: () => { setToggle(null); setLimitKey(null); },
  });
  const columns = [
    {
      key: 'name',
      header: 'Key',
      primary: true,
      render: (k) => (
        <span>
          <span className="block font-medium text-ink">{k.name}</span>
          <span className="block font-mono text-xs text-muted">{k.apiKey} · secret ••••{k.secretLast4}</span>
        </span>
      ),
    },
    { key: 'status', header: 'Status', render: (k) => <StatusBadge status={k.status} /> },
    { key: 'limit', header: 'Rate limit', render: (k) => (k.rateLimitPerMinute ? `${k.rateLimitPerMinute}/min` : <Badge tone="gray">Platform default</Badge>) },
    { key: 'ips', header: 'IP whitelist', hideOnMobile: true, render: (k) => (k.ipWhitelist?.length ? k.ipWhitelist.join(', ') : 'Any IP') },
    { key: 'lastUsedAt', header: 'Last used', hideOnMobile: true, render: (k) => (k.lastUsedAt ? <span><DateTime value={k.lastUsedAt} mode="relative" /><span className="block text-xs text-muted">{k.lastUsedIp}</span></span> : 'Never') },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      align: 'right',
      hideOnMobile: true,
      render: (k) => canManage && <KeyActions k={k} onToggle={setToggle} onLimit={(key) => { setLimitKey(key); setLimit(key.rateLimitPerMinute ? String(key.rateLimitPerMinute) : ''); }} />,
    },
  ];
  return (
    <Card title="API keys" subtitle="Keys the partner created for the public REST API. Secrets are never shown to admins.">
      <DataTable
        columns={columns}
        rows={q.data || []}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        mobileActions={(k) => canManage && <KeyActions k={k} onToggle={setToggle} onLimit={(key) => { setLimitKey(key); setLimit(key.rateLimitPerMinute ? String(key.rateLimitPerMinute) : ''); }} />}
        empty={<EmptyState icon={KeyRound} title="No API keys" text="The partner can create keys from their Developer page." />}
      />
      <ConfirmDialog
        open={Boolean(toggle)}
        onClose={() => setToggle(null)}
        danger={toggle?.status === 'ACTIVE'}
        title={toggle?.status === 'ACTIVE' ? 'Disable this API key?' : 'Enable this API key?'}
        message={toggle?.status === 'ACTIVE' ? 'All API calls using this key will be rejected immediately.' : 'API calls with this key will work again.'}
        confirmLabel={toggle?.status === 'ACTIVE' ? 'Disable key' : 'Enable key'}
        loading={m.isPending}
        onConfirm={() => m.mutate({ key: toggle, body: { status: toggle.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' } })}
      />
      <Modal
        open={Boolean(limitKey)}
        onClose={() => setLimitKey(null)}
        title="Rate limit for this key"
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setLimitKey(null)}>Cancel</Button>
            <Button
              loading={m.isPending}
              disabled={limit !== '' && !(Number(limit) >= 1 && Number(limit) <= 100000)}
              onClick={() => m.mutate({ key: limitKey, body: { rateLimitPerMinute: limit === '' ? null : Number(limit) } })}
            >
              Save
            </Button>
          </>
        }
      >
        <Input
          label="Requests per minute"
          inputMode="numeric"
          value={limit}
          onChange={(e) => setLimit(e.target.value.replace(/\D/g, ''))}
          placeholder="Use platform default"
          hint="Leave empty to use the platform default from Settings → API limits."
        />
      </Modal>
    </Card>
  );
}

function KeyActions({ k, onToggle, onLimit }) {
  return (
    <span className="flex justify-end gap-2">
      <Button size="sm" variant="secondary" onClick={(e) => { e.stopPropagation(); onLimit(k); }}>Rate limit</Button>
      <Button size="sm" variant={k.status === 'ACTIVE' ? 'danger-outline' : 'secondary'} onClick={(e) => { e.stopPropagation(); onToggle(k); }}>
        {k.status === 'ACTIVE' ? 'Disable' : 'Enable'}
      </Button>
    </span>
  );
}
