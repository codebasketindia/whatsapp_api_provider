import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plane, Pencil, Plus, Trash2, X } from 'lucide-react';
import toast from 'react-hot-toast';
import * as travelApi from '../../../../api/endpoints/travel';
import * as aiApi from '../../../../api/endpoints/ai';
import { getErrorMessage, getFieldErrors } from '../../../../api/axios';
import { Badge, Button, Card, ConfirmDialog, Drawer, EmptyState, FileUpload, IconButton, Input, QueryState, Select, Textarea } from '../../../../components/ui';
import { formatMoney } from '../../../../lib/format';
import KnowledgeMedia from '../../ai/components/KnowledgeMedia';
import { PACKAGE_STATUS, formToPackage, packageToForm, seatsText } from './travelHelpers';

function PackageDrawer({ open, item, onClose }) {
  const qc = useQueryClient();
  const [v, setV] = useState(packageToForm());
  const [media, setMedia] = useState(null);
  const [file, setFile] = useState(null);
  const [errors, setErrors] = useState({});
  useEffect(() => {
    if (!open) return;
    setV(packageToForm(item || {}));
    setMedia(item?.media ? { ...item.media, type: item.media.mimeType?.startsWith('image/') ? 'image' : item.media.mimeType?.startsWith('video/') ? 'video' : 'document' } : null);
    setFile(null);
    setErrors({});
  }, [open, item]);
  const set = (k) => (e) => setV((s) => ({ ...s, [k]: e.target.value }));
  const upload = async (f) => {
    setFile(f);
    if (!f) return;
    try {
      setMedia(await aiApi.uploadKnowledgeMedia(f));
    } catch (e) {
      toast.error(getErrorMessage(e));
      setFile(null);
    }
  };
  const save = useMutation({
    mutationFn: () => (item ? travelApi.updatePackage(item.id, formToPackage(v, media)) : travelApi.createPackage(formToPackage(v, media))),
    onSuccess: () => {
      toast.success(item ? 'Package saved' : 'Package added');
      qc.invalidateQueries({ queryKey: ['partner', 'travel'] });
      onClose();
    },
    onError: (e) => {
      setErrors(getFieldErrors(e));
      toast.error(getErrorMessage(e));
    },
  });
  return (
    <Drawer open={open} onClose={onClose} width="md:w-[560px]" title={item ? 'Edit package' : 'Add package'}
            description="The AI quotes exactly these details — prices, dates and seats — and nothing else."
            footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={save.isPending} disabled={!v.code.trim() || !v.name.trim()} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="Code" required maxLength={40} value={v.code} onChange={set('code')} error={errors.code} placeholder="UMRAH-15D" />
        <Select label="Status" value={v.status} onChange={set('status')} options={PACKAGE_STATUS} />
        <Input className="sm:col-span-2" label="Package name" required maxLength={160} value={v.name} onChange={set('name')} error={errors.name} placeholder="Umrah Deluxe 15 days" />
        <Input label="Destination" maxLength={160} value={v.destination} onChange={set('destination')} placeholder="Makkah & Madinah" />
        <Input label="Duration" maxLength={60} value={v.duration} onChange={set('duration')} placeholder="15 days / 14 nights" />
        <Input type="number" min={0} label="Price (₹)" value={v.price} onChange={set('price')} error={errors.price} />
        <Input label="Price note" maxLength={255} value={v.priceNote} onChange={set('priceNote')} placeholder="per person, quad sharing" />
        <Input type="date" label="Start date" value={v.startDate} onChange={set('startDate')} />
        <Input type="date" label="End date" value={v.endDate} onChange={set('endDate')} error={errors.endDate} />
        <Input type="number" min={0} label="Total seats" value={v.seatsTotal} onChange={set('seatsTotal')} hint="Empty = no limit. Confirmed bookings reduce the seats left." />
        <Input type="number" min={0} label="Order" value={v.sortOrder} onChange={set('sortOrder')} />
        <Textarea className="sm:col-span-2" label="Included" rows={3} maxLength={4000} value={v.inclusions} onChange={set('inclusions')} placeholder="Visa, return flights, 4-star hotel, meals, Ziyarat tour" />
        <Textarea className="sm:col-span-2" label="Not included" rows={2} maxLength={4000} value={v.exclusions} onChange={set('exclusions')} />
        <Textarea className="sm:col-span-2" label="Day-wise plan" rows={4} maxLength={8000} value={v.itinerary} onChange={set('itinerary')} />
        <Textarea className="sm:col-span-2" label="Other details (documents, payment terms…)" rows={3} maxLength={8000} value={v.details} onChange={set('details')} />
        <div className="sm:col-span-2">
          <span className="label-base">Brochure photo, video or PDF (optional)</span>
          {media ? (
            <div className="relative inline-block max-w-full rounded-lg border border-line p-2">
              <KnowledgeMedia media={media} />
              <IconButton icon={X} label="Remove file" size="sm" variant="secondary" className="absolute right-1 top-1" onClick={() => { setMedia(null); setFile(null); }} />
            </div>
          ) : (
            <FileUpload value={file} onChange={upload} accept="image/jpeg,image/png,video/mp4,application/pdf" maxSizeMB={16} hint="The AI can send it when a customer asks about this package." />
          )}
        </div>
      </div>
    </Drawer>
  );
}

export default function PackagesTab() {
  const qc = useQueryClient();
  const [drawer, setDrawer] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const q = useQuery({ queryKey: ['partner', 'travel', 'packages'], queryFn: () => travelApi.listPackages() });
  const del = useMutation({
    mutationFn: (p) => travelApi.deletePackage(p.id),
    onSuccess: () => {
      toast.success('Package deleted');
      setDeleting(null);
      qc.invalidateQueries({ queryKey: ['partner', 'travel'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  return (
    <>
      <div className="mb-4 flex justify-end"><Button leftIcon={Plus} onClick={() => setDrawer({})}>Add package</Button></div>
      <QueryState query={q}>
        {(list) =>
          !list.length ? (
            <Card><EmptyState icon={Plane} title="No packages yet" text="Add your tours and packages. The AI answers customers only from these details." action={<Button leftIcon={Plus} onClick={() => setDrawer({})}>Add package</Button>} /></Card>
          ) : (
            <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {list.map((p) => (
                <li key={p.id} className="card flex flex-col gap-2 p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-semibold text-ink">{p.name}</p>
                      <p className="text-xs text-muted">{p.code}{p.duration ? ` · ${p.duration}` : ''}{p.destination ? ` · ${p.destination}` : ''}</p>
                    </div>
                    <Badge tone={p.status === 'ACTIVE' ? 'green' : p.status === 'SOLD_OUT' ? 'red' : 'gray'} dot>{PACKAGE_STATUS.find((s) => s.value === p.status)?.label.split(' (')[0]}</Badge>
                  </div>
                  <p className="text-lg font-bold text-ink">{p.price != null ? formatMoney(p.price) : 'Price on request'} <span className="text-xs font-normal text-muted">{p.priceNote}</span></p>
                  <p className="text-sm text-muted">{seatsText(p)}{p.startDate ? ` · ${p.startDate}${p.endDate ? ` → ${p.endDate}` : ''}` : ''}</p>
                  <div className="mt-auto flex justify-end">
                    <IconButton icon={Pencil} label={`Edit ${p.name}`} onClick={() => setDrawer({ item: p })} />
                    <IconButton icon={Trash2} label={`Delete ${p.name}`} onClick={() => setDeleting(p)} />
                  </div>
                </li>
              ))}
            </ul>
          )
        }
      </QueryState>
      <PackageDrawer open={Boolean(drawer)} item={drawer?.item} onClose={() => setDrawer(null)} />
      <ConfirmDialog open={Boolean(deleting)} onClose={() => setDeleting(null)} onConfirm={() => del.mutate(deleting)} loading={del.isPending} danger
                     title="Delete this package?" message={<>The AI will stop offering <strong>{deleting?.name}</strong>.</>} confirmLabel="Delete" />
    </>
  );
}
