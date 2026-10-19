import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Ban } from 'lucide-react';
import toast from 'react-hot-toast';
import { getErrorMessage } from '../../../../../api/axios';
import { Alert, Button, ConfirmDialog, DataTable, DateTime, EmptyState, Modal, StatusBadge, Textarea } from '../../../../../components/ui';
import { formatPhone } from '../../../../../lib/format';

const parseNumbers = (text) =>
  Array.from(new Set(text.split(/[\s,;]+/).map((s) => s.replace(/\D/g, '')).filter(Boolean).map((d) => (d.length === 10 ? `91${d}` : d))));

/** Blocked WhatsApp users (T19–T21). */
export default function BlockListTab({ tools, sender, readOnly }) {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const key = [tools.scope, 'senders', sender.id, 'blocked', page];
  const q = useQuery({ queryKey: key, queryFn: () => tools.listBlockedUsers(sender.id, { page, size: 20 }), placeholderData: (p) => p });
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [unblock, setUnblock] = useState(null);
  const refresh = () => qc.invalidateQueries({ queryKey: [tools.scope, 'senders', sender.id, 'blocked'] });
  const block = useMutation({
    mutationFn: () => tools.blockUsers(sender.id, parseNumbers(text)),
    onSuccess: (res) => {
      const ok = res?.blocked?.length || 0;
      const failed = res?.failed || [];
      if (ok) toast.success(`${ok} blocked`);
      if (failed.length) toast.error(`${failed.length} could not be blocked: ${failed[0].reason || 'Meta refused'}`);
      setOpen(false);
      setText('');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const doUnblock = useMutation({
    mutationFn: (waId) => tools.unblockUsers(sender.id, [waId]),
    onSuccess: () => {
      toast.success('Unblocked');
      setUnblock(null);
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const columns = [
    { key: 'waId', header: 'Number', primary: true, render: (r) => formatPhone(r.waId) },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
    { key: 'blockedAt', header: 'Blocked on', render: (r) => <DateTime value={r.blockedAt} /> },
    {
      key: 'actions',
      header: '',
      align: 'right',
      hideOnMobile: true,
      render: (r) => !readOnly && r.status === 'BLOCKED' && <Button size="sm" variant="secondary" onClick={() => setUnblock(r.waId)}>Unblock</Button>,
    },
  ];
  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted">Blocked people cannot message you and you cannot message them.</p>
        {!readOnly && <Button leftIcon={Ban} variant="danger-outline" onClick={() => setOpen(true)}>Block numbers</Button>}
      </div>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={setPage}
        rowKey={(r) => r.waId}
        mobileActions={(r) => !readOnly && r.status === 'BLOCKED' && <Button size="sm" variant="secondary" onClick={() => setUnblock(r.waId)}>Unblock</Button>}
        empty={<EmptyState compact icon={Ban} title="Nobody is blocked" />}
      />
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Block numbers"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button variant="danger" onClick={() => block.mutate()} loading={block.isPending} disabled={!parseNumbers(text).length}>
              Block {parseNumbers(text).length || ''}
            </Button>
          </>
        }
      >
        <Alert variant="info" className="mb-3">WhatsApp only lets you block people who messaged you in the last 24 hours.</Alert>
        <Textarea label="Phone numbers" rows={4} value={text} onChange={(e) => setText(e.target.value)} hint="One per line or separated by commas. 10-digit numbers are treated as Indian (+91)." />
      </Modal>
      <ConfirmDialog
        open={Boolean(unblock)}
        onClose={() => setUnblock(null)}
        onConfirm={() => doUnblock.mutate(unblock)}
        loading={doUnblock.isPending}
        title="Unblock this number?"
        message={unblock ? formatPhone(unblock) : ''}
        confirmLabel="Unblock"
      />
    </>
  );
}
