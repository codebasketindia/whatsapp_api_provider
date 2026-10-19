import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Ban, UserCheck } from 'lucide-react';
import toast from 'react-hot-toast';
import { formatPhone } from '../../../../../lib/format';
import { Alert, Button, Card, ConfirmDialog, DataTable, DateTime, EmptyState, StatusBadge, Textarea } from '../../../../../components/ui';
import { useApiMutation } from '../../../shared/useApiMutation';

const parseNumbers = (text) =>
  Array.from(new Set(text.split(/[\s,;]+/).map((x) => x.replace(/\D/g, '')).filter((x) => x.length >= 8 && x.length <= 15)));

/** Block / unblock WhatsApp users (T19–T21). Meta only allows blocking users who messaged in the last 24 hours. */
export default function BlockListTab({ sender, tools, canManage }) {
  const key = [tools.scope, 'senders', sender.id, 'blocked'];
  const [page, setPage] = useState(0);
  const [text, setText] = useState('');
  const [unblock, setUnblock] = useState(null);
  const q = useQuery({ queryKey: [...key, page], queryFn: () => tools.listBlockedUsers(sender.id, { page, size: 20 }), placeholderData: (p) => p });
  const report = (r, verb) => {
    const ok = (r?.blocked || r?.unblocked || []).length;
    const failed = r?.failed || [];
    if (ok) toast.success(`${ok} number(s) ${verb}`);
    failed.forEach((f) => toast.error(`${f.waId}: ${f.reason}`));
  };
  const blockM = useApiMutation({ mutationFn: (ids) => tools.blockUsers(sender.id, ids), invalidate: [key], onSuccess: (r) => { report(r, 'blocked'); setText(''); } });
  const unblockM = useApiMutation({ mutationFn: (ids) => tools.unblockUsers(sender.id, ids), invalidate: [key], onSuccess: (r) => { report(r, 'unblocked'); setUnblock(null); } });
  const nums = parseNumbers(text);
  return (
    <div className="space-y-4">
      {canManage && (
        <Card title="Block numbers">
          <Alert variant="info" className="mb-3">WhatsApp only lets you block people who messaged this number in the last 24 hours. Blocked people cannot message you and you cannot message them.</Alert>
          <Textarea label="Phone numbers with country code" rows={3} placeholder="919876543210, 919812345678" value={text} onChange={(e) => setText(e.target.value)} hint={`${nums.length} valid number(s)`} />
          <Button className="mt-3" variant="danger" leftIcon={Ban} disabled={!nums.length} loading={blockM.isPending} onClick={() => blockM.mutate(nums)}>Block {nums.length || ''}</Button>
        </Card>
      )}
      <Card title="Blocked users" padded={false} bodyClassName="p-4">
        <DataTable
          columns={[
            { key: 'waId', header: 'Number', primary: true, render: (b) => formatPhone(b.waId) },
            { key: 'status', header: 'Status', render: (b) => <StatusBadge status={b.status} /> },
            { key: 'blockedAt', header: 'Blocked', render: (b) => <DateTime value={b.blockedAt} /> },
            { key: 'a', header: <span className="sr-only">Unblock</span>, align: 'right', hideOnMobile: true, render: (b) => canManage && b.status === 'BLOCKED' && <Button size="sm" variant="secondary" leftIcon={UserCheck} onClick={() => setUnblock(b)}>Unblock</Button> },
          ]}
          page={q.data}
          loading={q.isLoading}
          error={q.error}
          onRetry={q.refetch}
          rowKey={(b) => b.waId}
          onPageChange={setPage}
          mobileActions={(b) => canManage && b.status === 'BLOCKED' && <Button size="sm" variant="secondary" leftIcon={UserCheck} onClick={() => setUnblock(b)}>Unblock</Button>}
          empty={<EmptyState compact icon={Ban} title="Nobody is blocked" />}
        />
      </Card>
      <ConfirmDialog open={Boolean(unblock)} onClose={() => setUnblock(null)} title={`Unblock ${formatPhone(unblock?.waId)}?`} message="They will be able to message this number again." confirmLabel="Unblock" loading={unblockM.isPending} onConfirm={() => unblockM.mutate([unblock.waId])} />
    </div>
  );
}
