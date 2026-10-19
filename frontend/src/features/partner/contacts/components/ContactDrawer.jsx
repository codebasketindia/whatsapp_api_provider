import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import * as contactsApi from '../../../../api/endpoints/contacts';
import { getErrorMessage, getFieldErrors } from '../../../../api/axios';
import { Button, Checkbox, Drawer, IconButton, Input, PhoneInput, isE164Digits } from '../../../../components/ui';
import TagInput from '../../common/TagInput';

const ATTR_KEY = /^[a-z0-9_]{1,40}$/;

/** Add / edit contact. contact=null creates a new one. */
export default function ContactDrawer({ open, onClose, contact }) {
  const qc = useQueryClient();
  const tags = useQuery({ queryKey: ['partner', 'contacts', 'tags'], queryFn: contactsApi.listContactTags, enabled: open });
  const groups = useQuery({ queryKey: ['partner', 'contact-groups', 'all'], queryFn: () => contactsApi.listContactGroups({ size: 100 }), enabled: open });
  const [form, setForm] = useState(null);
  const [errors, setErrors] = useState({});
  useEffect(() => {
    if (!open) return;
    setErrors({});
    setForm({
      phone: contact?.phone || '',
      name: contact?.name || '',
      email: contact?.email || '',
      tags: contact?.tags || [],
      attributes: Object.entries(contact?.attributes || {}).map(([key, value]) => ({ key, value })),
      groupIds: (contact?.groups || []).map((g) => g.id),
    });
  }, [open, contact]);
  const m = useMutation({
    mutationFn: (body) => (contact ? contactsApi.updateContact(contact.id, body) : contactsApi.createContact(body)),
    onSuccess: () => {
      toast.success(contact ? 'Contact updated' : 'Contact added');
      qc.invalidateQueries({ queryKey: ['partner', 'contacts'] });
      qc.invalidateQueries({ queryKey: ['partner', 'contact-groups'] });
      onClose();
    },
    onError: (e) => {
      setErrors(getFieldErrors(e));
      toast.error(getErrorMessage(e));
    },
  });
  if (!form) return null;
  const set = (patch) => setForm((f) => ({ ...f, ...patch }));
  const submit = () => {
    const errs = {};
    if (!isE164Digits(form.phone)) errs.phone = 'Enter a valid WhatsApp number';
    if (form.email && !/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'Enter a valid email';
    if (form.attributes.some((a) => a.key && !ATTR_KEY.test(a.key))) errs.attributes = 'Field names: lowercase letters, numbers and _ only';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    m.mutate({
      phone: form.phone,
      name: form.name || undefined,
      email: form.email || undefined,
      tags: form.tags,
      attributes: Object.fromEntries(form.attributes.filter((a) => a.key).map((a) => [a.key, a.value])),
      groupIds: form.groupIds,
    });
  };
  const groupList = groups.data?.content || [];
  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={contact ? 'Edit contact' : 'Add contact'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={submit} loading={m.isPending}>Save</Button>
        </>
      }
    >
      <div className="space-y-4">
        <PhoneInput label="WhatsApp number" value={form.phone} onChange={(v) => set({ phone: v })} error={errors.phone} required disabled={Boolean(contact)} />
        <Input label="Name" maxLength={120} value={form.name} onChange={(e) => set({ name: e.target.value })} error={errors.name} />
        <Input label="Email" type="email" maxLength={190} value={form.email} onChange={(e) => set({ email: e.target.value })} error={errors.email} />
        <TagInput
          label="Tags"
          value={form.tags}
          onChange={(v) => set({ tags: v })}
          suggestions={(tags.data || []).map((t) => t.tag)}
          transform={(s) => s.slice(0, 40)}
          hint="Example: vip, mumbai, diwali-2026"
        />
        {groupList.length > 0 && (
          <div>
            <span className="label-base">Groups</span>
            <div className="space-y-1">
              {groupList.map((g) => (
                <Checkbox
                  key={g.id}
                  label={g.name}
                  checked={form.groupIds.includes(g.id)}
                  onChange={(e) => set({ groupIds: e.target.checked ? [...form.groupIds, g.id] : form.groupIds.filter((x) => x !== g.id) })}
                />
              ))}
            </div>
          </div>
        )}
        <div>
          <span className="label-base">Custom fields</span>
          <p className="mb-2 text-xs text-muted">Use these to fill template variables in campaigns (e.g. city, order_id).</p>
          <div className="space-y-2">
            {form.attributes.map((a, i) => (
              <div key={i} className="flex gap-2">
                <Input aria-label="Field name" placeholder="field_name" value={a.key} onChange={(e) => set({ attributes: form.attributes.map((x, j) => (j === i ? { ...x, key: e.target.value.toLowerCase() } : x)) })} />
                <Input aria-label="Value" placeholder="Value" value={a.value} onChange={(e) => set({ attributes: form.attributes.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)) })} />
                <IconButton icon={Trash2} label="Remove field" variant="danger" onClick={() => set({ attributes: form.attributes.filter((_, j) => j !== i) })} />
              </div>
            ))}
          </div>
          {errors.attributes && <p className="mt-1 text-xs text-red-600">{errors.attributes}</p>}
          <Button size="sm" variant="link" leftIcon={Plus} className="mt-1" onClick={() => set({ attributes: [...form.attributes, { key: '', value: '' }] })}>Add field</Button>
        </div>
      </div>
    </Drawer>
  );
}
