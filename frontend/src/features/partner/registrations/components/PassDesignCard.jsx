import { useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { ImageIcon, RefreshCw, Upload } from 'lucide-react';
import * as regApi from '../../../../api/endpoints/registrations';
import { getErrorMessage } from '../../../../api/axios';
import { Button, Card, FileUpload, Input, RadioCards, Select, Switch, Textarea } from '../../../../components/ui';
import { templateInputs } from '../../../../components/whatsapp/TemplateBuilder/templateUtils';
import { HARMAIN_LAYOUT, boxValid, fromPct, toPct } from './regHelpers';
import { useApprovedTemplates } from './MessageBuilder';

const BOXES = [
  { key: 'name', label: 'Name box' },
  { key: 'mobile', label: 'Mobile box' },
  { key: 'photo', label: 'Photo box (optional)' },
];

/** Free pass settings: design (built-in or uploaded), where the text goes, preview and how it is sent. */
export default function PassDesignCard({ value, onChange, senderId }) {
  const set = (patch) => onChange({ ...value, ...patch });
  const custom = Boolean(value.passDesignFile);
  const [designMode, setDesignMode] = useState(custom ? 'custom' : 'harmain');
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [preview, setPreview] = useState(null);
  const [previewing, setPreviewing] = useState(false);
  const previewRef = useRef(null);
  const layout = value.passLayout || HARMAIN_LAYOUT;
  const pct = Object.fromEntries(BOXES.map((b) => [b.key, toPct(layout[b.key])]));
  const { list: templates } = useApprovedTemplates(senderId);
  const imageTemplates = templates.filter((t) => templateInputs(t).headerFormat === 'IMAGE');
  const passTemplate = imageTemplates.find((t) => String(t.id) === String(value.passTemplateId));
  const bodyVars = passTemplate ? templateInputs(passTemplate).bodyVars : [];

  const refresh = async () => {
    setPreviewing(true);
    try {
      const url = await regApi.previewPass({
        passDesign: custom ? null : 'harmain',
        passDesignFileUid: value.passDesignFile?.fileUid || null,
        passLayout: layout,
      });
      if (previewRef.current) URL.revokeObjectURL(previewRef.current);
      previewRef.current = url;
      setPreview(url);
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setPreviewing(false);
    }
  };

  useEffect(() => {
    if (value.passEnabled) refresh();
    return undefined;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value.passEnabled, value.passDesignFile?.fileUid]);
  useEffect(() => () => previewRef.current && URL.revokeObjectURL(previewRef.current), []);

  const upload = async (f) => {
    setFile(f);
    if (!f) return;
    setUploading(true);
    try {
      const res = await regApi.uploadDesign(f);
      set({ passDesignFile: res, passLayout: value.passLayout || HARMAIN_LAYOUT });
      toast.success('Design uploaded. Now mark where the name and mobile go.');
    } catch (e) {
      toast.error(getErrorMessage(e));
      setFile(null);
    } finally {
      setUploading(false);
    }
  };

  const setBox = (key, field, v) => {
    const next = { ...(pct[key] || { x: 10, y: 10, w: 40, h: 4 }), [field]: v };
    set({ passLayout: { ...layout, [key]: fromPct(next) } });
  };

  return (
    <Card title="Free pass" subtitle="A personal pass image is created for every registration and sent on WhatsApp.">
      <div className="space-y-5">
        <Switch checked={value.passEnabled} onChange={(v) => set({ passEnabled: v })} label="Create and send a free pass" />
        {value.passEnabled && (
          <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_280px]">
            <div className="space-y-4">
              <RadioCards
                name="pass-design"
                columns={2}
                value={designMode}
                onChange={(v) => {
                  setDesignMode(v);
                  if (v === 'harmain') {
                    setFile(null);
                    set({ passDesignFile: null, passLayout: HARMAIN_LAYOUT });
                  }
                }}
                options={[
                  { value: 'harmain', label: 'Harmain Ziyarat Pass', description: 'Ready-made design', icon: ImageIcon },
                  { value: 'custom', label: 'My own design', description: 'Upload a JPG/PNG (up to 5 MB)', icon: Upload },
                ]}
              />
              {designMode === 'custom' && (
                <FileUpload value={file} onChange={upload} accept="image/jpeg,image/png" maxSizeMB={5} disabled={uploading}
                            hint={uploading ? 'Uploading…' : custom ? 'Design uploaded. Choose another file to replace it.' : 'Leave the name and mobile boxes empty on your design.'} />
              )}
              <details className="rounded-lg border border-line p-3" open={designMode === 'custom'}>
                <summary className="cursor-pointer text-sm font-medium">Where the details are printed (in % of the image)</summary>
                <div className="mt-3 space-y-3">
                  {BOXES.map((b) => (
                    <div key={b.key}>
                      <p className="mb-1 text-xs font-medium text-muted">{b.label}</p>
                      <div className="grid grid-cols-4 gap-2">
                        {['x', 'y', 'w', 'h'].map((k) => (
                          <Input key={k} type="number" step="0.1" min={0} max={100} aria-label={`${b.label} ${k}`} placeholder={k}
                                 value={pct[b.key]?.[k] ?? ''} onChange={(e) => setBox(b.key, k, e.target.value)}
                                 error={pct[b.key] && !boxValid(pct[b.key]) && k === 'x' ? 'Outside image' : undefined} />
                        ))}
                      </div>
                    </div>
                  ))}
                  <Input label="Text colour" type="color" value={layout.textColor || '#0B5A2E'} className="w-32"
                         onChange={(e) => set({ passLayout: { ...layout, textColor: e.target.value } })} />
                </div>
              </details>
              <Input label="Pass number prefix" maxLength={10} value={value.passPrefix}
                     onChange={(e) => set({ passPrefix: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '') })}
                     hint={`Passes are numbered ${value.passPrefix || 'PASS'}-000001, ${value.passPrefix || 'PASS'}-000002…`} />
              <Select
                label="Send the pass with this template"
                value={value.passTemplateId || ''}
                onChange={(e) => set({ passTemplateId: e.target.value ? Number(e.target.value) : null, passTemplateBody: [] })}
                options={imageTemplates.map((t) => ({ value: t.id, label: `${t.name} (${t.language})` }))}
                placeholder="No template — customer taps “Get my pass on WhatsApp”"
                hint="An approved template with an IMAGE header sends the pass instantly after registration. Without it, the pass arrives when the customer messages you (free)."
              />
              {passTemplate && bodyVars.map((v, i) => (
                <Input key={v} label={`Template variable {{${v}}}`} value={value.passTemplateBody?.[i] ?? ''}
                       placeholder={i === 0 ? '{name}' : '{passNo}'}
                       onChange={(e) => {
                         const arr = [...(value.passTemplateBody || [])];
                         arr[i] = e.target.value;
                         set({ passTemplateBody: arr });
                       }} />
              ))}
              <Textarea label="Message with the pass (when sent as a photo)" rows={2} maxLength={1000} value={value.passCaption || ''}
                        placeholder="Assalamu Alaikum {name}, here is your pass {passNo}. Please show it at the entry."
                        onChange={(e) => set({ passCaption: e.target.value })} />
            </div>
            <div>
              <div className="mb-2 flex items-center justify-between">
                <p className="text-sm font-medium text-muted">Preview</p>
                <Button size="sm" variant="secondary" leftIcon={RefreshCw} loading={previewing} onClick={refresh}>Refresh</Button>
              </div>
              {preview ? (
                <img src={preview} alt="Sample pass" className="w-full rounded-lg border border-line" />
              ) : (
                <div className="flex aspect-[4/5] items-center justify-center rounded-lg border border-dashed border-line text-sm text-muted">Preview</div>
              )}
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}
