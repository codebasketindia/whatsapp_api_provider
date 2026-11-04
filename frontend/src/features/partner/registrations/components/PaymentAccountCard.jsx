import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CreditCard } from 'lucide-react';
import toast from 'react-hot-toast';
import * as regApi from '../../../../api/endpoints/registrations';
import { getErrorMessage, getFieldErrors } from '../../../../api/axios';
import { Alert, Badge, Button, CopyButton, Drawer, Input, PasswordInput, Switch } from '../../../../components/ui';
import { formatDateTime } from '../../../../lib/format';

function AccountDrawer({ open, account, onClose }) {
  const qc = useQueryClient();
  const [v, setV] = useState({ keyId: '', keySecret: '', webhookSecret: '', enabled: true });
  const [errors, setErrors] = useState({});
  useEffect(() => {
    if (open) {
      setV({ keyId: account?.keyId || '', keySecret: '', webhookSecret: '', enabled: account?.enabled ?? true });
      setErrors({});
    }
  }, [open, account]);
  const save = useMutation({
    mutationFn: () => regApi.savePaymentAccount({ keyId: v.keyId.trim() || null, keySecret: v.keySecret || null, webhookSecret: v.webhookSecret || null, enabled: v.enabled }),
    onSuccess: () => {
      toast.success(v.enabled ? 'Razorpay connected' : 'Saved');
      qc.invalidateQueries({ queryKey: ['partner', 'payment-account'] });
      qc.invalidateQueries({ queryKey: ['partner', 'registration-forms'] });
      onClose();
    },
    onError: (e) => {
      setErrors(getFieldErrors(e));
      toast.error(getErrorMessage(e));
    },
  });
  return (
    <Drawer open={open} onClose={onClose} width="md:w-[520px]" title="Your Razorpay account"
            description="Registration fees are paid straight into your own Razorpay account."
            footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save & test</Button></>}>
      <div className="space-y-4">
        <Alert variant="info" title="Where to find the keys">
          Razorpay Dashboard → Account & Settings → API Keys → Generate key. Use <code>rzp_test_…</code> keys to try it first, then <code>rzp_live_…</code>.
        </Alert>
        <Input label="Key ID" required value={v.keyId} placeholder="rzp_live_XXXXXXXX" error={errors.keyId} onChange={(e) => setV({ ...v, keyId: e.target.value })} />
        <PasswordInput label="Key Secret" value={v.keySecret} error={errors.keySecret} autoComplete="off"
                       placeholder={account?.keySecretSet ? 'Saved — type to replace' : 'Paste the key secret'} onChange={(e) => setV({ ...v, keySecret: e.target.value })} />
        <div>
          <p className="label-base">Webhook (recommended)</p>
          <p className="mb-2 text-xs text-muted">Razorpay Dashboard → Webhooks → Add: paste this URL, choose events <b>payment.captured</b>, <b>order.paid</b> and <b>payment.failed</b>, set a secret and enter the same secret below. Payments are then confirmed even if the customer closes the page.</p>
          <div className="mb-3 flex min-w-0 items-center gap-2 rounded-lg bg-surface-2 px-3 py-2 text-sm">
            <span className="min-w-0 flex-1 truncate">{account?.webhookUrl}</span>
            <CopyButton value={account?.webhookUrl || ''} toastMessage="Webhook URL copied" />
          </div>
          <PasswordInput label="Webhook secret" value={v.webhookSecret} autoComplete="off"
                         placeholder={account?.webhookSecretSet ? 'Saved — type to replace' : 'Same secret you set in Razorpay'} onChange={(e) => setV({ ...v, webhookSecret: e.target.value })} />
        </div>
        <Switch checked={v.enabled} onChange={(c) => setV({ ...v, enabled: c })} label="Take registration fees online" description="We check the keys with Razorpay when you save." />
      </div>
    </Drawer>
  );
}

/** PA1: status of the partner's own Razorpay account, with set-up drawer. */
export default function PaymentAccountCard() {
  const [open, setOpen] = useState(false);
  const q = useQuery({ queryKey: ['partner', 'payment-account'], queryFn: regApi.getPaymentAccount });
  const a = q.data;
  const ready = a?.enabled && a?.keySecretSet;
  return (
    <div className="card mb-5 flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
      <CreditCard className="h-6 w-6 shrink-0 text-brand-600" aria-hidden />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-semibold text-ink">Online payments (Razorpay)</p>
          {a && <Badge tone={ready ? 'green' : 'gray'} dot>{ready ? 'Connected' : 'Not set up'}</Badge>}
        </div>
        <p className="text-sm text-muted">
          {ready
            ? `Fees go to your Razorpay account ${a.keyId}.${a.verifiedAt ? ` Checked ${formatDateTime(a.verifiedAt)}.` : ''}${a.webhookSecretSet ? '' : ' Add the webhook for extra safety.'}`
            : 'Connect your Razorpay account to charge a registration fee. The pass is sent only after payment.'}
        </p>
      </div>
      <Button variant={ready ? 'secondary' : 'primary'} onClick={() => setOpen(true)} disabled={!a}>{ready ? 'Settings' : 'Connect Razorpay'}</Button>
      <AccountDrawer open={open} account={a} onClose={() => setOpen(false)} />
    </div>
  );
}
