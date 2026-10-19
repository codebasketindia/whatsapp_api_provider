import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowDown, ArrowUp, Package, Pencil, Plus, Star, Trash2 } from 'lucide-react';
import * as walletApi from '../../../../api/endpoints/wallet';
import { formatMoney, toNumber } from '../../../../lib/format';
import {
  Badge, Button, ConfirmDialog, Drawer, EmptyState, ErrorState, IconButton, Input, Money, PageHeader, RadioCards, Skeleton, Switch, Textarea,
} from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';
import { packCredit } from '../components/packMath';

export default function CreditPacksPage() {
  const [editing, setEditing] = useState(null); // pack | 'new'
  const [del, setDel] = useState(null);
  const q = useQuery({ queryKey: ['admin', 'credit-packs'], queryFn: () => walletApi.listCreditPacksAdmin() });
  const packs = q.data || [];
  const inv = [['admin', 'credit-packs']];
  const delM = useApiMutation({ mutationFn: (p) => walletApi.deleteCreditPack(p.id), success: 'Pack removed', invalidate: inv, onSuccess: () => setDel(null) });
  const moveM = useApiMutation({
    mutationFn: async ({ a, b }) => {
      await walletApi.updateCreditPack(a.id, toBody(a, b.sortOrder));
      await walletApi.updateCreditPack(b.id, toBody(b, a.sortOrder));
    },
    invalidate: inv,
  });
  const move = (i, dir) => {
    const a = packs[i];
    const b = packs[i + dir];
    if (!a || !b) return;
    const so = a.sortOrder === b.sortOrder ? { ...b, sortOrder: b.sortOrder + dir } : b;
    moveM.mutate({ a, b: so });
  };

  return (
    <>
      <PageHeader
        title="Credit packs"
        subtitle="Ready-made top-up amounts with bonus credit, e.g. pay ₹1,000 and get ₹1,100."
        actions={<Button leftIcon={Plus} onClick={() => setEditing('new')}>Add pack</Button>}
      />
      {q.isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">{[1, 2, 3].map((i) => <Skeleton key={i} className="h-48" />)}</div>
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={q.refetch} />
      ) : packs.length === 0 ? (
        <div className="card"><EmptyState icon={Package} title="No credit packs yet" text="Packs make it easy for partners to buy credit in one tap." action={<Button leftIcon={Plus} onClick={() => setEditing('new')}>Add pack</Button>} /></div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {packs.map((p, i) => (
            <div key={p.id} className={`card relative flex flex-col p-5 ${p.isPopular ? 'ring-2 ring-brand-500' : ''} ${p.isActive ? '' : 'opacity-70'}`}>
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h3 className="text-lg font-semibold text-ink">{p.name}</h3>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {p.isPopular && <Badge tone="brand"><Star className="mr-1 inline h-3 w-3" />Popular</Badge>}
                    {p.isActive ? <Badge tone="green">Visible to partners</Badge> : <Badge tone="gray">Hidden</Badge>}
                  </div>
                </div>
                <div className="flex">
                  <IconButton icon={ArrowUp} label="Move up" disabled={i === 0 || moveM.isPending} onClick={() => move(i, -1)} />
                  <IconButton icon={ArrowDown} label="Move down" disabled={i === packs.length - 1 || moveM.isPending} onClick={() => move(i, 1)} />
                </div>
              </div>
              <p className="mt-4 text-sm text-muted">Partner pays</p>
              <p className="text-2xl font-bold text-ink"><Money value={p.price} /></p>
              <p className="mt-2 text-sm text-muted">Wallet gets</p>
              <p className="text-xl font-semibold text-green-700 dark:text-green-300">
                <Money value={p.creditAmount ?? packCredit(p).credit} />
                {toNumber(p.bonusAmount ?? packCredit(p).bonus) > 0 && (
                  <span className="ml-2 text-sm font-normal">(+{formatMoney(p.bonusAmount ?? packCredit(p).bonus)} bonus)</span>
                )}
              </p>
              {p.description && <p className="mt-3 text-sm text-muted">{p.description}</p>}
              <div className="mt-auto flex gap-2 pt-4">
                <Button size="sm" variant="secondary" leftIcon={Pencil} className="flex-1" onClick={() => setEditing(p)}>Edit</Button>
                <Button size="sm" variant="danger-outline" leftIcon={Trash2} onClick={() => setDel(p)}>Delete</Button>
              </div>
            </div>
          ))}
        </div>
      )}
      <PackDrawer pack={editing === 'new' ? null : editing} open={Boolean(editing)} onClose={() => setEditing(null)} nextSort={(packs.at(-1)?.sortOrder ?? 0) + 1} />
      <ConfirmDialog
        open={Boolean(del)}
        onClose={() => setDel(null)}
        danger
        title={`Delete "${del?.name}"?`}
        message="Partners will no longer see this pack. Past purchases are not affected (packs already used are hidden instead of deleted)."
        confirmLabel="Delete"
        loading={delM.isPending}
        onConfirm={() => delM.mutate(del)}
      />
    </>
  );
}

const toBody = (p, sortOrder = p.sortOrder) => ({
  name: p.name, description: p.description, price: p.price, bonusType: p.bonusType, bonusValue: p.bonusValue,
  isPopular: p.isPopular, isActive: p.isActive, sortOrder,
});

const money = z.string().trim().refine((v) => /^\d+(\.\d{1,2})?$/.test(v), 'Enter an amount like 1000');
const schema = z
  .object({
    name: z.string().trim().min(2, 'Enter a name').max(100),
    description: z.string().trim().max(255).optional(),
    price: money.refine((v) => Number(v) > 0, 'Price must be more than 0'),
    bonusType: z.enum(['FLAT', 'PERCENT']),
    bonusValue: money,
    isPopular: z.boolean(),
    isActive: z.boolean(),
    sortOrder: z.coerce.number().int().min(0),
  })
  .refine((v) => v.bonusType !== 'PERCENT' || Number(v.bonusValue) <= 100, { path: ['bonusValue'], message: 'Bonus % must be 100 or less' });

function PackDrawer({ pack, open, onClose, nextSort }) {
  const { register, handleSubmit, reset, watch, setValue, formState: { errors } } = useForm({ resolver: zodResolver(schema) });
  useEffect(() => {
    if (open) {
      reset(
        pack
          ? { ...pack, price: String(toNumber(pack.price)), bonusValue: String(toNumber(pack.bonusValue)), description: pack.description || '' }
          : { name: '', description: '', price: '', bonusType: 'PERCENT', bonusValue: '0', isPopular: false, isActive: true, sortOrder: nextSort },
      );
    }
  }, [open, pack, reset, nextSort]);
  const m = useApiMutation({
    mutationFn: (v) => (pack ? walletApi.updateCreditPack(pack.id, toBody(v)) : walletApi.createCreditPack(toBody(v))),
    success: pack ? 'Pack updated' : 'Pack created',
    invalidate: [['admin', 'credit-packs']],
    onSuccess: onClose,
  });
  const preview = packCredit({ price: watch('price'), bonusType: watch('bonusType'), bonusValue: watch('bonusValue') });
  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={pack ? 'Edit credit pack' : 'New credit pack'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={m.isPending} onClick={handleSubmit((v) => m.mutate(v))}>{pack ? 'Save' : 'Create pack'}</Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={handleSubmit((v) => m.mutate(v))} noValidate>
        <Input label="Name" required placeholder="e.g. Growth" error={errors.name} {...register('name')} />
        <Input label="Price (₹)" required inputMode="decimal" error={errors.price} {...register('price')} />
        <RadioCards
          label="Bonus type"
          name="bonusType"
          columns={2}
          value={watch('bonusType')}
          onChange={(v) => setValue('bonusType', v)}
          options={[{ value: 'PERCENT', label: 'Percentage', description: 'e.g. 10% extra' }, { value: 'FLAT', label: 'Fixed amount', description: 'e.g. ₹100 extra' }]}
        />
        <Input label={watch('bonusType') === 'PERCENT' ? 'Bonus (%)' : 'Bonus (₹)'} inputMode="decimal" error={errors.bonusValue} {...register('bonusValue')} />
        <div className="rounded-lg bg-surface-2 p-3 text-sm">
          Partner pays <b>{formatMoney(preview.price)}</b> and gets <b className="text-green-700 dark:text-green-300">{formatMoney(preview.credit)}</b> credit
          {preview.bonus > 0 && ` (${formatMoney(preview.bonus)} bonus)`}. GST, if enabled, is added at checkout.
        </div>
        <Textarea label="Short description" rows={2} maxLength={255} showCount error={errors.description} {...register('description')} />
        <Input label="Sort order" inputMode="numeric" hint="Lower numbers are shown first." error={errors.sortOrder} {...register('sortOrder')} />
        <Switch checked={watch('isPopular')} onChange={(v) => setValue('isPopular', v)} label="Show “Popular” badge" />
        <Switch checked={watch('isActive')} onChange={(v) => setValue('isActive', v)} label="Visible to partners" />
        <button type="submit" className="hidden" aria-hidden />
      </form>
    </Drawer>
  );
}
