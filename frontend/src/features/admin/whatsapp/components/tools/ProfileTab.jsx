import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { BadgeCheck, Building, Globe, Mail, MapPin, Upload } from 'lucide-react';
import { Avatar, Button, Card, FileUpload, Input, QueryState, Select, Textarea } from '../../../../../components/ui';
import { useApiMutation } from '../../../shared/useApiMutation';

export const VERTICALS = [
  ['OTHER', 'Other'], ['AUTO', 'Automotive'], ['BEAUTY', 'Beauty, spa and salon'], ['APPAREL', 'Clothing and apparel'], ['EDU', 'Education'],
  ['ENTERTAIN', 'Entertainment'], ['EVENT_PLAN', 'Event planning and service'], ['FINANCE', 'Finance and banking'], ['GROCERY', 'Food and grocery'],
  ['GOVT', 'Public service'], ['HOTEL', 'Hotel and lodging'], ['HEALTH', 'Medical and health'], ['NONPROFIT', 'Non-profit'],
  ['PROF_SERVICES', 'Professional services'], ['RETAIL', 'Shopping and retail'], ['TRAVEL', 'Travel and transportation'],
  ['RESTAURANT', 'Restaurant'], ['NOT_A_BIZ', 'Not a business'],
].map(([value, label]) => ({ value, label }));

const url = z.string().trim().optional().refine((v) => !v || /^https?:\/\/\S+\.\S+/.test(v), 'Enter a full URL starting with https://');
const schema = z.object({
  about: z.string().trim().max(139, 'Max 139 characters').optional(),
  description: z.string().trim().max(512, 'Max 512 characters').optional(),
  address: z.string().trim().max(256, 'Max 256 characters').optional(),
  email: z.string().trim().max(128).optional().refine((v) => !v || /^\S+@\S+\.\S+$/.test(v), 'Enter a valid email'),
  website1: url,
  website2: url,
  vertical: z.string().optional(),
});

/** Business profile editor (T7–T9) with a WhatsApp-style profile preview. */
export default function ProfileTab({ sender, tools, canManage }) {
  const q = useQuery({ queryKey: [tools.scope, 'senders', sender.id, 'profile'], queryFn: () => tools.getProfile(sender.id) });
  const [photo, setPhoto] = useState(null);
  const { register, handleSubmit, reset, watch, formState: { errors, isDirty } } = useForm({ resolver: zodResolver(schema) });
  useEffect(() => {
    const p = q.data;
    if (p) reset({ about: p.about || '', description: p.description || '', address: p.address || '', email: p.email || '', website1: p.websites?.[0] || '', website2: p.websites?.[1] || '', vertical: p.vertical || '' });
  }, [q.data, reset]);
  const inv = [[tools.scope, 'senders', sender.id, 'profile']];
  const save = useApiMutation({
    mutationFn: (v) =>
      tools.updateProfile(sender.id, {
        about: v.about || null, description: v.description || null, address: v.address || null, email: v.email || null,
        websites: [v.website1, v.website2].filter(Boolean), vertical: v.vertical || null,
      }),
    success: 'Business profile updated on WhatsApp',
    invalidate: inv,
  });
  const upload = useApiMutation({
    mutationFn: () => tools.uploadProfilePhoto(sender.id, photo),
    success: 'Profile photo updated',
    invalidate: inv,
    onSuccess: () => setPhoto(null),
  });
  const v = watch();
  return (
    <QueryState query={q}>
      {(p) => (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-5">
          <Card title="Business profile" subtitle="What customers see when they open your business info in WhatsApp." className="lg:col-span-3">
            <form className="space-y-4" onSubmit={handleSubmit((x) => save.mutate(x))} noValidate>
              <Input label="About" maxLength={139} disabled={!canManage} hint="Short status line, max 139 characters." error={errors.about} {...register('about')} />
              <Textarea label="Description" rows={4} maxLength={512} showCount disabled={!canManage} error={errors.description} value={v.description} {...register('description')} />
              <Select label="Industry" placeholder="Choose industry" options={VERTICALS} disabled={!canManage} {...register('vertical')} />
              <Input label="Address" disabled={!canManage} error={errors.address} {...register('address')} />
              <Input label="Email" type="email" disabled={!canManage} error={errors.email} {...register('email')} />
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <Input label="Website 1" placeholder="https://" disabled={!canManage} error={errors.website1} {...register('website1')} />
                <Input label="Website 2" placeholder="https://" disabled={!canManage} error={errors.website2} {...register('website2')} />
              </div>
              {canManage && (
                <div className="flex justify-end">
                  <Button type="submit" disabled={!isDirty} loading={save.isPending}>Save profile</Button>
                </div>
              )}
            </form>
          </Card>
          <div className="space-y-4 lg:col-span-2">
            <Card title="Preview">
              <div className="overflow-hidden rounded-xl border border-line">
                <div className="flex flex-col items-center bg-surface-2 p-6 text-center">
                  {p.profilePictureUrl ? (
                    <img src={p.profilePictureUrl} alt="Profile" className="h-24 w-24 rounded-full object-cover" />
                  ) : (
                    <Avatar name={sender.verifiedName || sender.displayPhoneNumber} size="lg" />
                  )}
                  <p className="mt-3 flex items-center gap-1 text-lg font-semibold text-ink">
                    {sender.verifiedName || 'Business name'} <BadgeCheck className="h-4 w-4 text-brand-600" />
                  </p>
                  <p className="text-sm text-muted">{sender.displayPhoneNumber}</p>
                  {v.about && <p className="mt-2 text-sm text-ink">{v.about}</p>}
                </div>
                <div className="space-y-3 p-4 text-sm">
                  {v.description && <p className="whitespace-pre-line text-ink">{v.description}</p>}
                  {v.vertical && <PreviewRow icon={Building} text={VERTICALS.find((x) => x.value === v.vertical)?.label} />}
                  {v.address && <PreviewRow icon={MapPin} text={v.address} />}
                  {v.email && <PreviewRow icon={Mail} text={v.email} />}
                  {[v.website1, v.website2].filter(Boolean).map((w) => <PreviewRow key={w} icon={Globe} text={w} link />)}
                </div>
              </div>
            </Card>
            {canManage && (
              <Card title="Profile photo" subtitle="Square JPG or PNG, at least 192×192 px, up to 5 MB.">
                <FileUpload value={photo} onChange={setPhoto} accept="image/jpeg,image/png" maxSizeMB={5} />
                <Button className="mt-3 w-full" leftIcon={Upload} disabled={!photo} loading={upload.isPending} onClick={() => upload.mutate()}>Upload photo</Button>
              </Card>
            )}
          </div>
        </div>
      )}
    </QueryState>
  );
}

function PreviewRow({ icon: Icon, text, link }) {
  return (
    <p className="flex items-start gap-3">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-muted" />
      <span className={link ? 'break-all text-brand-700 dark:text-brand-300' : 'text-ink'}>{text}</span>
    </p>
  );
}
