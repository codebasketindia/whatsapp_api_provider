import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import * as regApi from '../../../../api/endpoints/registrations';
import * as contactsApi from '../../../../api/endpoints/contacts';
import { getErrorMessage, getFieldErrors } from '../../../../api/axios';
import { Button, Card, Input, PageHeader, QueryState, Select, Switch, Textarea } from '../../../../components/ui';
import SenderSelect from '../../common/SenderSelect';
import TagInput from '../../common/TagInput';
import { defaultSenderId, useMySenders } from '../../common/useSenders';
import { istLocalToIso } from '../../campaigns/components/schedule';
import { isoToIstLocal } from '../../scheduled/components/scheduleTime';
import PassDesignCard from '../components/PassDesignCard';
import FollowUpsCard from '../components/FollowUpsCard';
import PageSettingsCard from '../components/PageSettingsCard';
import PackagesCard, { formToPackage, packageToForm } from '../components/PackagesCard';
import { HARMAIN_LAYOUT, slugify } from '../components/regHelpers';

const EMPTY = {
  title: '', description: '', slug: '', senderId: '', status: 'ACTIVE', closesAt: '', maxRegistrations: '',
  consentText: '', successMessage: '', collectPhoto: false, passEnabled: true, passDesignFile: null, passLayout: HARMAIN_LAYOUT,
  feeAmount: '', feeLabel: '', packages: [], termsText: '', privacyText: '',
  heroImageUrl: '', pageHeading: '', pageNote: '', ctaLabel: '', comboTitle: '', comboSubtitle: '', comboCodes: '', comboMrp: '', comboPrice: '', comboValidUntil: '', invoiceName: '',
  passPrefix: 'PASS', passTemplateId: null, passTemplateBody: [], passCaption: '', contactGroupId: '', contactTags: [], followUps: [],
};

const fromDto = (f) => ({
  ...EMPTY,
  ...f,
  senderId: f.sender?.id || '',
  closesAt: f.closesAt ? isoToIstLocal(f.closesAt) : '',
  maxRegistrations: f.maxRegistrations ?? '',
  contactGroupId: f.contactGroupId ?? '',
  passLayout: f.passLayout || HARMAIN_LAYOUT,
  description: f.description || '',
  successMessage: f.successMessage || '',
  passCaption: f.passCaption || '',
  feeAmount: f.feeAmount ?? '',
  feeLabel: f.feeLabel || '',
  packages: (f.packages || []).map(packageToForm),
  termsText: f.termsText || '',
  privacyText: f.privacyText || '',
  heroImageUrl: f.heroImageUrl || '', pageHeading: f.pageHeading || '', pageNote: f.pageNote || '', ctaLabel: f.ctaLabel || '',
  comboTitle: f.comboTitle || '', comboSubtitle: f.comboSubtitle || '', comboCodes: f.comboCodes || '',
  comboMrp: f.comboMrp ?? '', comboPrice: f.comboPrice ?? '', comboValidUntil: f.comboValidUntil ? isoToIstLocal(f.comboValidUntil) : '',
  invoiceName: f.invoiceName || '',
});

function toBody(v) {
  return {
    title: v.title.trim(),
    description: v.description?.trim() || null,
    slug: v.slug?.trim() || null,
    senderId: Number(v.senderId),
    status: v.status,
    closesAt: v.closesAt ? istLocalToIso(v.closesAt) : null,
    maxRegistrations: v.maxRegistrations === '' ? null : Number(v.maxRegistrations),
    consentText: v.consentText?.trim() || null,
    successMessage: v.successMessage?.trim() || null,
    collectPhoto: v.collectPhoto,
    passEnabled: v.passEnabled,
    passDesign: v.passDesignFile ? null : 'harmain',
    passDesignFileUid: v.passDesignFile?.fileUid || null,
    passLayout: v.passLayout,
    passPrefix: v.passPrefix || 'PASS',
    passTemplateId: v.passTemplateId || null,
    passTemplateBody: v.passTemplateBody || [],
    passCaption: v.passCaption?.trim() || null,
    contactGroupId: v.contactGroupId === '' ? null : Number(v.contactGroupId),
    contactTags: v.contactTags,
    followUps: v.followUps,
    feeAmount: v.feeAmount === '' ? null : Number(v.feeAmount),
    feeLabel: v.feeLabel?.trim() || null,
    packages: v.packages.map(formToPackage),
    termsText: v.termsText ?? '',
    privacyText: v.privacyText ?? '',
    heroImageUrl: v.heroImageUrl?.trim() || null, pageHeading: v.pageHeading?.trim() || null, pageNote: v.pageNote?.trim() || null,
    ctaLabel: v.ctaLabel?.trim() || null, comboTitle: v.comboTitle?.trim() || null, comboSubtitle: v.comboSubtitle?.trim() || null,
    comboCodes: v.comboCodes?.trim() || null, comboMrp: v.comboMrp === '' ? null : Number(v.comboMrp),
    comboPrice: v.comboPrice === '' ? null : Number(v.comboPrice), comboValidUntil: v.comboValidUntil ? istLocalToIso(v.comboValidUntil) : null,
    invoiceName: v.invoiceName?.trim() || null,
  };
}

function Editor({ initial, id }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const senders = useMySenders();
  const [v, setV] = useState(initial);
  const [errors, setErrors] = useState({});
  const set = (patch) => setV((s) => ({ ...s, ...patch }));
  useEffect(() => {
    if (!v.senderId && senders.data?.length) set({ senderId: defaultSenderId(senders.data) });
  }, [senders.data, v.senderId]);
  const account = useQuery({ queryKey: ['partner', 'payment-account'], queryFn: regApi.getPaymentAccount });
  const paymentsReady = Boolean(account.data?.enabled && account.data?.keySecretSet);
  const groups = useQuery({ queryKey: ['partner', 'contact-groups', 'all'], queryFn: () => contactsApi.listContactGroups({ size: 100 }) });

  const save = useMutation({
    mutationFn: () => (id ? regApi.updateForm(id, toBody(v)) : regApi.createForm(toBody(v))),
    onSuccess: (f) => {
      toast.success(id ? 'Registration link saved' : 'Registration link created');
      qc.invalidateQueries({ queryKey: ['partner', 'registration-forms'] });
      navigate(`/partner/registrations/${f.id}`);
    },
    onError: (e) => {
      setErrors(getFieldErrors(e));
      toast.error(getErrorMessage(e));
    },
  });

  const valid = v.title.trim().length > 0 && v.senderId;
  return (
    <div className="space-y-5">
      <Card title="Registration link" subtitle="People open this link, enter their name and WhatsApp number, and get their pass.">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <Input label="Title" required maxLength={160} value={v.title} error={errors.title}
                 placeholder="e.g. Free Harmain Ziyarat Pass"
                 onChange={(e) => set({ title: e.target.value, ...(!id && !v.slugTouched ? { slug: slugify(e.target.value) } : {}) })} />
          <SenderSelect value={v.senderId} onChange={(s) => set({ senderId: s })} label="WhatsApp number that sends the pass" error={errors.senderId} />
          <Input label="Link name" value={v.slug} error={errors.slug} maxLength={63}
                 hint={`${window.location.origin}/r/${v.slug || 'your-link'}`}
                 onChange={(e) => set({ slug: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''), slugTouched: true })} />
          <Select label="Status" value={v.status} onChange={(e) => set({ status: e.target.value })}
                  options={[{ value: 'ACTIVE', label: 'Open' }, { value: 'PAUSED', label: 'Paused' }, { value: 'CLOSED', label: 'Closed' }]} />
          <Input type="datetime-local" label="Close registrations on (optional)" value={v.closesAt} onChange={(e) => set({ closesAt: e.target.value })} />
          <Input type="number" min={1} label="Maximum registrations (optional)" value={v.maxRegistrations} error={errors.maxRegistrations}
                 onChange={(e) => set({ maxRegistrations: e.target.value })} />
          <Textarea className="md:col-span-2" label="Description shown on the page" rows={3} maxLength={2000} value={v.description}
                    onChange={(e) => set({ description: e.target.value })} />
          <Input className="md:col-span-2" label="Consent line (tick box)" maxLength={500} value={v.consentText} error={errors.consentText}
                 placeholder="I agree to receive my pass and updates on WhatsApp"
                 onChange={(e) => set({ consentText: e.target.value })} />
          <Textarea className="md:col-span-2" label="Thank-you message (optional)" rows={2} maxLength={1000} value={v.successMessage}
                    onChange={(e) => set({ successMessage: e.target.value })} />
          <Switch checked={v.collectPhoto} onChange={(c) => set({ collectPhoto: c })} label="Ask for a photo (optional for the person)"
                  description="The photo is printed in the photo box of the pass." />
        </div>
      </Card>
      <PageSettingsCard v={v} set={set} errors={errors} />
      <PackagesCard value={v.packages} onChange={(packages) => set({ packages })} termsText={v.termsText} privacyText={v.privacyText}
                    onLegal={set} errors={errors} />
      <Card title="Registration fee" subtitle={v.packages.length ? 'Not used: this link charges by its packages above.' : 'Leave empty for a free registration. With a fee, the pass is created and sent only after payment.'}>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <Input type="number" min={1} step="0.01" label="Fee (₹)" value={v.feeAmount} error={errors.feeAmount}
                 placeholder="Free" onChange={(e) => set({ feeAmount: e.target.value })}
                 hint={paymentsReady ? 'Paid into your Razorpay account.' : 'Connect Razorpay on the Registrations page first.'} />
          <Input label="Fee name" maxLength={120} value={v.feeLabel} placeholder="Registration fee" onChange={(e) => set({ feeLabel: e.target.value })}
                 hint={`Shown next to the amount on the registration page and in Razorpay: “${v.feeLabel?.trim() || 'Registration fee'}”. e.g. Entry pass, Seminar ticket, Umrah booking advance.`} />
        </div>
      </Card>
      <PassDesignCard value={v} onChange={(next) => setV(next)} senderId={v.senderId} />
      <Card title="Save people as contacts" subtitle="Every registration becomes a contact, so you can also run campaigns to them.">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <Select label="Add to contact group" value={v.contactGroupId} onChange={(e) => set({ contactGroupId: e.target.value })}
                  options={(groups.data?.content || []).map((g) => ({ value: g.id, label: g.name }))} placeholder="No group" error={errors.contactGroupId} />
          <TagInput label="Tags" value={v.contactTags} onChange={(t) => set({ contactTags: t })} max={10} transform={(s) => s.toLowerCase()} />
        </div>
      </Card>
      <FollowUpsCard value={v.followUps} onChange={(f) => set({ followUps: f })} senderId={v.senderId} />
      {Object.keys(errors).some((k) => k.startsWith('followUps') || k.startsWith('pass')) && (
        <p className="text-sm text-red-600">{Object.entries(errors).filter(([k]) => k.startsWith('followUps') || k.startsWith('pass')).map(([, m]) => m).join(' · ')}</p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" to="/partner/registrations">Cancel</Button>
        <Button loading={save.isPending} disabled={!valid} onClick={() => save.mutate()}>{id ? 'Save' : 'Create link'}</Button>
      </div>
    </div>
  );
}

export default function RegistrationFormPage() {
  const { id } = useParams();
  const q = useQuery({ queryKey: ['partner', 'registration-forms', id], queryFn: () => regApi.getForm(id), enabled: Boolean(id) });
  return (
    <>
      <PageHeader title={id ? 'Edit registration link' : 'New registration link'} backTo={id ? `/partner/registrations/${id}` : '/partner/registrations'} />
      {id ? <QueryState query={q}>{(f) => <Editor initial={fromDto(f)} id={id} />}</QueryState> : <Editor initial={EMPTY} />}
    </>
  );
}
