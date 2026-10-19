import { useState } from 'react';
import toast from 'react-hot-toast';
import * as messagesApi from '../../../api/endpoints/messages';
import { getErrorMessage } from '../../../api/axios';
import { Checkbox, FileUpload, Input, Select, Textarea } from '../../../components/ui';

const KINDS = [
  { value: 'text', label: 'Text' },
  { value: 'image', label: 'Image' },
  { value: 'video', label: 'Video' },
  { value: 'document', label: 'Document' },
  { value: 'audio', label: 'Audio' },
  { value: 'buttons', label: 'Text with reply buttons' },
  { value: 'link', label: 'Text with a website button' },
];

const ACCEPT = {
  image: 'image/jpeg,image/png',
  video: 'video/mp4,video/3gpp',
  audio: 'audio/aac,audio/mp4,audio/mpeg,audio/amr,audio/ogg',
  document: '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,application/pdf',
};
const MAX = { image: 5, video: 16, audio: 16, document: 16 };

/** Free-form message editor (only usable while the 24-hour window is open). */
export default function MessageComposer({ senderId, value, onChange, compact }) {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const set = (patch) => onChange({ ...value, ...patch });

  const upload = async (f) => {
    setFile(f);
    if (!f) {
      set({ fileUid: null, fileName: '' });
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
      set({ fileUid: res.fileUid, fileName: f.name, link: '' });
    } catch (e) {
      toast.error(getErrorMessage(e));
      setFile(null);
    } finally {
      setUploading(false);
    }
  };

  const media = ['image', 'video', 'audio', 'document'].includes(value.kind);
  return (
    <div className="space-y-4">
      <Select
        label="Message type"
        value={value.kind}
        onChange={(e) => {
          setFile(null);
          set({ kind: e.target.value, fileUid: null, fileName: '', link: '' });
        }}
        options={KINDS}
      />
      {(value.kind === 'text' || value.kind === 'buttons' || value.kind === 'link') && (
        <Textarea
          label="Message"
          rows={compact ? 3 : 5}
          maxLength={value.kind === 'text' ? 4096 : 1024}
          showCount
          value={value.text}
          onChange={(e) => set({ text: e.target.value })}
          hint="Use *bold*, _italic_ and ~strike~"
        />
      )}
      {value.kind === 'text' && (
        <Checkbox label="Show link preview" checked={value.previewUrl} onChange={(e) => set({ previewUrl: e.target.checked })} />
      )}
      {media && (
        <>
          <FileUpload label="File" accept={ACCEPT[value.kind]} maxSizeMB={MAX[value.kind]} value={file} onChange={upload} disabled={uploading} />
          {uploading && <p className="text-xs text-muted">Uploading…</p>}
          {!file && (
            <Input label="…or a public link (https)" placeholder="https://example.com/file" value={value.link} onChange={(e) => set({ link: e.target.value })} />
          )}
          {value.kind !== 'audio' && (
            <Textarea label="Caption (optional)" rows={2} maxLength={1024} value={value.caption} onChange={(e) => set({ caption: e.target.value })} />
          )}
        </>
      )}
      {value.kind === 'buttons' && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {value.buttons.map((b, i) => (
            <Input
              key={i}
              label={`Button ${i + 1}${i ? ' (optional)' : ''}`}
              maxLength={20}
              value={b}
              onChange={(e) => set({ buttons: value.buttons.map((x, j) => (j === i ? e.target.value : x)) })}
            />
          ))}
        </div>
      )}
      {value.kind === 'link' && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Button text" maxLength={20} value={value.ctaText} onChange={(e) => set({ ctaText: e.target.value })} />
          <Input label="Website link" placeholder="https://example.com" value={value.ctaUrl} onChange={(e) => set({ ctaUrl: e.target.value })} />
        </div>
      )}
    </div>
  );
}
