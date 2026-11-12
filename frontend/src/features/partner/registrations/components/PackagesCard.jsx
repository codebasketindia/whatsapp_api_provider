import { useState } from 'react';
import toast from 'react-hot-toast';
import { ArrowDown, ArrowUp, ImagePlus, Plus, Trash2, X } from 'lucide-react';
import * as regApi from '../../../../api/endpoints/registrations';
import { getErrorMessage } from '../../../../api/axios';
import { Badge, Button, Card, IconButton, Input, Select, Switch, Textarea } from '../../../../components/ui';
import { useProtectedUrl } from '../../inbox/components/ChatBubble';
import PackageIcon, { PACKAGE_ICONS } from '../../../public/register/PackageIcon';
import { discountPercent, rupees } from '../../../public/register/bookingHelpers';

export const EMPTY_PACKAGE = { id: null, code: '', name: '', description: '', details: '', icon: 'sparkles', image: null, mrp: '', salePrice: '', mandatory: false, active: true, passTitle: '', imageUrl: '', issuePass: true, passStyle: 'TICKET', itinerary: [] };

export const packageToForm = (p) => ({ ...EMPTY_PACKAGE, ...p, description: p.description || '', details: p.details || '', icon: p.icon || 'sparkles', passTitle: p.passTitle || '', imageUrl: p.imageUrl || '', issuePass: p.issuePass !== false, passStyle: p.passStyle || 'TICKET', itinerary: (p.itinerary || []).map((x) => ({ round: x.round || '', place: x.place || '', time: x.time || '', note: x.note || '' })), mrp: p.mrp ?? '', salePrice: p.salePrice ?? '' });

export const formToPackage = (p, i) => ({
  id: p.id || null, code: p.code.trim().toUpperCase(), name: p.name.trim(), description: p.description?.trim() || null,
  details: p.details?.trim() || null, icon: p.icon || null, imageFileUid: p.image?.fileUid || null,
  mrp: Number(p.mrp || 0), salePrice: Number(p.salePrice || 0), mandatory: Boolean(p.mandatory), active: p.active !== false,
  sortOrder: i + 1, passTitle: p.passTitle?.trim() || null,
  imageUrl: p.imageUrl?.trim() || null, issuePass: p.issuePass !== false, passStyle: p.passStyle || 'TICKET',
  itinerary: (p.itinerary || []).filter((x) => x.place?.trim()).map((x) => ({
    round: x.round?.trim() || null, place: x.place.trim(), time: x.time?.trim() || null, note: x.note?.trim() || null })),
});

/** Rounds / days with places for "Full Details". A row without a round keeps the round above it. */
function ItineraryEditor({ value, onChange }) {
  const list = value || [];
  const set = (i, patch) => onChange(list.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  const move = (i, d) => {
    const next = [...list];
    [next[i], next[i + d]] = [next[i + d], next[i]];
    onChange(next);
  };
  return (
    <div className="mt-4 rounded-lg border border-line p-3">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold">Itinerary <span className="font-normal text-muted">({list.length})</span></p>
        <Button size="sm" variant="secondary" leftIcon={Plus} disabled={list.length >= 60}
                onClick={() => onChange([...list, { round: list.length ? '' : 'Round 1', place: '', time: '', note: '' }])}>Add stop</Button>
      </div>
      {list.length === 0 ? (
        <p className="text-xs text-muted">No itinerary yet. Add the rounds / days and the places visited — shown under “Full Details”.</p>
      ) : (
        <ul className="space-y-2">
          {list.map((x, i) => (
            <li key={i} className="grid grid-cols-1 gap-2 rounded-lg bg-surface-2 p-2 md:grid-cols-[140px_1fr_110px_1fr_auto]">
              <Input label="Round / Day" maxLength={80} value={x.round} placeholder={i ? 'same as above' : 'Round 1'} onChange={(e) => set(i, { round: e.target.value })} />
              <Input label="Place" required maxLength={160} value={x.place} placeholder="Masjid Quba" onChange={(e) => set(i, { place: e.target.value })} />
              <Input label="Time" maxLength={40} value={x.time} placeholder="8:00 AM" onChange={(e) => set(i, { time: e.target.value })} />
              <Input label="Note" maxLength={500} value={x.note} placeholder="2 rakat nafl" onChange={(e) => set(i, { note: e.target.value })} />
              <div className="flex items-end gap-1">
                <IconButton icon={ArrowUp} label="Move up" disabled={i === 0} onClick={() => move(i, -1)} />
                <IconButton icon={ArrowDown} label="Move down" disabled={i === list.length - 1} onClick={() => move(i, 1)} />
                <IconButton icon={Trash2} label="Remove stop" onClick={() => onChange(list.filter((_, j) => j !== i))} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

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
        <p className="min-w-0 flex-1 break-words font-semibold">{p.name || `Package ${index + 1}`}</p>
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
        <Input className="md:col-span-2" label="Card image URL (optional)" maxLength={500} value={p.imageUrl} placeholder="/ziyarat/makkah.jpg or https://…" error={err('imageUrl')}
               hint="Used when no image is uploaded." onChange={(e) => set({ imageUrl: e.target.value })} />
        <Select label="Pass style" value={p.passStyle} onChange={(e) => set({ passStyle: e.target.value })}
                options={[{ value: 'TICKET', label: 'Ticket pass' }, { value: 'FORM_DESIGN', label: 'Link pass design' }, { value: 'ZIYARAT_CARD', label: 'Ziyarat card (green & gold)' }, { value: 'HFHU_1', label: 'HFHU PASS 1 (Show pass)' }, { value: 'HFHU_2', label: 'HFHU PASS 2 (Khaas)' }, { value: 'HFHU_3', label: 'HFHU PASS 3 (Mazeed)' }, { value: 'HFHU_4', label: 'HFHU PASS 4 (Taif-Jeddah-Badr)' }]} />
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
        <Switch checked={p.issuePass} onChange={(v) => set({ issuePass: v })} label="Issue a QR pass" />
      </div>
      {!p.mandatory && <ItineraryEditor value={p.itinerary} onChange={(itinerary) => set({ itinerary })} />}
    </li>
  );
}

/** Packages of a booking link, plus its Terms & Privacy texts. All stored in the database. */
export default function PackagesCard({ value, onChange, termsText, privacyText, refundText, onLegal, errors = {} }) {
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
        <Textarea label="Refund Policy" rows={6} maxLength={50000} value={refundText} onChange={(e) => onLegal({ refundText: e.target.value })} />
      </div>
    </Card>
  );
}
