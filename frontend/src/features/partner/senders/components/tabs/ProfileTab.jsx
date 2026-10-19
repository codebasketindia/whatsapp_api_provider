import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Globe, Mail, MapPin, Plus, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { getErrorMessage } from '../../../../../api/axios';
import { Avatar, Button, Card, ErrorState, FileUpload, IconButton, Input, PageLoader, Select, Textarea } from '../../../../../components/ui';

export const VERTICALS = [
  ['OTHER', 'Other'], ['AUTO', 'Automotive'], ['BEAUTY', 'Beauty, spa and salon'], ['APPAREL', 'Clothing and apparel'],
  ['EDU', 'Education'], ['ENTERTAIN', 'Entertainment'], ['EVENT_PLAN', 'Event planning and service'], ['FINANCE', 'Finance and banking'],
  ['GROCERY', 'Food and grocery'], ['GOVT', 'Public service'], ['HOTEL', 'Hotel and lodging'], ['HEALTH', 'Medical and health'],
  ['NONPROFIT', 'Non-profit'], ['PROF_SERVICES', 'Professional services'], ['RETAIL', 'Shopping and retail'],
  ['TRAVEL', 'Travel and transportation'], ['RESTAURANT', 'Restaurant'], ['NOT_A_BIZ', 'Not a business'],
].map(([value, label]) => ({ value, label }));

/** Business profile editor with WhatsApp-style preview (T7–T9). */
export default function ProfileTab({ tools, sender, readOnly }) {
  const qc = useQueryClient();
  const key = [tools.scope, 'senders', sender.id, 'profile'];
  const q = useQuery({ queryKey: key, queryFn: () => tools.getProfile(sender.id) });
  const [form, setForm] = useState(null);
  const [photo, setPhoto] = useState(null);
  useEffect(() => {
    if (q.data) {
      setForm({
        about: q.data.about || '',
        description: q.data.description || '',
        address: q.data.address || '',
        email: q.data.email || '',
        websites: q.data.websites?.length ? q.data.websites : [''],
        vertical: q.data.vertical || '',
      });
    }
  }, [q.data]);
  const save = useMutation({
    mutationFn: () => tools.updateProfile(sender.id, { ...form, websites: form.websites.map((w) => w.trim()).filter(Boolean) }),
    onSuccess: (data) => {
      qc.setQueryData(key, data);
      toast.success('Business profile updated in WhatsApp');
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const upload = useMutation({
    mutationFn: () => tools.uploadProfilePhoto(sender.id, photo),
    onSuccess: (data) => {
      qc.setQueryData(key, data);
      setPhoto(null);
      toast.success('Profile photo updated');
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  if (q.isLoading || (!form && !q.isError)) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const set = (patch) => setForm((f) => ({ ...f, ...patch }));
  const errors = {
    about: form.about.length > 139 ? 'At most 139 characters' : null,
    email: form.email && !/^\S+@\S+\.\S+$/.test(form.email) ? 'Enter a valid email' : null,
    websites: form.websites.some((w) => w && !/^https?:\/\/\S+$/.test(w)) ? 'Websites must start with http:// or https://' : null,
  };
  const invalid = Object.values(errors).some(Boolean);

  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
      <Card title="Business profile" subtitle="Customers see this when they open your business info in WhatsApp">
        <fieldset disabled={readOnly} className="grid gap-4 sm:grid-cols-2">
          <Input className="sm:col-span-2" label="About (short status)" maxLength={139} value={form.about} onChange={(e) => set({ about: e.target.value })} error={errors.about} hint={`${form.about.length}/139`} />
          <Textarea className="sm:col-span-2" label="Description" rows={3} maxLength={512} showCount value={form.description} onChange={(e) => set({ description: e.target.value })} />
          <Select label="Business category" value={form.vertical} onChange={(e) => set({ vertical: e.target.value })} options={VERTICALS} placeholder="Choose a category" />
          <Input label="Email" type="email" maxLength={128} value={form.email} onChange={(e) => set({ email: e.target.value })} error={errors.email} />
          <Input className="sm:col-span-2" label="Address" maxLength={256} value={form.address} onChange={(e) => set({ address: e.target.value })} />
          <div className="sm:col-span-2">
            <span className="label-base">Websites (up to 2)</span>
            <div className="space-y-2">
              {form.websites.map((w, i) => (
                <div key={i} className="flex gap-2">
                  <Input
                    aria-label={`Website ${i + 1}`}
                    placeholder="https://example.com"
                    value={w}
                    onChange={(e) => set({ websites: form.websites.map((x, j) => (j === i ? e.target.value : x)) })}
                  />
                  {form.websites.length > 1 && (
                    <IconButton icon={Trash2} label="Remove website" variant="danger" onClick={() => set({ websites: form.websites.filter((_, j) => j !== i) })} />
                  )}
                </div>
              ))}
            </div>
            {errors.websites && <p className="mt-1 text-xs text-red-600">{errors.websites}</p>}
            {form.websites.length < 2 && (
              <Button size="sm" variant="link" leftIcon={Plus} className="mt-1" onClick={() => set({ websites: [...form.websites, ''] })}>
                Add website
              </Button>
            )}
          </div>
        </fieldset>
        {!readOnly && (
          <div className="mt-5 flex justify-end">
            <Button onClick={() => save.mutate()} loading={save.isPending} disabled={invalid}>Save profile</Button>
          </div>
        )}
        {!readOnly && (
          <div className="mt-6 border-t border-line pt-5">
            <FileUpload label="Profile photo" accept="image/jpeg,image/png" maxSizeMB={5} value={photo} onChange={setPhoto} hint="Square JPG or PNG, at least 192×192 px, max 5 MB" />
            {photo && (
              <div className="mt-3 flex justify-end">
                <Button onClick={() => upload.mutate()} loading={upload.isPending}>Upload photo</Button>
              </div>
            )}
          </div>
        )}
      </Card>
      <div className="lg:sticky lg:top-4 lg:self-start">
        <p className="mb-2 text-sm font-medium text-muted">Preview</p>
        <div className="card overflow-hidden">
          <div className="flex flex-col items-center bg-surface-2 px-4 py-6 text-center">
            <Avatar name={sender.verifiedName} src={photo ? URL.createObjectURL(photo) : q.data?.profilePictureUrl} size="lg" className="h-24 w-24 text-3xl" />
            <p className="mt-3 text-lg font-semibold text-ink">{sender.verifiedName}</p>
            <p className="text-sm text-muted">{sender.displayPhoneNumber}</p>
            {form.about && <p className="mt-2 text-sm text-ink">{form.about}</p>}
          </div>
          <dl className="space-y-3 p-4 text-sm">
            {form.description && <p className="text-ink">{form.description}</p>}
            {form.vertical && <p className="text-muted">{VERTICALS.find((v) => v.value === form.vertical)?.label}</p>}
            {form.address && <p className="flex gap-2 text-ink"><MapPin className="h-4 w-4 shrink-0 text-muted" />{form.address}</p>}
            {form.email && <p className="flex gap-2 text-ink"><Mail className="h-4 w-4 shrink-0 text-muted" />{form.email}</p>}
            {form.websites.filter(Boolean).map((w) => (
              <p key={w} className="flex gap-2 break-all text-brand-700 dark:text-brand-300"><Globe className="h-4 w-4 shrink-0 text-muted" />{w}</p>
            ))}
          </dl>
        </div>
      </div>
    </div>
  );
}
