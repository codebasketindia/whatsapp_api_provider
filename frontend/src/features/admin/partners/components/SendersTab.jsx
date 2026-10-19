import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Plus, Smartphone, Unlink } from 'lucide-react';
import * as partnersApi from '../../../../api/endpoints/partners';
import * as metaApi from '../../../../api/endpoints/meta';
import { useAuth } from '../../../../auth/useAuth';
import { humanize } from '../../../../lib/format';
import QualityBadge from '../../../../components/whatsapp/QualityBadge';
import {
  Badge, Button, Card, Checkbox, ConfirmDialog, DataTable, EmptyState, Modal, Select, StatusBadge,
} from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';

export default function SendersTab({ partner }) {
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('WHATSAPP_MANAGE');
  const [assignOpen, setAssignOpen] = useState(false);
  const [remove, setRemove] = useState(null);
  const q = useQuery({ queryKey: ['admin', 'partners', 'senders', partner.id], queryFn: () => partnersApi.listPartnerSenders(partner.id) });

  const removeM = useApiMutation({
    mutationFn: (s) => partnersApi.removePartnerSender(partner.id, s.id),
    success: 'Number removed from this partner. Message history is kept.',
    invalidate: [['admin', 'partners'], ['admin', 'senders']],
    onSuccess: () => setRemove(null),
  });

  const columns = [
    {
      key: 'number',
      header: 'Number',
      primary: true,
      render: (s) => (
        <span>
          <span className="block font-medium text-ink">{s.displayPhoneNumber}</span>
          <span className="block text-xs text-muted">{s.verifiedName || 'No display name yet'}</span>
        </span>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (s) => (
        <span className="flex flex-wrap gap-1">
          <Badge tone={s.senderType === 'PLATFORM_SENDER' ? 'brand' : 'blue'}>{s.senderType === 'PLATFORM_SENDER' ? 'Platform number' : 'Own number'}</Badge>
          {s.isDefault && <Badge tone="green">Default</Badge>}
        </span>
      ),
    },
    { key: 'quality', header: 'Quality', render: (s) => <QualityBadge rating={s.qualityRating} /> },
    { key: 'status', header: 'Status', render: (s) => (s.isPaused ? <StatusBadge status="PAUSED" /> : <StatusBadge status={s.status} />) },
    { key: 'tier', header: 'Messaging limit', hideOnMobile: true, render: (s) => humanize(s.messagingLimitTier) || '—' },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      align: 'right',
      hideOnMobile: true,
      render: (s) =>
        canManage && s.senderType === 'PLATFORM_SENDER' ? (
          <Button size="sm" variant="danger-outline" leftIcon={Unlink} onClick={(e) => { e.stopPropagation(); setRemove(s); }}>Remove</Button>
        ) : null,
    },
  ];

  return (
    <Card
      title="WhatsApp numbers"
      subtitle="Platform numbers assigned by you and numbers the partner connected."
      actions={canManage && <Button size="sm" leftIcon={Plus} onClick={() => setAssignOpen(true)}>Assign platform number</Button>}
    >
      <DataTable
        columns={columns}
        rows={q.data || []}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onRowClick={(s) => navigate(`/admin/whatsapp/senders/${s.id}`)}
        mobileActions={(s) =>
          canManage && s.senderType === 'PLATFORM_SENDER' ? (
            <Button size="sm" variant="danger-outline" leftIcon={Unlink} onClick={() => setRemove(s)}>Remove</Button>
          ) : null
        }
        empty={
          <EmptyState
            icon={Smartphone}
            title="No numbers yet"
            text={partner.allowOwnNumber ? 'Assign one of your platform numbers, or the partner can connect their own.' : 'Assign one of your platform numbers so this partner can send messages.'}
            action={canManage && <Button leftIcon={Plus} onClick={() => setAssignOpen(true)}>Assign platform number</Button>}
          />
        }
      />
      <AssignSenderModal partner={partner} open={assignOpen} onClose={() => setAssignOpen(false)} />
      <ConfirmDialog
        open={Boolean(remove)}
        onClose={() => setRemove(null)}
        danger
        title={`Remove ${remove?.displayPhoneNumber}?`}
        message="The partner can no longer send from this number. Inbound messages will stop reaching them. Past messages stay in the logs."
        confirmLabel="Remove number"
        loading={removeM.isPending}
        onConfirm={() => removeM.mutate(remove)}
      />
    </Card>
  );
}

function AssignSenderModal({ partner, open, onClose }) {
  const [senderId, setSenderId] = useState('');
  const [isDefault, setIsDefault] = useState(true);
  const q = useQuery({
    queryKey: ['admin', 'senders', 'unassigned'],
    queryFn: () => metaApi.listSenders({ senderType: 'PLATFORM_SENDER', assigned: false, status: 'CONNECTED', size: 100 }),
    enabled: open,
  });
  const options = (q.data?.content || []).map((s) => ({ value: String(s.id), label: `${s.displayPhoneNumber} · ${s.verifiedName || 'no name'}` }));
  const m = useApiMutation({
    mutationFn: () => partnersApi.assignPartnerSender(partner.id, { phoneNumberDbId: Number(senderId), isDefault }),
    success: 'Number assigned. The partner can send from it now.',
    invalidate: [['admin', 'partners'], ['admin', 'senders']],
    onSuccess: () => { setSenderId(''); onClose(); },
  });
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Assign a number to ${partner.businessName}`}
      description="Only connected platform numbers that are not used by another partner are listed."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button disabled={!senderId} loading={m.isPending} onClick={() => m.mutate()}>Assign</Button>
        </>
      }
    >
      <Select
        label="Platform number"
        value={senderId}
        onChange={(e) => setSenderId(e.target.value)}
        placeholder={q.isLoading ? 'Loading…' : options.length ? 'Choose a number' : 'No free platform numbers'}
        options={options}
      />
      <Checkbox className="mt-2" label="Make it the partner's default number" checked={isDefault} onChange={(e) => setIsDefault(e.target.checked)} />
    </Modal>
  );
}
