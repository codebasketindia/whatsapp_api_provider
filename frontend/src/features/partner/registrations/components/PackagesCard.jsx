import { useState } from 'react';
import toast from 'react-hot-toast';
import { ArrowDown, ArrowUp, ImagePlus, Plus, Trash2, X } from 'lucide-react';
import * as regApi from '../../../../api/endpoints/registrations';
import { getErrorMessage } from '../../../../api/axios';
import { Badge, Button, Card, IconButton, Input, Select, Switch, Textarea } from '../../../../components/ui';
import { useProtectedUrl } from '../../inbox/components/ChatBubble';
import PackageIcon, { PACKAGE_ICONS } from '../../../public/register/PackageIcon';
import { discountPercent, rupees } from '../../../public/register/bookingHelpers';

export const EMPTY_PACKAGE = { id: null, code: '', name: '', description: '', details: '', icon: 'sparkles', image: null, mrp: '', salePrice: '', mandatory: false, active: true, passTitle: '' };

export const packageToForm = (p) => ({ ...EMPTY_PACKAGE, ...p, description: p.description || '', details: p.details || '', icon: p.icon || 'sparkles', passTitle: p.passTitle || '', mrp: p.mrp ?? '', salePrice: p.salePrice ?? '' });

export const formToPackage = (p, i) => ({
  id: p.id || null, code: p.code.trim().toUpperCase(), name: p.name.trim(), description: p.description?.trim() || null,
  details: p.details?.trim() || null, icon: p.icon || null, imageFileUid: p.image?.fileUid || null,
  mrp: Number(p.mrp || 0), salePrice: Number(p.salePrice || 0), mandatory: Boolean(p.mandatory), active: p.active !== false,
  sortOrder: i + 1, passTitle: p.passTitle?.trim() || null,
});

function PackageEditor({ p, index, count, onChange, onRemove, onMove, errors }) {
  const [uploading, setUploading] = useState(false);
  const imageSrc = useProtectedUrl(p.image?.url);
  const set = (patch) => onChange({ ...p, ...patch });
  const err = (k) => errors[`packages[${index}].${k}`];
  const upload = async (file) => {
    if (!file) return;
    setUploading(true);
    try {
      set({ image: await regApi.uploadDesign(file) });
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setUploading(false);
    }
  };
  const off = discountPercent(p.mrp, p.salePrice);
  return (
    <li className="rounded-xl border border-line p-4">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-surface-2"><PackageIcon name={p.icon} className="h-5 w-5" /></span>
        <p className="min-w-0 flex-1 truncate font-semibold">{p.name || `Package ${index + 1}`}</p>
        {p.mandatory && <Badge tone="blue">Mandatory</Badge>}
        {!p.active && <Badge tone="gray">Hidden</Badge>}
        {off > 0 && <Badge tone="yellow">{off}% off</Badge>}
        <IconButton icon={ArrowUp} label="Move up" disabled={index === 0} onClick={() => onMove(-1)} />
        <IconButton icon={ArrowDown} label="Move down" disabled={index === count - 1} onClick={() => onMove(1)} />
        <IconButton icon={Trash2} label="Remove package" onClick={onRemove} />
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
        <Input className="md:col-span-2" label="Name" required maxLength={120} value={p.name} error={err('name')} onChange={(e) => set({ name: e.target.value })} />
        <Input label="Code" required maxLength={12} value={p.code} error={err('code')} hint="e.g. REG, SMM" onChange={(e) => set({ code: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '') })} />
        <Select label="Icon" value={p.icon} onChange={(e) => set({ icon: e.target.value })} options={Object.keys(PACKAGE_ICONS).map((k) => ({ value: k, label: k }))} />
        <Input type="number" min={0} step="0.01" label="MRP (₹)" required value={p.mrp} error={err('mrp')} onChange={(e) => set({ mrp: e.target.value })} />
        <Input type="number" min={0} step="0.01" label="Sale price (₹)" required value={p.salePrice} error={err('salePrice')} onChange={(e) => set({ salePrice: e.target.value })}
               hint={p.mrp && p.salePrice ? `Shown as ${rupees(p.mrp)} → ${rupees(p.salePrice)}` : undefined} />
        <Input className="md:col-span-2" label="Pass title" maxLength={160} value={p.passTitle} placeholder={`${p.name || 'Package'} Pass`} onChange={(e) => set({ passTitle: e.target.value })} />
        <Input className="md:col-span-4" label="Short description" maxLength={1000} value={p.description} onChange={(e) => set({ description: e.target.value })} />
        <Textarea className="md:col-span-4" label="Full details (shown under “View Full Details”)" rows={3} maxLength={8000} value={p.details} onChange={(e) => set({ details: e.target.value })} />
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-4">
        {p.image ? (
          <span className="relative inline-block">
            <img src={imageSrc || undefined} alt="" className="h-16 w-24 rounded-lg bg-surface-2 object-cover" />
            <IconButton icon={X} size="sm" variant="secondary" label="Remove image" className="absolute -right-2 -top-2" onClick={() => set({ image: null })} />
          </span>
        ) : (
          <label className="inline-flex min-h-tap cursor-pointer items-center gap-2 rounded-lg border border-dashed border-line px-3 text-sm">
            <ImagePlus className="h-4 w-4" aria-hidden /> {uploading ? 'Uploading…' : 'Card image (optional)'}
            <input type="file" accept="image/jpeg,image/png" className="hidden" onChange={(e) => upload(e.target.files?.[0])} />
          </label>
        )}
        <Switch checked={p.mandatory} onChange={(v) => set({ mandatory: v })} label="Mandatory (always included)" />
        <Switch checked={p.active} onChange={(v) => set({ active: v })} label="Show on the page" />
      </div>
    </li>
  );
}

/** Packages of a booking link, plus its Terms & Privacy texts. All stored in the database. */
export default function PackagesCard({ value, onChange, termsText, privacyText, onLegal, errors = {} }) {
  const list = value || [];
  const update = (i, p) => onChange(list.map((x, j) => (j === i ? p : x)));
  const move = (i, d) => {
    const next = [...list];
    [next[i], next[i + d]] = [next[i + d], next[i]];
    onChange(next);
  };
  return (
    <Card title="Booking packages" subtitle="Each package gets its own QR pass after payment. Leave empty for a simple registration."
          actions={<Button size="sm" leftIcon={Plus} disabled={list.length >= 12} onClick={() => onChange([...list, { ...EMPTY_PACKAGE }])}>Add package</Button>}>
      {errors.packages && <p className="mb-3 text-sm text-red-600">{errors.packages}</p>}
      {list.length === 0 ? (
        <p className="text-sm text-muted">No packages. People register with the fee below (or free).</p>
      ) : (
        <ul className="space-y-3">
          {list.map((p, i) => (
            <PackageEditor key={p.id ?? `new-${i}`} p={p} index={i} count={list.length} errors={errors}
                           onChange={(np) => update(i, np)} onRemove={() => onChange(list.filter((_, j) => j !== i))} onMove={(d) => move(i, d)} />
          ))}
        </ul>
      )}
      <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
        <Textarea label="Terms & Conditions" rows={6} maxLength={50000} value={termsText} onChange={(e) => onLegal({ termsText: e.target.value })}
                  hint="Opens on its own page from the booking form." />
        <Textarea label="Privacy Policy" rows={6} maxLength={50000} value={privacyText} onChange={(e) => onLegal({ privacyText: e.target.value })} />
      </div>
    </Card>
  );
}
