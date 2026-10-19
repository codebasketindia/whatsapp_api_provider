import { useEffect, useState } from 'react';
import { FileText, MapPin } from 'lucide-react';
import { fetchObjectUrl } from '../../../../api/axios';
import { toApiPath } from '../../../../api/endpoints/files';
import { MessageTicks } from '../../../../components/ui';
import { cn } from '../../../../lib/cn';
import { formatTime } from '../../../../lib/format';

function useProtectedUrl(url) {
  const [obj, setObj] = useState(null);
  useEffect(() => {
    if (!url) return undefined;
    let alive = true;
    let created;
    const path = toApiPath(url);
    if (/^https?:\/\//.test(path)) {
      setObj(path);
      return undefined;
    }
    fetchObjectUrl(path)
      .then((u) => {
        created = u;
        if (alive) setObj(u);
      })
      .catch(() => {});
    return () => {
      alive = false;
      if (created) URL.revokeObjectURL(created);
    };
  }, [url]);
  return obj;
}

function mediaOf(m) {
  return m.mediaUrl || m.media?.url || m.payload?.mediaUrl || null;
}

/** One chat bubble (inbound left, outbound right) with media and ticks. */
export default function ChatBubble({ message: m }) {
  const out = m.direction === 'OUTBOUND';
  const type = String(m.type || '').toUpperCase();
  const url = useProtectedUrl(['IMAGE', 'STICKER', 'VIDEO', 'AUDIO', 'DOCUMENT'].includes(type) ? mediaOf(m) : null);
  const p = m.payload || {};
  const caption = p.image?.caption || p.video?.caption || p.document?.caption || p.media?.caption;
  return (
    <div className={cn('flex', out ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[85%] rounded-lg px-2.5 pb-1.5 pt-2 text-[14px] leading-[19px] shadow-sm sm:max-w-[70%]',
          out ? 'rounded-tr-none bg-[#d9fdd3] text-[#111b21] dark:bg-[#005c4b] dark:text-[#e9edef]' : 'rounded-tl-none bg-white text-[#111b21] dark:bg-[#202c33] dark:text-[#e9edef]',
        )}
      >
        {m.template?.name && <p className="mb-1 text-[11px] font-medium uppercase tracking-wide opacity-60">Template · {m.template.name}</p>}
        {(type === 'IMAGE' || type === 'STICKER') && (url ? <img src={url} alt="" className="mb-1 max-h-72 rounded-md" /> : <div className="mb-1 h-40 w-56 animate-pulse rounded-md bg-black/10" />)}
        {type === 'VIDEO' && url && <video src={url} controls className="mb-1 max-h-72 rounded-md" />}
        {type === 'AUDIO' && url && <audio src={url} controls className="mb-1 w-60" />}
        {type === 'DOCUMENT' && (
          <a href={url || undefined} download={p.document?.filename || 'document'} className="mb-1 flex items-center gap-2 rounded-md bg-black/5 p-2 dark:bg-white/10">
            <FileText className="h-6 w-6 text-red-500" />
            <span className="truncate text-sm">{p.document?.filename || 'Document'}</span>
          </a>
        )}
        {type === 'LOCATION' && (
          <p className="mb-1 flex items-center gap-1"><MapPin className="h-4 w-4 text-red-500" />{p.location?.name || p.location?.address || 'Location'}</p>
        )}
        {(m.preview || caption) && <p className="whitespace-pre-wrap break-words">{caption && type !== 'TEXT' ? caption : m.preview}</p>}
        {m.status === 'FAILED' && m.errorMessage && <p className="mt-1 text-xs text-red-600">{m.errorMessage}</p>}
        <div className="mt-0.5 flex items-center justify-end gap-1 text-[11px] opacity-70">
          <span>{formatTime(m.createdAt)}</span>
          {out && <MessageTicks status={m.status} />}
        </div>
      </div>
    </div>
  );
}
