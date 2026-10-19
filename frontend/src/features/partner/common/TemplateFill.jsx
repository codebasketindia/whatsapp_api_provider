import { useState } from 'react';
import toast from 'react-hot-toast';
import * as messagesApi from '../../../api/endpoints/messages';
import { getErrorMessage } from '../../../api/axios';
import { FileUpload, Input } from '../../../components/ui';
import { templateInputs } from '../../../components/whatsapp/TemplateBuilder';

const MEDIA_ACCEPT = { IMAGE: 'image/jpeg,image/png', VIDEO: 'video/mp4,video/3gpp', DOCUMENT: 'application/pdf' };
const MEDIA_MAX = { IMAGE: 5, VIDEO: 16, DOCUMENT: 16 };

/**
 * Inputs for one template send: body variables, header text/media/location and button values.
 * values shape: { body: {var: value}, header: {...}, buttons: {index: value} } (see buildTemplatePayload).
 */
export default function TemplateFill({ template, senderId, values, onChange }) {
  const inputs = templateInputs(template);
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const setBody = (k, v) => onChange({ ...values, body: { ...(values.body || {}), [k]: v } });
  const setHeader = (patch) => onChange({ ...values, header: { ...(values.header || {}), ...patch } });
  const setButton = (i, v) => onChange({ ...values, buttons: { ...(values.buttons || {}), [i]: v } });

  const upload = async (f) => {
    setFile(f);
    if (!f) {
      setHeader({ fileUid: undefined, filename: undefined });
      return;
    }
    if (!senderId) {
      toast.error('Choose the number to send from first');
      setFile(null);
      return;
    }
    setUploading(true);
    try {
      const res = await messagesApi.uploadMedia(senderId, f);
      setHeader({ fileUid: res.fileUid, filename: f.name, link: undefined });
    } catch (e) {
      toast.error(getErrorMessage(e));
      setFile(null);
    } finally {
      setUploading(false);
    }
  };

  const nothing = !inputs.bodyVars.length && !inputs.headerVars.length && !inputs.needsHeaderMedia && !inputs.needsLocation && !inputs.buttonInputs.length;
  if (nothing) return <p className="text-sm text-muted">This template has no variables. It is ready to send.</p>;

  return (
    <div className="space-y-4">
      {inputs.headerVars.length > 0 && (
        <Input label={`Header {{${inputs.headerVars[0]}}}`} value={values.header?.text || ''} onChange={(e) => setHeader({ text: e.target.value })} required />
      )}
      {inputs.needsHeaderMedia && (
        <div>
          <FileUpload
            label={`Header ${inputs.headerFormat.toLowerCase()}`}
            accept={MEDIA_ACCEPT[inputs.headerFormat]}
            maxSizeMB={MEDIA_MAX[inputs.headerFormat]}
            value={file}
            onChange={upload}
            disabled={uploading}
          />
          {uploading && <p className="mt-1 text-xs text-muted">Uploading…</p>}
          {!file && (
            <Input
              className="mt-2"
              label="…or a public link (https)"
              placeholder="https://example.com/file.jpg"
              value={values.header?.link || ''}
              onChange={(e) => setHeader({ link: e.target.value, fileUid: undefined })}
            />
          )}
        </div>
      )}
      {inputs.needsLocation && (
        <div className="grid gap-3 sm:grid-cols-2">
          <Input label="Latitude" inputMode="decimal" value={values.header?.latitude ?? ''} onChange={(e) => setHeader({ latitude: e.target.value })} required />
          <Input label="Longitude" inputMode="decimal" value={values.header?.longitude ?? ''} onChange={(e) => setHeader({ longitude: e.target.value })} required />
          <Input label="Place name" value={values.header?.name || ''} onChange={(e) => setHeader({ name: e.target.value })} />
          <Input label="Address" value={values.header?.address || ''} onChange={(e) => setHeader({ address: e.target.value })} />
        </div>
      )}
      {inputs.bodyVars.length > 0 && (
        <div className="grid gap-3 sm:grid-cols-2">
          {inputs.bodyVars.map((v) => (
            <Input
              key={v}
              label={inputs.auth ? 'One-time code' : `{{${v}}}`}
              value={values.body?.[v] || ''}
              onChange={(e) => setBody(v, e.target.value)}
              inputMode={inputs.auth ? 'numeric' : undefined}
              maxLength={inputs.auth ? 15 : 1024}
              required
            />
          ))}
        </div>
      )}
      {inputs.buttonInputs.filter((b) => !b.otp).map((b) => (
        <Input key={b.index} label={b.label} value={values.buttons?.[b.index] || ''} onChange={(e) => setButton(b.index, e.target.value)} required />
      ))}
    </div>
  );
}
