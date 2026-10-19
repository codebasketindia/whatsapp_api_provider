import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Banknote, CheckCircle2, Gift, Landmark, Smartphone } from 'lucide-react';
import toast from 'react-hot-toast';
import * as walletApi from '../../../../api/endpoints/wallet';
import * as paymentsApi from '../../../../api/endpoints/payments';
import { getErrorMessage, getFieldErrors } from '../../../../api/axios';
import { useAuth } from '../../../../auth/useAuth';
import {
  Alert, Button, Card, EmptyState, ErrorState, FileUpload, Input, PageHeader, RadioCards, Select, Skeleton, Textarea,
} from '../../../../components/ui';
import { openCheckout } from '../../../../lib/razorpay';
import { PAYMENT_MODES } from '../../../../lib/constants';
import { formatMoney, toIstDateParam, toNumber } from '../../../../lib/format';
import { cn } from '../../../../lib/cn';
import { calcTopup } from '../gst';

const QUICK_AMOUNTS = [500, 1000, 2000, 5000, 10000];

function Breakdown({ b }) {
  return (
    <dl className="space-y-1.5 rounded-xl bg-surface-2 p-4 text-sm">
      <div className="flex justify-between"><dt className="text-muted">Amount</dt><dd className="tabular-nums">{formatMoney(b.base)}</dd></div>
      {b.gstPercent > 0 && (
        <div className="flex justify-between"><dt className="text-muted">GST ({b.gstPercent}%)</dt><dd className="tabular-nums">{formatMoney(b.gst)}</dd></div>
      )}
      <div className="flex justify-between border-t border-line pt-1.5 font-semibold"><dt>You pay</dt><dd className="tabular-nums">{formatMoney(b.total)}</dd></div>
      {b.bonus > 0 && (
        <div className="flex justify-between text-green-700 dark:text-green-400"><dt>Bonus credit</dt><dd className="tabular-nums">+{formatMoney(b.bonus)}</dd></div>
      )}
      <div className="flex justify-between font-semibold text-brand-700 dark:text-brand-300"><dt>Added to wallet</dt><dd className="tabular-nums">{formatMoney(b.credit)}</dd></div>
    </dl>
  );
}

/** Creates an order, opens Razorpay, verifies on the server and goes to the result page. */
function usePay(config) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { user } = useAuth();
  const [busy, setBusy] = useState(false);
  const pay = async (body, description) => {
    setBusy(true);
    let order;
    try {
      order = await paymentsApi.createPaymentOrder(body);
    } catch (e) {
      toast.error(getErrorMessage(e));
      setBusy(false);
      return;
    }
    try {
      const res = await openCheckout({
        key: order.keyId || config.keyId,
        order_id: order.gatewayOrderId,
        amount: order.amountPaise,
        currency: order.currency || 'INR',
        name: config.companyName || 'Wallet top-up',
        image: config.logoUrl || undefined,
        description,
        prefill: order.prefill || { name: user?.name, email: user?.email },
        theme: { color: '#25D366' },
        config: {
          display: {
            blocks: { upi: { name: 'Pay using UPI', instruments: [{ method: 'upi' }] } },
            sequence: ['block.upi'],
            preferences: { show_default_blocks: true },
          },
        },
      });
      if (res.status === 'success') {
        try {
          await paymentsApi.verifyPayment({
            gatewayOrderId: res.razorpay_order_id,
            gatewayPaymentId: res.razorpay_payment_id,
            signature: res.razorpay_signature,
          });
        } catch (e) {
          // The server also confirms via webhook; the result page keeps checking.
          toast.error(getErrorMessage(e, 'We are confirming your payment. Please wait.'));
        }
      } else {
        const err = res.error || {};
        await paymentsApi
          .reportPaymentFailed(order.orderUid, {
            code: err.code || (res.status === 'dismissed' ? 'CHECKOUT_CLOSED' : 'PAYMENT_FAILED'),
            description: err.description || (res.status === 'dismissed' ? 'Payment window closed' : 'Payment failed'),
            reason: err.reason || null,
          })
          .catch(() => {});
      }
      qc.invalidateQueries({ queryKey: ['partner'] });
      navigate(`/partner/wallet/payment/${order.orderUid}`);
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return { pay, busy };
}

function UpiOption({ config }) {
  const [amount, setAmount] = useState('1000');
  const { pay, busy } = usePay(config);
  const min = toNumber(config.minAmount) || 1;
  const max = toNumber(config.maxAmount) || 1000000;
  const n = toNumber(amount);
  const error = !amount ? 'Enter an amount' : n < min ? `Minimum ${formatMoney(min)}` : n > max ? `Maximum ${formatMoney(max)}` : !/^\d+(\.\d{1,2})?$/.test(amount) ? 'Enter a valid amount' : null;
  const b = calcTopup({ amount: n, gstEnabled: config.gstEnabled, gstPercent: config.gstPercent });
  return (
    <Card title="Pay by UPI" subtitle="Instant. Pay with any UPI app, QR code, card or net banking.">
      <Input
        label="Amount to add"
        prefix="₹"
        inputMode="decimal"
        value={amount}
        onChange={(e) => setAmount(e.target.value.replace(/[^\d.]/g, ''))}
        error={amount && error ? error : undefined}
        hint={`Between ${formatMoney(min)} and ${formatMoney(max)}`}
      />
      <div className="mt-3 flex flex-wrap gap-2">
        {QUICK_AMOUNTS.filter((a) => a >= min && a <= max).map((a) => (
          <button
            key={a}
            type="button"
            onClick={() => setAmount(String(a))}
            className={cn('min-h-[40px] rounded-full border px-4 text-sm', n === a ? 'border-brand-600 bg-brand-600 text-white' : 'border-line hover:bg-surface-2')}
          >
            {formatMoney(a).replace('.00', '')}
          </button>
        ))}
      </div>
      {!error && <div className="mt-4"><Breakdown b={b} /></div>}
      <Button className="mt-4" fullWidth size="lg" disabled={Boolean(error)} loading={busy} onClick={() => pay({ amount: b.base.toFixed(2) }, 'Wallet top-up')}>
        Pay {error ? '' : formatMoney(b.total)}
      </Button>
      <p className="mt-2 text-center text-xs text-muted">Your wallet is updated only after we confirm the payment with the bank.</p>
    </Card>
  );
}

function PacksOption({ config }) {
  const q = useQuery({ queryKey: ['partner', 'credit-packs'], queryFn: walletApi.listCreditPacks });
  const [selected, setSelected] = useState(null);
  const { pay, busy } = usePay(config);
  if (q.isLoading) return <Skeleton className="h-48 w-full" />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  if (!q.data?.length) return <div className="card"><EmptyState icon={Gift} title="No packs available right now" text="Use Pay by UPI to add any amount." /></div>;
  const pack = q.data.find((p) => p.id === selected);
  const b = pack ? calcTopup({ pack, gstEnabled: config.gstEnabled, gstPercent: config.gstPercent }) : null;
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {q.data.map((p) => (
          <button
            key={p.id}
            type="button"
            onClick={() => setSelected(p.id)}
            className={cn(
              'card relative flex flex-col items-start p-5 text-left transition-colors',
              selected === p.id ? 'ring-2 ring-brand-600' : 'hover:bg-surface-2',
            )}
          >
            {p.isPopular && <span className="absolute right-3 top-3 rounded-full bg-brand-600 px-2 py-0.5 text-[10px] font-bold uppercase text-white">Popular</span>}
            <span className="text-sm font-medium text-muted">{p.name}</span>
            <span className="mt-1 text-2xl font-bold text-ink">{formatMoney(p.price).replace('.00', '')}</span>
            <span className="mt-1 text-sm font-medium text-green-700 dark:text-green-400">Get {formatMoney(p.creditAmount)} credit</span>
            {p.description && <span className="mt-2 text-xs text-muted">{p.description}</span>}
            {selected === p.id && <CheckCircle2 className="absolute bottom-3 right-3 h-6 w-6 text-brand-600" aria-hidden />}
          </button>
        ))}
      </div>
      {pack && (
        <Card>
          <Breakdown b={b} />
          <Button className="mt-4" fullWidth size="lg" loading={busy} onClick={() => pay({ creditPackId: pack.id }, pack.name)}>
            Pay {formatMoney(b.total)}
          </Button>
        </Card>
      )}
    </div>
  );
}

const manualSchema = z
  .object({
    amount: z.string().regex(/^\d+(\.\d{1,2})?$/, 'Enter a valid amount').refine((v) => Number(v) >= 1, 'Enter an amount'),
    paymentMode: z.enum(['UPI', 'NEFT', 'IMPS', 'CASH', 'OTHER']),
    utr: z.string().trim().max(64, 'Too long'),
    paymentDate: z.string().min(1, 'Choose the payment date'),
    screenshot: z.any().nullable(),
    note: z.string().max(500, 'At most 500 characters').optional(),
  })
  .superRefine((v, ctx) => {
    if (v.paymentMode !== 'CASH' && !v.utr) ctx.addIssue({ path: ['utr'], code: 'custom', message: 'Enter the UTR / transaction reference' });
    if (v.paymentMode !== 'CASH' && !v.screenshot) ctx.addIssue({ path: ['screenshot'], code: 'custom', message: 'Attach the payment screenshot or receipt' });
    if (v.paymentDate > toIstDateParam()) ctx.addIssue({ path: ['paymentDate'], code: 'custom', message: 'Date cannot be in the future' });
  });

function ManualOption() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { register, handleSubmit, control, watch, setError, formState: { errors } } = useForm({
    resolver: zodResolver(manualSchema),
    defaultValues: { amount: '', paymentMode: 'UPI', utr: '', paymentDate: toIstDateParam(), screenshot: null, note: '' },
  });
  const m = useMutation({
    mutationFn: (v) => walletApi.createTopupRequest({ ...v, utr: v.utr || undefined, screenshot: v.screenshot || undefined }),
    onSuccess: () => {
      toast.success('Request sent. We will add the money after checking your payment.');
      qc.invalidateQueries({ queryKey: ['partner', 'topups'] });
      navigate('/partner/wallet?tab=requests');
    },
    onError: (e) => {
      Object.entries(getFieldErrors(e)).forEach(([k, msg]) => setError(k, { message: msg }));
      toast.error(getErrorMessage(e));
    },
  });
  const mode = watch('paymentMode');
  return (
    <Card title="I already paid" subtitle="Paid us by bank transfer, UPI or cash? Send the details and we will add it to your wallet.">
      <form onSubmit={handleSubmit((v) => m.mutate(v))} className="grid grid-cols-1 gap-4 sm:grid-cols-2" noValidate>
        <Input label="Amount paid" prefix="₹" inputMode="decimal" required {...register('amount')} error={errors.amount} />
        <Select label="Paid by" options={PAYMENT_MODES} required {...register('paymentMode')} error={errors.paymentMode} />
        <Input
          label={mode === 'CASH' ? 'Receipt number (optional)' : 'UTR / reference number'}
          {...register('utr')}
          error={errors.utr}
          hint="You can find it in your bank or UPI app"
          required={mode !== 'CASH'}
        />
        <Input label="Payment date" type="date" max={toIstDateParam()} required {...register('paymentDate')} error={errors.paymentDate} />
        <Controller
          control={control}
          name="screenshot"
          render={({ field }) => (
            <FileUpload
              className="sm:col-span-2"
              label="Screenshot or receipt"
              accept="image/jpeg,image/png,application/pdf"
              maxSizeMB={2}
              value={field.value}
              onChange={field.onChange}
              error={errors.screenshot}
              hint="JPG, PNG or PDF · max 2 MB"
            />
          )}
        />
        <Textarea className="sm:col-span-2" label="Note (optional)" rows={2} maxLength={500} {...register('note')} error={errors.note} />
        <Alert variant="info" className="sm:col-span-2">We usually check requests within a few working hours. Each UTR can be used only once.</Alert>
        <div className="sm:col-span-2">
          <Button type="submit" fullWidth size="lg" loading={m.isPending}>Send request</Button>
        </div>
      </form>
    </Card>
  );
}

export default function AddMoneyPage() {
  const [params, setParams] = useSearchParams();
  const cfg = useQuery({ queryKey: ['partner', 'payments', 'config'], queryFn: paymentsApi.getPaymentConfig });
  const onlineEnabled = Boolean(cfg.data?.enabled);
  const options = useMemo(
    () => [
      { value: 'upi', label: 'Pay by UPI', description: 'Instant top-up with any UPI app', icon: Smartphone, disabled: !onlineEnabled },
      { value: 'pack', label: 'Buy a pack', description: 'Pay more, get bonus credit', icon: Gift, badge: 'Bonus', disabled: !onlineEnabled },
      { value: 'manual', label: 'I already paid', description: 'Bank transfer, UPI or cash', icon: Landmark },
    ],
    [onlineEnabled],
  );
  const requested = params.get('option');
  const option = requested || (cfg.isSuccess && !onlineEnabled ? 'manual' : 'upi');
  const w = useQuery({ queryKey: ['partner', 'wallet'], queryFn: walletApi.getMyWallet });

  return (
    <>
      <PageHeader title="Add money" subtitle={w.data ? `Current balance ${formatMoney(w.data.balance)}` : undefined} backTo="/partner/wallet" backLabel="Wallet" />
      {cfg.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : (
        <div className="mx-auto max-w-3xl space-y-5">
          {cfg.isError && <ErrorState error={cfg.error} onRetry={cfg.refetch} />}
          {cfg.isSuccess && !onlineEnabled && (
            <Alert variant="info" title="Online payment is not available right now">
              You can still pay by bank transfer or UPI and send us the details below.
            </Alert>
          )}
          <RadioCards
            name="option"
            columns={3}
            value={option}
            onChange={(v) => setParams({ option: v }, { replace: true })}
            options={options}
          />
          {option === 'upi' && onlineEnabled && <UpiOption config={cfg.data} />}
          {option === 'pack' && onlineEnabled && <PacksOption config={cfg.data} />}
          {(option === 'manual' || ((option === 'upi' || option === 'pack') && !onlineEnabled && cfg.isSuccess)) && <ManualOption />}
          {option !== 'manual' && onlineEnabled && (
            <p className="flex items-center justify-center gap-2 text-xs text-muted">
              <Banknote className="h-4 w-4" aria-hidden /> Payments are processed securely by Razorpay.
            </p>
          )}
        </div>
      )}
    </>
  );
}
