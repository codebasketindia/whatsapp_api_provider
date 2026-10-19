import { useRef, useState } from 'react';
import { FileText, ImageIcon, UploadCloud, X } from 'lucide-react';
import { cn } from '../../lib/cn';
import { formatBytes } from '../../lib/format';

function matchesAccept(file, accept) {
  if (!accept) return true;
  const rules = accept.split(',').map((r) => r.trim().toLowerCase());
  const name = file.name.toLowerCase();
  const type = (file.type || '').toLowerCase();
  return rules.some((r) => {
    if (r.startsWith('.')) return name.endsWith(r);
    if (r.endsWith('/*')) return type.startsWith(r.slice(0, -1));
    return type === r;
  });
}

/**
 * Drag & drop / tap-to-pick single file input with type and size checks.
 * value: File | null; onChange(File | null).
 */
export default function FileUpload({ label, value, onChange, accept, maxSizeMB = 2, hint, error, disabled, className }) {
  const inputRef = useRef(null);
  const [dragOver, setDragOver] = useState(false);
  const [localError, setLocalError] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);

  const pick = (file) => {
    setLocalError(null);
    if (!file) return;
    if (!matchesAccept(file, accept)) {
      setLocalError(`This file type is not allowed. Allowed: ${accept}`);
      return;
    }
    if (file.size > maxSizeMB * 1024 * 1024) {
      setLocalError(`File is too large. Maximum size is ${maxSizeMB} MB.`);
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(file.type.startsWith('image/') ? URL.createObjectURL(file) : null);
    onChange?.(file);
  };

  const clear = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(null);
    if (inputRef.current) inputRef.current.value = '';
    onChange?.(null);
  };

  const shownError = localError || (typeof error === 'string' ? error : error?.message);

  return (
    <div className={className}>
      {label && <span className="label-base">{label}</span>}
      {value ? (
        <div className="flex items-center gap-3 rounded-xl border border-line bg-surface-2 p-3">
          {previewUrl ? (
            <img src={previewUrl} alt="Selected file preview" className="h-14 w-14 rounded-lg object-cover" />
          ) : (
            <span className="flex h-14 w-14 items-center justify-center rounded-lg bg-surface text-muted">
              {value.type?.startsWith('image/') ? <ImageIcon className="h-6 w-6" /> : <FileText className="h-6 w-6" />}
            </span>
          )}
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-ink">{value.name}</p>
            <p className="text-xs text-muted">{formatBytes(value.size)}</p>
          </div>
          {!disabled && (
            <button type="button" onClick={clear} className="flex h-11 w-11 items-center justify-center rounded-lg text-muted hover:bg-surface hover:text-ink" aria-label="Remove file">
              <X className="h-5 w-5" />
            </button>
          )}
        </div>
      ) : (
        <button
          type="button"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            pick(e.dataTransfer.files?.[0]);
          }}
          className={cn(
            'flex w-full flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-4 py-8 text-center transition-colors',
            dragOver ? 'border-brand-500 bg-brand-50 dark:bg-brand-500/10' : 'border-line hover:bg-surface-2',
            shownError && 'border-red-400',
          )}
        >
          <UploadCloud className="h-8 w-8 text-muted" aria-hidden />
          <span className="text-sm font-medium text-ink">Tap to choose a file or drag it here</span>
          <span className="text-xs text-muted">{hint || `${accept ? `${accept} · ` : ''}max ${maxSizeMB} MB`}</span>
        </button>
      )}
      <input ref={inputRef} type="file" accept={accept} className="hidden" onChange={(e) => pick(e.target.files?.[0])} />
      {shownError && <p className="mt-1 text-xs text-red-600" role="alert">{shownError}</p>}
    </div>
  );
}
