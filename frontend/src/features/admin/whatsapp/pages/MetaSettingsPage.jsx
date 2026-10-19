import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CheckCircle2, PlugZap, XCircle } from 'lucide-react';
import * as metaApi from '../../../../api/endpoints/meta';
import { useAuth } from '../../../../auth/useAuth';
import { glossary } from '../../../../lib/metaGlossary';
import {
  Alert, Badge, Button, Card, ConfirmDialog, CopyButton, DateTime, ErrorState, Input, PageHeader, PageLoader, PasswordInput, RadioCards,
} from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';

const schema = z.object({
  appId: z.string().trim().regex(/^\d{5,32}$/, 'App ID is a number from the Meta App Dashboard').or(z.literal('')),
  appSecret: z.string().trim().optional(),
  configId: z.string().trim().regex(/^\d{5,32}$/, 'Configuration ID is a number').or(z.literal('')),
  solutionId: z.string().trim().optional(),
  businessPortfolioId: z.string().trim().regex(/^\d*$/, 'Numbers only').optional(),
  graphApiVersion: z.string().trim().regex(/^v\d{1,2}\.\d$/, 'Format like v25.0'),
  verifyToken: z.string().trim().optional(),
  billingMode: z.enum(['TECH_PROVIDER', 'SOLUTION_PARTNER']),
  extendedCreditLineId: z.string().trim().regex(/^\d*$/, 'Numbers only').optional(),
});

export default function MetaSettingsPage() {
  const { hasPermission } = useAuth();
  const canEdit = hasPermission('SETTINGS_MANAGE');
  const canTest = hasPermission('WHATSAPP_MANAGE');
  const q = useQuery({ queryKey: ['admin', 'meta-settings'], queryFn: metaApi.getMetaSettings });
  const [testResult, setTestResult] = useState(null);
  const [confirm, setConfirm] = useState(false);
  const { register, handleSubmit, reset, watch, setValue, formState: { errors, isDirty } } = useForm({ resolver: zodResolver(schema) });

  useEffect(() => {
    const s = q.data;
    if (s) {
      reset({
        appId: s.appId || '', appSecret: '', configId: s.configId || '', solutionId: s.solutionId || '',
        businessPortfolioId: s.businessPortfolioId || '', graphApiVersion: s.graphApiVersion || 'v25.0', verifyToken: '',
        billingMode: s.billingMode || 'TECH_PROVIDER', extendedCreditLineId: s.extendedCreditLineId || '',
      });
    }
  }, [q.data, reset]);

  const save = useApiMutation({
    mutationFn: (v) => {
      const body = { ...v };
      if (!body.appSecret) delete body.appSecret;
      if (!body.verifyToken) delete body.verifyToken;
      return metaApi.updateMetaSettings(body);
    },
    success: 'Meta settings saved',
    invalidate: [['admin', 'meta-settings']],
    onSuccess: () => setConfirm(false),
  });
  const test = useApiMutation({
    mutationFn: metaApi.testMetaSettings,
    onSuccess: (r) => setTestResult(r),
    invalidate: [['admin', 'meta-settings']],
  });

  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const s = q.data;
  const generateToken = () => {
    const arr = new Uint8Array(24);
    crypto.getRandomValues(arr);
    setValue('verifyToken', Array.from(arr, (b) => b.toString(16).padStart(2, '0')).join(''), { shouldDirty: true });
  };

  return (
    <>
      <PageHeader
        title="Meta settings"
        subtitle="Connect this platform to your Meta app. Only the server talks to Meta; secrets are stored encrypted."
        actions={canTest && <Button variant="secondary" leftIcon={PlugZap} loading={test.isPending} onClick={() => test.mutate()}>Test connection</Button>}
      />
      {(testResult || s.lastTestAt) && (
        <Card className="mb-4" title="Connection test">
          {testResult ? (
            <div className="space-y-2">
              <TestRow ok={testResult.appOk} label="Meta app (App ID + App Secret)" />
              <TestRow ok={testResult.tokenOk} label="Access token" />
              <TestRow ok={testResult.webhookSubscribed} label="Webhook subscription" />
              {testResult.details?.length > 0 && (
                <ul className="mt-2 list-disc pl-5 text-sm text-muted">{testResult.details.map((d, i) => <li key={i}>{typeof d === 'string' ? d : JSON.stringify(d)}</li>)}</ul>
              )}
            </div>
          ) : (
            <p className="text-sm">
              Last test <DateTime value={s.lastTestAt} mode="relative" />: {s.lastTestOk ? <Badge tone="green">Passed</Badge> : <Badge tone="red">Failed</Badge>}
            </p>
          )}
        </Card>
      )}
      <form onSubmit={handleSubmit(() => setConfirm(true))} noValidate className="space-y-4 pb-24 md:pb-4">
        <Card title="Webhook" subtitle="Paste these in Meta App Dashboard → WhatsApp → Configuration.">
          <div className="space-y-3">
            <div>
              <span className="label-base">Callback URL</span>
              <div className="flex items-center gap-2 rounded-lg border border-line bg-surface-2 px-3 py-2">
                <code className="min-w-0 flex-1 break-all text-sm">{s.webhookUrl}</code>
                <CopyButton value={s.webhookUrl} />
              </div>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
              <PasswordInput
                className="flex-1"
                label="Verify token"
                info={glossary('WEBHOOK')}
                disabled={!canEdit}
                placeholder={s.verifyTokenSet ? 'Saved — type to replace' : 'Not set'}
                hint={s.verifyTokenSet ? 'A token is saved. Leave empty to keep it.' : 'Create a random token and paste the same value in Meta.'}
                autoComplete="off"
                {...register('verifyToken')}
              />
              {canEdit && <Button variant="secondary" onClick={generateToken}>Generate</Button>}
              {watch('verifyToken') && <CopyButton value={watch('verifyToken')} label="Copy" />}
            </div>
          </div>
        </Card>
        <Card title="Meta app">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Input label="App ID" required disabled={!canEdit} error={errors.appId} {...register('appId')} />
            <PasswordInput
              label="App Secret"
              disabled={!canEdit}
              autoComplete="off"
              placeholder={s.appSecretSet ? 'Saved — type to replace' : 'Not set'}
              hint={s.appSecretSet ? <span className="inline-flex items-center gap-1"><Badge tone="green">Saved</Badge> Leave empty to keep the saved secret.</span> : 'Required for webhooks and Embedded Signup.'}
              {...register('appSecret')}
            />
            <Input label="Embedded Signup configuration ID" info={glossary('EMBEDDED_SIGNUP')} disabled={!canEdit} error={errors.configId} {...register('configId')} />
            <Input label="Solution / partner ID (optional)" disabled={!canEdit} {...register('solutionId')} />
            <Input label="Business portfolio ID" disabled={!canEdit} error={errors.businessPortfolioId} {...register('businessPortfolioId')} />
            <Input label="Graph API version" required disabled={!canEdit} hint="Use the latest version listed in Meta docs, e.g. v25.0." error={errors.graphApiVersion} {...register('graphApiVersion')} />
          </div>
        </Card>
        <Card title="Billing for partners' own numbers" subtitle="How Meta's message charges are paid when a partner connects their own number.">
          <RadioCards
            name="billingMode"
            columns={2}
            value={watch('billingMode')}
            onChange={(v) => canEdit && setValue('billingMode', v, { shouldDirty: true })}
            options={[
              { value: 'TECH_PROVIDER', label: 'Tech Provider', description: glossary('TECH_PROVIDER') + ' The partner adds a payment method in Meta.' },
              { value: 'SOLUTION_PARTNER', label: 'Solution Partner', description: glossary('SOLUTION_PARTNER') + ' Requires an approved credit line from Meta.' },
            ]}
          />
          {watch('billingMode') === 'SOLUTION_PARTNER' && (
            <Input className="mt-4" label="Extended credit line ID" disabled={!canEdit} hint="Shared with each partner WABA during onboarding." error={errors.extendedCreditLineId} {...register('extendedCreditLineId')} />
          )}
          <Alert variant="info" className="mt-4">
            Changing this affects numbers connected from now on. Numbers already connected keep their billing mode.
          </Alert>
        </Card>
        {canEdit && (
          <div className="fixed inset-x-0 bottom-0 z-20 flex gap-3 border-t border-line bg-surface p-3 safe-bottom md:static md:justify-end md:border-0 md:bg-transparent md:p-0">
            <Button type="submit" className="flex-1 md:flex-none" disabled={!isDirty} loading={save.isPending}>Save settings</Button>
          </div>
        )}
      </form>
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title="Save Meta settings?"
        message="Wrong values stop messages, webhooks and Embedded Signup from working. Run “Test connection” after saving."
        confirmLabel="Save"
        loading={save.isPending}
        onConfirm={handleSubmit((v) => save.mutate(v))}
      />
    </>
  );
}

function TestRow({ ok, label }) {
  return (
    <p className="flex items-center gap-2 text-sm">
      {ok ? <CheckCircle2 className="h-5 w-5 text-green-600" /> : <XCircle className="h-5 w-5 text-red-600" />}
      <span className="text-ink">{label}</span>
      <span className="text-muted">{ok ? 'OK' : 'Not working'}</span>
    </p>
  );
}
