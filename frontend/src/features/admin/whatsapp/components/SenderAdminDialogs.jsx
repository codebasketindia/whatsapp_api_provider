import { useEffect, useState } from 'react';
import * as metaApi from '../../../../api/endpoints/meta';
import { Button, Checkbox, ConfirmDialog, Input, Modal, Switch, Textarea } from '../../../../components/ui';
import PartnerSelect from '../../shared/PartnerSelect';
import ReasonDialog from '../../shared/ReasonDialog';
import { useApiMutation } from '../../shared/useApiMutation';

const INV = [['admin', 'senders'], ['admin', 'partners'], ['admin', 'wabas']];

/** dialog: { type: 'assign'|'unassign'|'settings'|'disconnect', sender } */
export default function SenderAdminDialogs({ dialog, onClose }) {
  const s = dialog?.sender;
  return (
    <>
      <AssignDialog sender={s} open={dialog?.type === 'assign'} onClose={onClose} />
      <UnassignDialog sender={s} open={dialog?.type === 'unassign'} onClose={onClose} />
      <SettingsDialog sender={s} open={dialog?.type === 'settings'} onClose={onClose} />
      <DisconnectDialog sender={s} open={dialog?.type === 'disconnect'} onClose={onClose} />
    </>
  );
}

function AssignDialog({ sender, open, onClose }) {
  const [partner, setPartner] = useState(null);
  const [isDefault, setIsDefault] = useState(true);
  useEffect(() => {
    if (open) setPartner(null);
  }, [open]);
  const m = useApiMutation({
    mutationFn: () => metaApi.assignSender(sender.id, { partnerId: partner.id, isDefault }),
    success: `Number assigned to ${partner?.businessName}`,
    invalidate: INV,
    onSuccess: onClose,
  });
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Assign ${sender?.displayPhoneNumber}`}
      description="A number can belong to only one partner at a time, so inbound replies reach the right partner."
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={!partner} loading={m.isPending} onClick={() => m.mutate()}>Assign</Button></>}
    >
      <PartnerSelect value={partner} onChange={setPartner} required />
      <Checkbox className="mt-2" label="Make it the partner's default number" checked={isDefault} onChange={(e) => setIsDefault(e.target.checked)} />
    </Modal>
  );
}

function UnassignDialog({ sender, open, onClose }) {
  const m = useApiMutation({ mutationFn: () => metaApi.unassignSender(sender.id), success: 'Number unassigned', invalidate: INV, onSuccess: onClose });
  return (
    <ConfirmDialog
      open={open}
      onClose={onClose}
      danger
      title={`Unassign ${sender?.displayPhoneNumber}?`}
      message={`${sender?.partner?.businessName || 'The partner'} can no longer send from this number and will stop receiving its replies. History is kept.`}
      confirmLabel="Unassign"
      loading={m.isPending}
      onConfirm={() => m.mutate()}
    />
  );
}

function SettingsDialog({ sender, open, onClose }) {
  const [cap, setCap] = useState('');
  const [mps, setMps] = useState('');
  const [paused, setPaused] = useState(false);
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (open && sender) {
      setCap(sender.dailySendCap ? String(sender.dailySendCap) : '');
      setMps(sender.maxMps ? String(sender.maxMps) : '');
      setPaused(Boolean(sender.isPaused));
      setReason(sender.pauseReason || '');
    }
  }, [open, sender]);
  const m = useApiMutation({
    mutationFn: () =>
      metaApi.updateSender(sender.id, {
        dailySendCap: cap === '' ? null : Number(cap),
        maxMps: mps === '' ? null : Number(mps),
        isPaused: paused,
        pauseReason: paused ? reason.trim() || 'Paused by admin' : null,
      }),
    success: 'Sending settings saved',
    invalidate: INV,
    onSuccess: onClose,
  });
  const mpsInvalid = mps !== '' && !(Number(mps) >= 1 && Number(mps) <= 1000);
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Sending settings"
      description={sender?.displayPhoneNumber}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={mpsInvalid} loading={m.isPending} onClick={() => m.mutate()}>Save</Button></>}
    >
      <div className="space-y-4">
        <Input label="Daily sending cap (messages)" inputMode="numeric" placeholder="No cap" value={cap} onChange={(e) => setCap(e.target.value.replace(/\D/g, ''))} hint="Our own safety limit per day (IST). Empty = no cap." />
        <Input label="Max messages per second" inputMode="numeric" value={mps} onChange={(e) => setMps(e.target.value.replace(/\D/g, ''))} error={mpsInvalid && 'Between 1 and 1000'} hint="Keep at or below the number's Meta throughput (default 80)." />
        <Switch checked={paused} onChange={setPaused} label="Pause sending" description="Queued messages wait; running campaigns on this number pause." />
        {paused && <Textarea label="Pause reason (shown to the partner)" rows={2} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} />}
      </div>
    </Modal>
  );
}

function DisconnectDialog({ sender, open, onClose }) {
  const m = useApiMutation({ mutationFn: (reason) => metaApi.disconnectSenderAdmin(sender.id, reason), success: 'Number disconnected', invalidate: INV, onSuccess: onClose });
  return (
    <ReasonDialog
      open={open}
      onClose={onClose}
      danger
      title={`Disconnect ${sender?.displayPhoneNumber}?`}
      message="We unsubscribe from its webhooks and deregister it from Cloud API. Messages can no longer be sent. History stays read-only."
      confirmLabel="Disconnect"
      loading={m.isPending}
      onConfirm={(reason) => m.mutate(reason)}
    />
  );
}
