import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download, Pencil, Plus, QrCode, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { getErrorMessage } from '../../../../../api/axios';
import { Button, ConfirmDialog, CopyButton, EmptyState, Modal, QueryState, Skeleton, Textarea } from '../../../../../components/ui';
import { useApiMutation } from '../../../shared/useApiMutation';

/** QR codes & short links with a pre-filled message (T12–T16). */
export default function QrCodesTab({ sender, tools, canManage }) {
  const key = [tools.scope, 'senders', sender.id, 'qr-codes'];
  const q = useQuery({ queryKey: key, queryFn: () => tools.listQrCodes(sender.id) });
  const [edit, setEdit] = useState(null); // {} for new or qr
  const [del, setDel] = useState(null);
  const delM = useApiMutation({ mutationFn: (qr) => tools.deleteQrCode(sender.id, qr.id), success: 'QR code deleted', invalidate: [key], onSuccess: () => setDel(null) });
  return (
    <>
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted">Customers scan the code or tap the link to open a chat with this message already typed.</p>
        {canManage && <Button leftIcon={Plus} onClick={() => setEdit({})}>New QR code</Button>}
      </div>
      <QueryState query={q}>
        {(list) =>
          !list?.length ? (
            <div className="card"><EmptyState icon={QrCode} title="No QR codes yet" text="Create one for your shop counter, website or packaging." action={canManage && <Button leftIcon={Plus} onClick={() => setEdit({})}>New QR code</Button>} /></div>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {list.map((qr) => (
                <div key={qr.id} className="card flex flex-col p-4">
                  <QrImage tools={tools} senderId={sender.id} qr={qr} />
                  <p className="mt-3 line-clamp-3 text-sm text-ink">“{qr.prefilledMessage}”</p>
                  <div className="mt-3 space-y-2 text-sm">
                    {[qr.shortLink, qr.deepLinkUrl].filter(Boolean).map((l) => (
                      <div key={l} className="flex items-center gap-1 rounded-lg bg-surface-2 px-2">
                        <span className="min-w-0 flex-1 truncate font-mono text-xs">{l}</span>
                        <CopyButton value={l} />
                      </div>
                    ))}
                  </div>
                  <div className="mt-auto flex flex-wrap gap-2 pt-3">
                    <Button size="sm" variant="secondary" leftIcon={Download} onClick={() => tools.downloadQrImage(sender.id, qr.id, 'png').catch((e) => toast.error(getErrorMessage(e)))}>PNG</Button>
                    <Button size="sm" variant="secondary" leftIcon={Download} onClick={() => tools.downloadQrImage(sender.id, qr.id, 'svg').catch((e) => toast.error(getErrorMessage(e)))}>SVG</Button>
                    {canManage && <Button size="sm" variant="ghost" leftIcon={Pencil} onClick={() => setEdit(qr)}>Edit</Button>}
                    {canManage && <Button size="sm" variant="ghost" leftIcon={Trash2} className="text-red-600" onClick={() => setDel(qr)}>Delete</Button>}
                  </div>
                </div>
              ))}
            </div>
          )
        }
      </QueryState>
      <QrModal sender={sender} tools={tools} qr={edit} onClose={() => setEdit(null)} queryKey={key} />
      <ConfirmDialog open={Boolean(del)} onClose={() => setDel(null)} danger title="Delete this QR code?" message="Printed copies will stop opening the chat with the message." confirmLabel="Delete" loading={delM.isPending} onConfirm={() => delM.mutate(del)} />
    </>
  );
}

function QrImage({ tools, senderId, qr }) {
  const [src, setSrc] = useState(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let url;
    let alive = true;
    tools
      .qrImageUrl(senderId, qr.id, 'png')
      .then((u) => {
        url = u;
        if (alive) setSrc(u);
      })
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [tools, senderId, qr.id, qr.prefilledMessage]);
  if (failed) return <div className="flex aspect-square items-center justify-center rounded-lg bg-surface-2 text-sm text-muted">QR image unavailable</div>;
  if (!src) return <Skeleton className="aspect-square w-full" />;
  return <img src={src} alt={`QR code: ${qr.prefilledMessage}`} className="mx-auto aspect-square w-full max-w-[220px] rounded-lg bg-white p-2" />;
}

function QrModal({ sender, tools, qr, onClose, queryKey }) {
  const [text, setText] = useState('');
  useEffect(() => {
    if (qr) setText(qr.prefilledMessage || '');
  }, [qr]);
  const m = useApiMutation({
    mutationFn: () => (qr?.id ? tools.updateQrCode(sender.id, qr.id, text.trim()) : tools.createQrCode(sender.id, text.trim())),
    success: qr?.id ? 'QR code updated' : 'QR code created',
    invalidate: [queryKey],
    onSuccess: onClose,
  });
  return (
    <Modal
      open={Boolean(qr)}
      onClose={onClose}
      title={qr?.id ? 'Edit QR code' : 'New QR code'}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={!text.trim()} loading={m.isPending} onClick={() => m.mutate()}>{qr?.id ? 'Save' : 'Create'}</Button></>}
    >
      <Textarea label="Pre-filled message" required rows={3} maxLength={140} showCount value={text} onChange={(e) => setText(e.target.value)} placeholder="Hi! I want to know more about your offers." />
    </Modal>
  );
}
