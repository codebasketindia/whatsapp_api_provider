import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { BadgeCheck, MessageCircle, Phone, Smartphone } from 'lucide-react';
import toast from 'react-hot-toast';
import * as sendersApi from '../../../../api/endpoints/senders';
import { getErrorMessage } from '../../../../api/axios';
import { Alert, Button, Card, ErrorState, InfoTip, Input, PageHeader, PageLoader, RadioCards, Spinner, Stepper } from '../../../../components/ui';
import { Checklist, StepsList } from '../components/signupUi';
import { signupFailureText, useEmbeddedSignup } from '../components/useEmbeddedSignup';

const MODES = [
  { value: 'NEW', label: 'A new number', description: 'A number that is not on WhatsApp yet. You will get a code by SMS or call.', icon: Phone },
  { value: 'EXISTING', label: 'A number already on WhatsApp Cloud API', description: 'Move a number that is connected to another provider.', icon: Smartphone },
  {
    value: 'COEXISTENCE',
    label: 'Keep using the WhatsApp Business App',
    description: 'Use the same number in the WhatsApp Business App on your phone and here.',
    icon: MessageCircle,
  },
];

export default function ConnectNumberPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const cfg = useQuery({ queryKey: ['partner', 'embedded-signup', 'config'], queryFn: sendersApi.getEmbeddedSignupConfig, retry: false });
  const [mode, setMode] = useState('NEW');
  const [pin, setPin] = useState('');
  const [pin2, setPin2] = useState('');
  const [step, setStep] = useState(0);
  const [failure, setFailure] = useState(null);
  const [result, setResult] = useState(null);
  const { run, running } = useEmbeddedSignup();

  const complete = useMutation({
    mutationFn: (body) => sendersApi.completeEmbeddedSignup(body),
    onSuccess: (data) => {
      setResult(data);
      setStep(3);
      qc.invalidateQueries({ queryKey: ['partner', 'senders'] });
      toast.success('Your number is connected');
    },
    onError: (e) => {
      setFailure(getErrorMessage(e));
      setResult(e?.response?.data?.data || null);
    },
  });

  if (cfg.isLoading) return <PageLoader />;
  if (cfg.isError) return <ErrorState error={cfg.error} onRetry={cfg.refetch} />;
  if (!cfg.data?.allowed) {
    return (
      <>
        <PageHeader title="Connect WhatsApp number" backTo="/partner/senders" backLabel="My numbers" />
        <Alert variant="info" title="Not available for your account">Connecting your own number is not turned on. Please contact support.</Alert>
      </>
    );
  }

  const coexist = mode === 'COEXISTENCE';
  const pinError = pin && !/^\d{6}$/.test(pin) ? 'PIN must be 6 digits' : pin2 && pin2 !== pin ? 'PINs do not match' : null;
  const pinOk = coexist || (/^\d{6}$/.test(pin) && pin === pin2);

  const start = async () => {
    setFailure(null);
    setResult(null);
    const r = await run(cfg.data, mode);
    if (!r.ok) {
      setFailure(signupFailureText(r));
      return;
    }
    // The code is valid for ~30 seconds: send it right away.
    complete.mutate({
      code: r.code,
      wabaId: r.session.wabaId,
      phoneNumberId: r.session.phoneNumberId,
      businessId: r.session.businessId,
      mode,
      pin: coexist ? undefined : pin,
    });
  };

  const steps = [
    { key: 'type', label: 'Number type' },
    { key: 'pin', label: 'Security PIN' },
    { key: 'meta', label: 'Connect with Meta' },
    { key: 'done', label: 'Done' },
  ];

  return (
    <>
      <PageHeader title="Connect WhatsApp number" subtitle="Takes about 5 minutes. Keep your phone nearby." backTo="/partner/senders" backLabel="My numbers" />
      <Stepper steps={steps} current={step} className="mb-6" />
      <div className="mx-auto max-w-2xl">
        {step === 0 && (
          <Card title="Which number do you want to connect?">
            <RadioCards name="mode" value={mode} onChange={setMode} options={MODES} />
            {coexist && (
              <Alert variant="info" className="mt-4">
                Your chats stay in the WhatsApp Business App. Messages sent from here also appear there. <InfoTip term="COEXISTENCE" />
              </Alert>
            )}
            <div className="mt-5 flex justify-end">
              <Button onClick={() => setStep(coexist ? 2 : 1)}>Continue</Button>
            </div>
          </Card>
        )}
        {step === 1 && (
          <Card title="Create a 6-digit security PIN" subtitle="WhatsApp asks for this PIN if the number is ever registered again. Keep it safe.">
            <div className="grid gap-4 sm:grid-cols-2">
              <Input label="PIN" type="password" inputMode="numeric" autoComplete="new-password" maxLength={6} value={pin} onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} error={pin && !/^\d{6}$/.test(pin) ? 'PIN must be 6 digits' : undefined} />
              <Input label="Repeat PIN" type="password" inputMode="numeric" autoComplete="new-password" maxLength={6} value={pin2} onChange={(e) => setPin2(e.target.value.replace(/\D/g, ''))} error={pin2 && pin2 !== pin ? 'PINs do not match' : undefined} />
            </div>
            <p className="mt-2 flex items-center gap-1 text-xs text-muted">Two-step verification PIN <InfoTip term="TWO_STEP_PIN" /></p>
            <div className="mt-5 flex justify-between">
              <Button variant="secondary" onClick={() => setStep(0)}>Back</Button>
              <Button onClick={() => setStep(2)} disabled={!pinOk || Boolean(pinError)}>Continue</Button>
            </div>
          </Card>
        )}
        {step === 2 && (
          <Card title="Connect with Meta" subtitle="A Facebook window will open. Log in, choose or create your business and follow the steps.">
            <ul className="mb-4 space-y-2 text-sm text-ink">
              <li className="flex gap-2"><BadgeCheck className="h-5 w-5 shrink-0 text-brand-600" /> Use the Facebook account that manages your business.</li>
              <li className="flex gap-2"><BadgeCheck className="h-5 w-5 shrink-0 text-brand-600" /> Enter your business name exactly as it should appear to customers.</li>
              {!coexist && <li className="flex gap-2"><BadgeCheck className="h-5 w-5 shrink-0 text-brand-600" /> Keep your phone ready to receive the verification code.</li>}
              {coexist && <li className="flex gap-2"><BadgeCheck className="h-5 w-5 shrink-0 text-brand-600" /> Open the WhatsApp Business App on your phone to scan the QR code.</li>}
            </ul>
            {failure && (
              <Alert variant="error" className="mb-4" title="Not connected yet">
                {failure}
              </Alert>
            )}
            {result?.steps && <div className="mb-4"><StepsList steps={result.steps} /></div>}
            {(running || complete.isPending) ? (
              <div className="flex flex-col items-center gap-3 py-6 text-center" role="status">
                <Spinner size="lg" />
                <p className="text-sm text-muted">{complete.isPending ? 'Finishing setup with Meta… this can take up to a minute.' : 'Waiting for the Facebook window…'}</p>
              </div>
            ) : (
              <div className="flex justify-between">
                <Button variant="secondary" onClick={() => setStep(coexist ? 0 : 1)}>Back</Button>
                <Button onClick={start}>{failure ? 'Try again' : 'Open Facebook'}</Button>
              </div>
            )}
          </Card>
        )}
        {step === 3 && result && (
          <div className="space-y-4">
            <Alert variant="success" title="Number connected">
              {result.sender?.verifiedName || 'Your number'} ({result.sender?.displayPhoneNumber}) is now connected.
            </Alert>
            {result.steps?.length > 0 && (
              <Card title="What we did">
                <StepsList steps={result.steps} />
              </Card>
            )}
            <Card title="What to do next">
              <Checklist checklist={result.checklist} />
            </Card>
            <div className="grid gap-2 sm:grid-cols-2">
              <Button variant="secondary" onClick={() => navigate(`/partner/senders/${result.sender?.id}`)}>Open number details</Button>
              <Button to="/partner/templates/new">Create a template</Button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
