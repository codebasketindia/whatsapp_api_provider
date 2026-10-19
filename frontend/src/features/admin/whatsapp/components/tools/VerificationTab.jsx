import { useState } from 'react';
import { KeyRound, MessageSquare, Phone, ShieldCheck, ShieldOff } from 'lucide-react';
import { glossary } from '../../../../../lib/metaGlossary';
import { humanize } from '../../../../../lib/format';
import { Alert, Badge, Button, Card, ConfirmDialog, InfoTip, Input, KeyValue, RadioCards, Select } from '../../../../../components/ui';
import { useApiMutation } from '../../../shared/useApiMutation';

const LANGS = [{ value: 'en', label: 'English' }, { value: 'hi', label: 'Hindi' }];

/** Request/verify code, register/deregister and change the two-step PIN (T2–T6). */
export default function VerificationTab({ sender, tools, canManage, onChanged }) {
  const [method, setMethod] = useState('SMS');
  const [lang, setLang] = useState('en');
  const [code, setCode] = useState('');
  const [regPin, setRegPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [confirm, setConfirm] = useState(null);
  const inv = [[tools.scope, 'senders'], ['admin', 'senders']];
  const requestM = useApiMutation({ mutationFn: () => tools.requestCode(sender.id, { method, language: lang }), success: method === 'SMS' ? 'Code sent by SMS' : 'You will receive a call with the code' });
  const verifyM = useApiMutation({ mutationFn: () => tools.verifyCode(sender.id, code), success: 'Number verified', invalidate: inv, onSuccess: () => { setCode(''); onChanged?.(); } });
  const registerM = useApiMutation({ mutationFn: () => tools.register(sender.id, regPin), success: 'Number registered for sending', invalidate: inv, onSuccess: () => { setRegPin(''); setConfirm(null); onChanged?.(); } });
  const deregM = useApiMutation({ mutationFn: () => tools.deregister(sender.id), success: 'Number deregistered', invalidate: inv, onSuccess: () => { setConfirm(null); onChanged?.(); } });
  const pinM = useApiMutation({ mutationFn: () => tools.changePin(sender.id, newPin), success: 'Two-step PIN changed', onSuccess: () => { setNewPin(''); setConfirm(null); } });
  const pinOk = (p) => /^\d{6}$/.test(p);
  const locked = !canManage;
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <Card title="Status">
        <KeyValue
          items={[
            { label: 'Code verification', value: sender.codeVerificationStatus ? humanize(sender.codeVerificationStatus) : '—' },
            { label: 'Registered for Cloud API', value: sender.isRegistered ? <Badge tone="green">Registered</Badge> : <Badge tone="amber">Not registered</Badge> },
            { label: 'Display name', value: sender.verifiedName || '—' },
            { label: 'Name status', value: sender.nameStatus ? <span className="inline-flex items-center gap-1"><Badge tone={sender.nameStatus === 'APPROVED' ? 'green' : sender.nameStatus === 'DECLINED' ? 'red' : 'amber'}>{humanize(sender.nameStatus)}</Badge><InfoTip term="NAME_STATUS" /></span> : '—' },
          ]}
        />
        {sender.nameStatus === 'DECLINED' && (
          <Alert variant="warning" className="mt-4" title="Display name was declined">
            Change the display name in WhatsApp Manager so it matches the business name on your website or documents, then sync again.
          </Alert>
        )}
        {sender.isCoexistence && (
          <Alert variant="info" className="mt-4">
            This number also uses the WhatsApp Business app (coexistence). Registration and PIN are managed in the app.
          </Alert>
        )}
      </Card>
      {!locked && !sender.isCoexistence && (
        <>
          <Card title="Verify the number" subtitle="Only needed when Meta asks to verify ownership of the number.">
            <RadioCards
              name="method"
              columns={2}
              value={method}
              onChange={setMethod}
              options={[{ value: 'SMS', label: 'SMS', icon: MessageSquare }, { value: 'VOICE', label: 'Voice call', icon: Phone }]}
            />
            <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-end">
              <Select className="sm:w-40" label="Language" value={lang} onChange={(e) => setLang(e.target.value)} options={LANGS} />
              <Button variant="secondary" loading={requestM.isPending} onClick={() => requestM.mutate()}>Send code</Button>
            </div>
            <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-end">
              <Input className="sm:w-48" label="Code received" inputMode="numeric" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
              <Button disabled={code.length !== 6} loading={verifyM.isPending} onClick={() => verifyM.mutate()}>Verify</Button>
            </div>
          </Card>
          <Card title="Register for sending" subtitle="Connects the number to Cloud API with a 6-digit two-step PIN.">
            {sender.isRegistered ? (
              <Button variant="danger-outline" leftIcon={ShieldOff} onClick={() => setConfirm('dereg')}>Deregister number</Button>
            ) : (
              <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
                <Input className="sm:w-48" label="6-digit PIN" info={glossary('TWO_STEP_PIN')} inputMode="numeric" maxLength={6} value={regPin} onChange={(e) => setRegPin(e.target.value.replace(/\D/g, ''))} />
                <Button leftIcon={ShieldCheck} disabled={!pinOk(regPin)} onClick={() => setConfirm('register')}>Register</Button>
              </div>
            )}
          </Card>
          <Card title="Change two-step PIN" subtitle="Keep the PIN safe; it is needed to move the number to another platform.">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
              <Input className="sm:w-48" label="New 6-digit PIN" inputMode="numeric" maxLength={6} value={newPin} onChange={(e) => setNewPin(e.target.value.replace(/\D/g, ''))} />
              <Button leftIcon={KeyRound} disabled={!pinOk(newPin)} onClick={() => setConfirm('pin')}>Change PIN</Button>
            </div>
          </Card>
        </>
      )}
      <ConfirmDialog open={confirm === 'register'} onClose={() => setConfirm(null)} title="Register this number?" message="The number will be able to send and receive messages through this platform." confirmLabel="Register" loading={registerM.isPending} onConfirm={() => registerM.mutate()} />
      <ConfirmDialog open={confirm === 'dereg'} onClose={() => setConfirm(null)} danger title="Deregister this number?" message="Sending and receiving stop immediately until the number is registered again." confirmLabel="Deregister" confirmWord="DEREGISTER" loading={deregM.isPending} onConfirm={() => deregM.mutate()} />
      <ConfirmDialog open={confirm === 'pin'} onClose={() => setConfirm(null)} title="Change the two-step PIN?" message="The old PIN stops working. Save the new PIN somewhere safe." confirmLabel="Change PIN" loading={pinM.isPending} onConfirm={() => pinM.mutate()} />
    </div>
  );
}
