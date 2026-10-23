import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import * as partnersApi from '../../../../api/endpoints/partners';
import * as ratePlansApi from '../../../../api/endpoints/ratePlans';
import { getErrorMessage, getFieldErrors } from '../../../../api/axios';
import { PASSWORD_HINT, PASSWORD_RULE } from '../../../../lib/constants';
import { GSTIN_RE, INDIAN_STATES, PAN_RE } from '../../shared/indianStates';
import {
  Button, Card, Checkbox, ErrorState, Input, PageHeader, PageLoader, PasswordInput, PhoneInput, Select, Switch,
} from '../../../../components/ui';

const optional = (re, msg) =>
  z
    .string()
    .trim()
    .optional()
    .transform((v) => (v ? v.toUpperCase() : ''))
    .refine((v) => !v || re.test(v), msg);

const schema = z.object({
  businessName: z.string().trim().min(2, 'Enter the business name').max(190),
  contactName: z.string().trim().min(2, 'Enter the contact person').max(120),
  email: z.string().trim().email('Enter a valid email'),
  mobile: z.string().regex(/^[1-9]\d{7,14}$/, 'Enter a valid mobile number'),
  gstin: optional(GSTIN_RE, 'Enter a valid 15-character GSTIN'),
  pan: optional(PAN_RE, 'Enter a valid PAN (e.g. ABCDE1234F)'),
  line1: z.string().trim().max(190).optional(),
  line2: z.string().trim().max(190).optional(),
  city: z.string().trim().max(100).optional(),
  stateCode: z.string().optional(),
  pincode: z
    .string()
    .trim()
    .optional()
    .refine((v) => !v || /^[1-9][0-9]{5}$/.test(v), 'Enter a valid 6-digit PIN code'),
  website: z
    .string()
    .trim()
    .optional()
    .refine((v) => !v || /^https?:\/\/\S+\.\S+/.test(v), 'Enter a full URL starting with https://'),
  ratePlanId: z.string().min(1, 'Choose a rate plan'),
  allowOwnNumber: z.boolean(),
  lowBalanceThreshold: z
    .string()
    .optional()
    .refine((v) => !v || (/^\d+(\.\d{1,2})?$/.test(v) && Number(v) >= 0), 'Enter an amount like 500'),
  password: z
    .string()
    .optional()
    .refine((v) => !v || PASSWORD_RULE.test(v), PASSWORD_HINT),
  sendWelcomeEmail: z.boolean(),
});

const empty = {
  businessName: '', contactName: '', email: '', mobile: '', gstin: '', pan: '', line1: '', line2: '', city: '', stateCode: '',
  pincode: '', website: '', ratePlanId: '', allowOwnNumber: false, lowBalanceThreshold: '', password: '', sendWelcomeEmail: true,
};

export default function PartnerFormPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const qc = useQueryClient();

  const partnerQ = useQuery({ queryKey: ['admin', 'partners', 'detail', id], queryFn: () => partnersApi.getPartner(id), enabled: editing });
  const plansQ = useQuery({ queryKey: ['admin', 'rate-plans', 'active'], queryFn: () => ratePlansApi.listRatePlans({ status: 'ACTIVE', includeCustom: true, size: 100 }) });
  const plans = plansQ.data?.content || [];

  const {
    register, handleSubmit, reset, setValue, watch, setError,
    formState: { errors, isDirty },
  } = useForm({ resolver: zodResolver(schema), defaultValues: empty });

  useEffect(() => {
    const p = partnerQ.data;
    if (p) {
      reset({
        businessName: p.businessName || '', contactName: p.contactName || '', email: p.email || '', mobile: p.mobile || '',
        gstin: p.gstin || '', pan: p.pan || '', line1: p.address?.line1 || '', line2: p.address?.line2 || '', city: p.address?.city || '',
        stateCode: p.address?.stateCode || '', pincode: p.address?.pincode || '', website: p.website || '',
        ratePlanId: p.ratePlan?.id ? String(p.ratePlan.id) : '', allowOwnNumber: Boolean(p.allowOwnNumber),
        lowBalanceThreshold: p.settings?.lowBalanceThreshold != null ? String(Number(p.settings.lowBalanceThreshold)) : '',
        password: '', sendWelcomeEmail: false,
      });
    }
  }, [partnerQ.data, reset]);

  useEffect(() => {
    if (!editing && plans.length && !watch('ratePlanId')) {
      const def = plans.find((p) => p.isDefault);
      if (def) setValue('ratePlanId', String(def.id));
    }
  }, [plans, editing, setValue, watch]);

  const save = useMutation({
    mutationFn: (v) => {
      const state = INDIAN_STATES.find((s) => s.code === v.stateCode);
      const body = {
        businessName: v.businessName.trim(),
        contactName: v.contactName.trim(),
        email: v.email.trim().toLowerCase(),
        mobile: v.mobile,
        gstin: v.gstin || null,
        pan: v.pan || null,
        address: {
          line1: v.line1 || null, line2: v.line2 || null, city: v.city || null, state: state?.name || null,
          stateCode: v.stateCode || null, pincode: v.pincode || null, country: 'IN',
        },
        website: v.website || null,
        ratePlanId: Number(v.ratePlanId),
        allowOwnNumber: v.allowOwnNumber,
        lowBalanceThreshold: v.lowBalanceThreshold === '' ? null : v.lowBalanceThreshold,
      };
      if (editing) return partnersApi.updatePartner(id, body);
      return partnersApi.createPartner({ ...body, password: v.password || undefined, sendWelcomeEmail: v.sendWelcomeEmail });
    },
    onSuccess: (p) => {
      toast.success(editing ? 'Partner updated' : 'Partner created. Login details were emailed.');
      qc.invalidateQueries({ queryKey: ['admin', 'partners'] });
      navigate(`/admin/partners/${p?.id ?? id}`);
    },
    onError: (err) => {
      Object.entries(getFieldErrors(err)).forEach(([f, m]) => {
        const field = f.startsWith('address.') ? f.slice(8) : f;
        setError(field, { message: m });
      });
      toast.error(getErrorMessage(err));
    },
  });

  if (editing && partnerQ.isLoading) return <PageLoader />;
  if (editing && partnerQ.isError) return <ErrorState error={partnerQ.error} onRetry={partnerQ.refetch} />;

  const submit = handleSubmit((v) => save.mutate(v));
  return (
    <>
      <PageHeader
        title={editing ? `Edit ${partnerQ.data?.businessName}` : 'Add partner'}
        subtitle={editing ? 'Update business details and settings.' : 'The partner gets an email with login details.'}
        backTo={editing ? `/admin/partners/${id}` : '/admin/partners'}
      />
      <form onSubmit={submit} noValidate className="space-y-4 pb-24 md:pb-4">
        <Card title="Business">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Input label="Business name" required error={errors.businessName} {...register('businessName')} />
            <Input label="Contact person" required error={errors.contactName} {...register('contactName')} />
            <Input label="Email (login)" type="email" required disabled={editing} hint={editing ? 'Login email cannot be changed.' : undefined} error={errors.email} {...register('email')} />
            <PhoneInput label="Mobile number (used to log in)" required value={watch('mobile')} onChange={(v) => setValue('mobile', v, { shouldValidate: true, shouldDirty: true })} error={errors.mobile} />
            <Input label="Website" placeholder="https://example.com" error={errors.website} {...register('website')} />
          </div>
        </Card>
        <Card title="KYC & tax" subtitle="Used on GST invoices.">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Input label="GSTIN" placeholder="22AAAAA0000A1Z5" maxLength={15} className="uppercase" error={errors.gstin} {...register('gstin')} />
            <Input label="PAN" placeholder="ABCDE1234F" maxLength={10} error={errors.pan} {...register('pan')} />
          </div>
        </Card>
        <Card title="Address">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Input label="Address line 1" error={errors.line1} {...register('line1')} />
            <Input label="Address line 2" error={errors.line2} {...register('line2')} />
            <Input label="City" error={errors.city} {...register('city')} />
            <Select label="State" placeholder="Choose state" options={INDIAN_STATES.map((s) => ({ value: s.code, label: s.name }))} error={errors.stateCode} {...register('stateCode')} />
            <Input label="PIN code" inputMode="numeric" maxLength={6} error={errors.pincode} {...register('pincode')} />
          </div>
        </Card>
        <Card title="Account settings">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Select
              label="Rate plan"
              required
              placeholder={plansQ.isLoading ? 'Loading…' : 'Choose rate plan'}
              options={plans.map((p) => ({ value: String(p.id), label: `${p.name}${p.isDefault ? ' (default)' : ''}${p.isCustom ? ' · custom' : ''}` }))}
              error={errors.ratePlanId}
              {...register('ratePlanId')}
            />
            <Input
              label="Low balance alert (₹)"
              inputMode="decimal"
              placeholder="Use platform default"
              hint="Partner gets a banner and email when balance falls below this."
              error={errors.lowBalanceThreshold}
              {...register('lowBalanceThreshold')}
            />
          </div>
          <Switch
            className="mt-4"
            checked={watch('allowOwnNumber')}
            onChange={(v) => setValue('allowOwnNumber', v, { shouldDirty: true })}
            label="Allow connecting own WhatsApp number"
            description="The partner can connect their own number using Meta Embedded Signup."
          />
          {!editing && (
            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
              <PasswordInput
                label="Temporary password (optional)"
                autoComplete="new-password"
                hint={`Leave empty to generate one. ${PASSWORD_HINT}`}
                error={errors.password}
                {...register('password')}
              />
              <Checkbox label="Send welcome email with login details" {...register('sendWelcomeEmail')} />
            </div>
          )}
        </Card>
        <div className="fixed inset-x-0 bottom-0 z-20 flex gap-3 border-t border-line bg-surface p-3 safe-bottom md:static md:justify-end md:border-0 md:bg-transparent md:p-0">
          <Button variant="secondary" className="flex-1 md:flex-none" to={editing ? `/admin/partners/${id}` : '/admin/partners'}>
            Cancel
          </Button>
          <Button type="submit" className="flex-1 md:flex-none" loading={save.isPending} disabled={editing && !isDirty}>
            {editing ? 'Save changes' : 'Create partner'}
          </Button>
        </div>
      </form>
    </>
  );
}
