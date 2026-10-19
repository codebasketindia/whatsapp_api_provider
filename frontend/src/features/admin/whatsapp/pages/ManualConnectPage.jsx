import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import * as metaApi from '../../../../api/endpoints/meta';
import { glossary } from '../../../../lib/metaGlossary';
import { Alert, Button, Card, Input, PageHeader, PasswordInput, Switch } from '../../../../components/ui';
import PartnerSelect from '../../shared/PartnerSelect';
import { useApiMutation } from '../../shared/useApiMutation';
import Checklist from '../components/Checklist';

const schema = z
  .object({
    wabaId: z.string().trim().regex(/^\d{5,32}$/, 'WABA ID is a number'),
    phoneNumberId: z.string().trim().regex(/^\d{5,32}$/, 'Phone number ID is a number (not the phone number)'),
    accessToken: z.string().trim().min(20, 'Paste a permanent access token'),
    register: z.boolean(),
    pin: z.string().optional(),
  })
  .refine((v) => !v.register || /^\d{6}$/.test(v.pin || ''), { path: ['pin'], message: 'Enter a 6-digit PIN' });

export default function ManualConnectPage() {
  const navigate = useNavigate();
  const [partner, setPartner] = useState(null);
  const [result, setResult] = useState(null);
  const { register, handleSubmit, watch, setValue, formState: { errors } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { wabaId: '', phoneNumberId: '', accessToken: '', register: false, pin: '' },
  });
  const m = useApiMutation({
    mutationFn: (v) => metaApi.manualConnect({ ...v, partnerId: partner?.id ?? null, pin: v.register ? v.pin : undefined }),
    success: 'Number connected',
    invalidate: [['admin', 'wabas'], ['admin', 'senders'], ['admin', 'partners']],
    onSuccess: setResult,
  });
  if (result) {
    const sender = result.sender || result;
    return (
      <>
        <PageHeader title="Number connected" backTo="/admin/whatsapp/senders" />
        <Card title={sender.displayPhoneNumber} subtitle={sender.verifiedName}>
          <Checklist checklist={result.checklist} />
          <div className="mt-4 flex flex-wrap gap-2">
            <Button onClick={() => navigate(`/admin/whatsapp/senders/${sender.id}`)}>Open number</Button>
            <Button variant="secondary" onClick={() => setResult(null)}>Connect another</Button>
          </div>
        </Card>
      </>
    );
  }
  return (
    <>
      <PageHeader title="Manual connect" subtitle="Connect a number using IDs and a permanent token from Meta (admin only)." backTo="/admin/whatsapp/accounts" />
      <form onSubmit={handleSubmit((v) => m.mutate(v))} noValidate className="space-y-4">
        <Alert variant="info">
          Prefer Embedded Signup for partners. Use manual connect for our own numbers or when a partner gives you a system-user token.
        </Alert>
        <Card title="Meta details">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Input label="WhatsApp Business Account ID" info={glossary('WABA')} required error={errors.wabaId} {...register('wabaId')} />
            <Input label="Phone number ID" info={glossary('PHONE_NUMBER_ID')} required error={errors.phoneNumberId} {...register('phoneNumberId')} />
            <PasswordInput className="md:col-span-2" label="Permanent access token" required autoComplete="off" hint="Stored encrypted, never shown again." error={errors.accessToken} {...register('accessToken')} />
          </div>
        </Card>
        <Card title="Assign to" subtitle="Leave empty to keep it as an unassigned platform number.">
          <PartnerSelect label="Partner (optional)" value={partner} onChange={setPartner} />
        </Card>
        <Card title="Registration">
          <Switch checked={watch('register')} onChange={(v) => setValue('register', v)} label="Register the number for Cloud API now" description="Needed if the number is not registered yet." />
          {watch('register') && (
            <Input className="mt-3 md:w-1/2" label="Two-step verification PIN" info={glossary('TWO_STEP_PIN')} inputMode="numeric" maxLength={6} error={errors.pin} {...register('pin')} />
          )}
        </Card>
        <div className="flex justify-end">
          <Button type="submit" loading={m.isPending} className="w-full md:w-auto">Validate and connect</Button>
        </div>
      </form>
    </>
  );
}
