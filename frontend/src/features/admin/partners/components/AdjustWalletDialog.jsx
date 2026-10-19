import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button, Input, Modal, Money, Textarea } from '../../../../components/ui';
import { formatMoney, toNumber } from '../../../../lib/format';
import * as walletApi from '../../../../api/endpoints/wallet';
import { useApiMutation } from '../../shared/useApiMutation';

const schema = z.object({
  amount: z
    .string()
    .trim()
    .refine((v) => /^\d+(\.\d{1,2})?$/.test(v) && Number(v) > 0, 'Enter an amount greater than 0 (max 2 decimals)'),
  remarks: z.string().trim().min(5, 'Write a remark of at least 5 characters').max(500),
  confirm: z.string(),
});

/** Manual wallet credit / debit (W3). Requires a remark and typing the amount again to confirm. */
export default function AdjustWalletDialog({ partner, wallet, type, onClose }) {
  const open = Boolean(type);
  const credit = type === 'CREDIT';
  const { register, handleSubmit, reset, watch, formState: { errors } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { amount: '', remarks: '', confirm: '' },
  });
  useEffect(() => {
    if (open) reset({ amount: '', remarks: '', confirm: '' });
  }, [open, reset]);
  const amount = watch('amount');
  const confirmText = watch('confirm');
  const overdraw = !credit && toNumber(amount) > toNumber(wallet?.balance);
  const m = useApiMutation({
    mutationFn: (v) => walletApi.adjustPartnerWallet(partner.id, { type, amount: v.amount, remarks: v.remarks }),
    success: credit ? 'Wallet credited' : 'Amount deducted from wallet',
    invalidate: [['admin', 'partners']],
    onSuccess: onClose,
  });
  const after = credit ? toNumber(wallet?.balance) + toNumber(amount) : toNumber(wallet?.balance) - toNumber(amount);
  const matches = confirmText.trim() !== '' && toNumber(confirmText) === toNumber(amount);
  return (
    <Modal
      open={open}
      onClose={m.isPending ? undefined : onClose}
      title={credit ? `Add credit to ${partner.businessName}` : `Deduct from ${partner.businessName}`}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={m.isPending}>Cancel</Button>
          <Button
            variant={credit ? 'primary' : 'danger'}
            loading={m.isPending}
            disabled={!matches || overdraw}
            onClick={handleSubmit((v) => m.mutate(v))}
          >
            {credit ? 'Credit wallet' : 'Deduct'}
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={handleSubmit((v) => m.mutate(v))} noValidate>
        <p className="text-sm text-muted">
          Current balance: <span className="font-medium text-ink"><Money value={wallet?.balance} /></span>
        </p>
        <Input label="Amount (₹)" inputMode="decimal" required autoFocus error={errors.amount} {...register('amount')} />
        <Textarea label="Remark (shown in the partner's passbook)" required rows={3} maxLength={500} showCount error={errors.remarks} {...register('remarks')} />
        {amount && !errors.amount && (
          <div className={`rounded-lg p-3 text-sm ${overdraw ? 'bg-red-50 text-red-800 dark:bg-red-500/10 dark:text-red-200' : 'bg-surface-2 text-ink'}`}>
            {overdraw ? 'This is more than the available balance. Wallets can never go below zero.' : `Balance after: ${formatMoney(after)}`}
          </div>
        )}
        <Input label="Type the amount again to confirm" inputMode="decimal" autoComplete="off" {...register('confirm')} />
        <button type="submit" className="hidden" aria-hidden />
      </form>
    </Modal>
  );
}
