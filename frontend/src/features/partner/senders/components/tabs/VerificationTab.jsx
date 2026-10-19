import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { KeyRound, MessageSquare, PhoneCall, ShieldCheck } from 'lucide-react';
import toast from 'react-hot-toast';
import { getErrorMessage } from '../../../../../api/axios';
import { Alert, Button, Card, ConfirmDialog, InfoTip, Input, KeyValue, StatusBadge } from '../../../../../components/ui';
import { humanize } from '../../../../../lib/format';

const pinOk = (p) => /^\d{6}$/.test(p);

/** Code verification, registration and two-step PIN (own numbers only). T2–T6. */
export default function VerificationTab({ tools, sender }) {
  const qc = useQueryClient();
  const refresh = () => qc.invalidateQueries({ queryKey: [tools.scope, 'senders'] });
  const [code, setCode] = useState('');
  const [regPin, setRegPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [deregOpen, setDeregOpen] = useState(false);
  const opts = (msg) => ({
    onSuccess: () => {
      toast.success(msg);
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const request = useMutation({ mutationFn: (method) => tools.requestCode(sender.id, { method, language: 'en' }), ...opts('Code sent. It may take a minute to arrive.') });
  const verify = useMutation({ mutationFn: () => tools.verifyCode(sender.id, code), ...opts('Number verified') });
  const register = useMutation({ mutationFn: () => tools.register(sender.id, regPin), ...opts('Number registered for sending') });
  const deregister = useMutation({
    mutationFn: () => tools.deregister(sender.id),
    onSuccess: () => {
      toast.success('Number removed from WhatsApp Cloud API');
      setDeregOpen(false);
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const changePin = useMutation({
    mutationFn: () => tools.changePin(sender.id, newPin),
    onSuccess: () => {
      toast.success('PIN changed');
      setNewPin('');
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
      <Card title="Status">
        <KeyValue
          items={[
            { label: 'Code verification', value: <StatusBadge status={sender.codeVerificationStatus || 'UNKNOWN'} label={humanize(sender.codeVerificationStatus || 'Not known')} /> },
            { label: 'Registered for sending', value: <StatusBadge status={sender.isRegistered ? 'DONE' : 'PENDING'} label={sender.isRegistered ? 'Yes' : 'No'} /> },
            { label: 'Display name', value: sender.verifiedName },
            { label: 'Name review', value: <StatusBadge status={sender.nameStatus || 'UNKNOWN'} /> },
          ]}
        />
        {sender.nameStatus === 'DECLINED' && (
          <Alert variant="warning" className="mt-4" title="Display name was declined">
            Meta did not approve the display name. Use a name that matches your business and its website, then request a new name in WhatsApp Manager.
          </Alert>
        )}
        {sender.isCoexistence && (
          <Alert variant="info" className="mt-4">This number also works in the WhatsApp Business App, so it does not need registration here.</Alert>
        )}
      </Card>

      {!sender.isCoexistence && (
        <Card title="Verify the number" subtitle="Needed only if Meta asks to confirm you own this number">
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" leftIcon={MessageSquare} loading={request.isPending && request.variables === 'SMS'} onClick={() => request.mutate('SMS')}>Send code by SMS</Button>
            <Button variant="secondary" leftIcon={PhoneCall} loading={request.isPending && request.variables === 'VOICE'} onClick={() => request.mutate('VOICE')}>Call me with code</Button>
          </div>
          <div className="mt-4 flex items-end gap-2">
            <Input label="Code" inputMode="numeric" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
            <Button onClick={() => verify.mutate()} loading={verify.isPending} disabled={code.length !== 6}>Verify</Button>
          </div>
        </Card>
      )}

      {!sender.isCoexistence && (
        <Card title="Register for sending" subtitle="Registers the number with WhatsApp Cloud API using your 6-digit PIN">
          <div className="flex items-end gap-2">
            <Input label="6-digit PIN" type="password" inputMode="numeric" maxLength={6} value={regPin} onChange={(e) => setRegPin(e.target.value.replace(/\D/g, ''))} />
            <Button leftIcon={ShieldCheck} onClick={() => register.mutate()} loading={register.isPending} disabled={!pinOk(regPin)}>Register</Button>
          </div>
          {sender.isRegistered && (
            <div className="mt-4 border-t border-line pt-4">
              <Button variant="danger-outline" onClick={() => setDeregOpen(true)}>Deregister number</Button>
              <p className="mt-1 text-xs text-muted">Stops sending and receiving messages on this number until you register again.</p>
            </div>
          )}
        </Card>
      )}

      {!sender.isCoexistence && (
        <Card title={<span className="inline-flex items-center gap-1">Change two-step PIN <InfoTip term="TWO_STEP_PIN" /></span>}>
          <div className="flex items-end gap-2">
            <Input label="New 6-digit PIN" type="password" inputMode="numeric" maxLength={6} value={newPin} onChange={(e) => setNewPin(e.target.value.replace(/\D/g, ''))} />
            <Button leftIcon={KeyRound} onClick={() => changePin.mutate()} loading={changePin.isPending} disabled={!pinOk(newPin)}>Change PIN</Button>
          </div>
        </Card>
      )}

      <ConfirmDialog
        open={deregOpen}
        onClose={() => setDeregOpen(false)}
        onConfirm={() => deregister.mutate()}
        loading={deregister.isPending}
        danger
        title="Deregister this number?"
        message="Messages will stop until you register the number again."
        confirmWord="DEREGISTER"
        confirmLabel="Deregister"
      />
    </div>
  );
}
