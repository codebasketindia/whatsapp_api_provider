import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, Pencil, Plus, QrCode, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { getErrorMessage } from '../../../../../api/axios';
import { Button, ConfirmDialog, CopyButton, EmptyState, ErrorState, IconButton, Modal, Skeleton, Textarea } from '../../../../../components/ui';

function QrImage({ tools, senderId, qrId }) {
  const [url, setUrl] = useState(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let alive = true;
    let objectUrl;
    tools
      .qrImageUrl(senderId, qrId, 'png')
      .then((u) => {
        objectUrl = u;
        if (alive) setUrl(u);
      })
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [tools, senderId, qrId]);
  if (failed) return <div className="flex h-40 w-40 items-center justify-center rounded-lg bg-surface-2 text-xs text-muted">QR not available</div>;
  if (!url) return <Skeleton className="h-40 w-40" />;
  return <img src={url} alt="QR code" className="h-40 w-40 rounded-lg bg-white p-2" />;
}

/** QR codes and short links with a pre-filled message (T12–T16). */
export default function QrCodesTab({ tools, sender, readOnly }) {
  const qc = useQueryClient();
  const key = [tools.scope, 'senders', sender.id, 'qr'];
  const q = useQuery({ queryKey: key, queryFn: () => tools.listQrCodes(sender.id) });
  const [edit, setEdit] = useState(null); // { id?, prefilledMessage }
  const [del, setDel] = useState(null);
  const save = useMutation({
    mutationFn: () => (edit.id ? tools.updateQrCode(sender.id, edit.id, edit.prefilledMessage) : tools.createQrCode(sender.id, edit.prefilledMessage)),
    onSuccess: () => {
      toast.success(edit.id ? 'QR code updated' : 'QR code created');
      setEdit(null);
      qc.invalidateQueries({ queryKey: key });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id) => tools.deleteQrCode(sender.id, id),
    onSuccess: () => {
      toast.success('QR code deleted');
      setDel(null);
      qc.invalidateQueries({ queryKey: key });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const download = (id, format) => tools.downloadQrImage(sender.id, id, format).catch((e) => toast.error(getErrorMessage(e)));

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted">Customers scan the QR code or tap the link to open a chat with your message already typed.</p>
        {!readOnly && <Button leftIcon={Plus} onClick={() => setEdit({ prefilledMessage: '' })}>New QR code</Button>}
      </div>
      {q.isError ? (
        <ErrorState error={q.error} onRetry={q.refetch} />
      ) : q.isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : !q.data?.length ? (
        <div className="card">
          <EmptyState icon={QrCode} title="No QR codes yet" text="Print a QR code on your shop counter, bill or website." action={!readOnly && <Button onClick={() => setEdit({ prefilledMessage: '' })}>Create QR code</Button>} />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {q.data.map((c) => (
            <div key={c.id} className="card flex flex-col gap-4 p-4 sm:flex-row">
              <QrImage tools={tools} senderId={sender.id} qrId={c.id} />
              <div className="min-w-0 flex-1 space-y-2">
                <p className="whitespace-pre-wrap break-words text-sm text-ink">“{c.prefilledMessage}”</p>
                {(c.shortLink || c.deepLinkUrl) && (
                  <div className="flex items-center gap-2 rounded-lg bg-surface-2 p-2">
                    <span className="min-w-0 flex-1 truncate font-mono text-xs">{c.shortLink || c.deepLinkUrl}</span>
                    <CopyButton value={c.shortLink || c.deepLinkUrl} toastMessage="Link copied" />
                  </div>
                )}
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="secondary" leftIcon={Download} onClick={() => download(c.id, 'png')}>PNG</Button>
                  <Button size="sm" variant="secondary" leftIcon={Download} onClick={() => download(c.id, 'svg')}>SVG</Button>
                  {!readOnly && <IconButton icon={Pencil} label="Edit message" size="sm" onClick={() => setEdit({ id: c.id, prefilledMessage: c.prefilledMessage })} />}
                  {!readOnly && <IconButton icon={Trash2} label="Delete QR code" variant="danger" size="sm" onClick={() => setDel(c)} />}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
      <Modal
        open={Boolean(edit)}
        onClose={() => setEdit(null)}
        title={edit?.id ? 'Edit QR code' : 'New QR code'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEdit(null)}>Cancel</Button>
            <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!edit?.prefilledMessage?.trim()}>Save</Button>
          </>
        }
      >
        {edit && (
          <Textarea
            label="Message the customer will send"
            rows={3}
            maxLength={140}
            showCount
            value={edit.prefilledMessage}
            onChange={(e) => setEdit({ ...edit, prefilledMessage: e.target.value })}
            hint="Example: Hi, I want to know about your offers"
          />
        )}
      </Modal>
      <ConfirmDialog
        open={Boolean(del)}
        onClose={() => setDel(null)}
        onConfirm={() => remove.mutate(del.id)}
        loading={remove.isPending}
        danger
        title="Delete this QR code?"
        message="Printed copies will stop working."
        confirmLabel="Delete"
      />
    </>
  );
}
