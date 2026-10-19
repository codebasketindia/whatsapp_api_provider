import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RotateCw, Send } from 'lucide-react';
import toast from 'react-hot-toast';
import * as developerApi from '../../../../api/endpoints/developer';
import { getErrorMessage } from '../../../../api/axios';
import {
  Alert, Button, Card, Checkbox, ConfirmDialog, CopyButton, DateTime, ErrorState, InfoTip, Input, KeyValue, Modal, PageLoader, Select, StatusBadge, Switch,
} from '../../../../components/ui';

export const EVENTS = [
  { value: 'message.status', label: 'Message status', description: 'sent, delivered, read, failed' },
  { value: 'message.inbound', label: 'Incoming messages', description: 'Customer replies and new chats' },
  { value: 'template.status', label: 'Template status', description: 'Approved, rejected, paused' },
  { value: 'sender.status', label: 'Number status', description: 'Quality changes, pauses, disconnects' },
  { value: 'wallet.low_balance', label: 'Low wallet balance', description: 'When your balance drops below your alert level' },
];

function SecretShown({ secret, onClose }) {
  return (
    <Modal open={Boolean(secret)} onClose={onClose} title="Your webhook signing secret" footer={<Button onClick={onClose}>I have saved it</Button>}>
      <Alert variant="warning" className="mb-3">Shown only once. Use it to check the X-CB-Signature header on every request.</Alert>
      <div className="flex items-center gap-2 rounded-lg bg-surface-2 p-3 font-mono text-sm"><span className="min-w-0 flex-1 break-all">{secret}</span><CopyButton value={secret} /></div>
    </Modal>
  );
}

export default function WebhookTab() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['partner', 'webhook'], queryFn: developerApi.getWebhookConfig, retry: (n, e) => e?.response?.status !== 404 && n < 1 });
  const [form, setForm] = useState({ url: '', events: EVENTS.map((e) => e.value), isActive: true });
  const [secret, setSecret] = useState(null);
  const [rotateOpen, setRotateOpen] = useState(false);
  const [testEvent, setTestEvent] = useState('message.status');
  const [testResult, setTestResult] = useState(null);
  useEffect(() => {
    if (q.data?.url) setForm({ url: q.data.url, events: q.data.events || [], isActive: q.data.isActive });
  }, [q.data]);
  const save = useMutation({
    mutationFn: () => developerApi.updateWebhookConfig(form),
    onSuccess: (d) => {
      qc.setQueryData(['partner', 'webhook'], d);
      toast.success('Webhook saved');
      if (d?.secret) setSecret(d.secret);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const rotate = useMutation({
    mutationFn: developerApi.rotateWebhookSecret,
    onSuccess: (d) => {
      setRotateOpen(false);
      setSecret(d.secret);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const test = useMutation({
    mutationFn: () => developerApi.testWebhook(testEvent),
    onSuccess: setTestResult,
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  if (q.isLoading) return <PageLoader />;
  if (q.isError && q.error?.response?.status !== 404) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const configured = Boolean(q.data?.url);
  const urlError = form.url && !/^https:\/\/\S+$/.test(form.url) ? 'Use an https:// address' : null;
  const ok = testResult && testResult.responseCode >= 200 && testResult.responseCode < 300;
  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
      <Card title={<span className="inline-flex items-center gap-1">Webhook <InfoTip term="WEBHOOK" /></span>} subtitle="We POST events to this URL, signed with your secret">
        <div className="space-y-4">
          <Input label="Your webhook URL" placeholder="https://yourserver.com/whatsapp/webhook" value={form.url} onChange={(e) => setForm({ ...form, url: e.target.value })} error={urlError} required />
          <div>
            <span className="label-base">Send me these events</span>
            <div className="space-y-1">
              {EVENTS.map((ev) => (
                <Checkbox
                  key={ev.value}
                  label={ev.label}
                  description={ev.description}
                  checked={form.events.includes(ev.value)}
                  onChange={(e) => setForm({ ...form, events: e.target.checked ? [...form.events, ev.value] : form.events.filter((x) => x !== ev.value) })}
                />
              ))}
            </div>
          </div>
          <Switch checked={form.isActive} onChange={(v) => setForm({ ...form, isActive: v })} label="Active" description="Turn off to pause deliveries" />
          <div className="flex flex-wrap justify-end gap-2">
            {configured && <Button variant="secondary" leftIcon={RotateCw} onClick={() => setRotateOpen(true)}>New secret</Button>}
            <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!form.url || Boolean(urlError) || !form.events.length}>Save</Button>
          </div>
        </div>
      </Card>
      <div className="space-y-5">
        {configured && (
          <Card title="Health">
            <KeyValue
              items={[
                { label: 'Signing secret', value: q.data.secretSet ? 'Set' : 'Not set' },
                { label: 'Last successful delivery', value: <DateTime value={q.data.lastSuccessAt} mode="relative" /> },
                { label: 'Failures in a row', value: <StatusBadge status={q.data.consecutiveFailures ? 'FAILED' : 'SUCCESS'} label={String(q.data.consecutiveFailures ?? 0)} /> },
              ]}
            />
          </Card>
        )}
        <Card title="Send a test event" subtitle="Check your server receives and verifies our requests">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <Select className="flex-1" label="Event" value={testEvent} onChange={(e) => setTestEvent(e.target.value)} options={[{ value: 'test.ping', label: 'Test ping' }, ...EVENTS]} />
            <Button leftIcon={Send} onClick={() => test.mutate()} loading={test.isPending} disabled={!configured}>Send test</Button>
          </div>
          {!configured && <p className="mt-2 text-xs text-muted">Save your webhook URL first.</p>}
          {testResult && (
            <Alert variant={ok ? 'success' : 'error'} className="mt-4" title={ok ? 'Your server accepted the event' : 'Your server did not accept the event'}>
              HTTP {testResult.responseCode ?? '—'} · {testResult.durationMs ?? '—'} ms
              {testResult.responseBody && <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap rounded bg-black/5 p-2 text-xs dark:bg-white/10">{testResult.responseBody}</pre>}
            </Alert>
          )}
        </Card>
      </div>
      <SecretShown secret={secret} onClose={() => setSecret(null)} />
      <ConfirmDialog
        open={rotateOpen}
        onClose={() => setRotateOpen(false)}
        onConfirm={() => rotate.mutate()}
        loading={rotate.isPending}
        title="Create a new signing secret?"
        message="The old secret stops working immediately. Update your server right after."
        confirmLabel="Create new secret"
      />
    </div>
  );
}
