import { CheckCheck, Check, Clock, Copy, ExternalLink, FileText, ImageIcon, List, MapPin, Phone, Reply, Video, AlertCircle } from 'lucide-react';
import { cn } from '../../lib/cn';
import { formatTime } from '../../lib/format';

const VAR_RE = /\{\{\s*([\w.]+)\s*\}\}/g;

/**
 * Replaces {{1}} / {{name}} with values. Missing values stay visible and highlighted.
 * values: array (positional, index 0 => {{1}}) or object (named).
 */
export function renderTemplateText(text, values) {
  if (!text) return [];
  const parts = [];
  let last = 0;
  let m;
  VAR_RE.lastIndex = 0;
  while ((m = VAR_RE.exec(text)) !== null) {
    if (m.index > last) parts.push({ type: 'text', value: text.slice(last, m.index) });
    const key = m[1];
    let v;
    if (Array.isArray(values)) v = values[Number(key) - 1];
    else if (values && typeof values === 'object') v = values[key];
    parts.push(v !== undefined && v !== null && v !== '' ? { type: 'value', value: String(v) } : { type: 'var', value: m[0] });
    last = m.index + m[0].length;
  }
  if (last < text.length) parts.push({ type: 'text', value: text.slice(last) });
  return parts;
}

/** Minimal WhatsApp formatting: *bold*, _italic_, ~strike~, ```mono```. */
function formatInline(str, keyBase) {
  const tokens = [];
  const re = /(\*[^*\n]+\*|_[^_\n]+_|~[^~\n]+~|```[^`]+```)/g;
  let last = 0;
  let m;
  let i = 0;
  while ((m = re.exec(str)) !== null) {
    if (m.index > last) tokens.push(str.slice(last, m.index));
    const t = m[0];
    const k = `${keyBase}-${i++}`;
    if (t.startsWith('```')) tokens.push(<code key={k} className="font-mono text-[0.9em]">{t.slice(3, -3)}</code>);
    else if (t[0] === '*') tokens.push(<strong key={k}>{t.slice(1, -1)}</strong>);
    else if (t[0] === '_') tokens.push(<em key={k}>{t.slice(1, -1)}</em>);
    else tokens.push(<s key={k}>{t.slice(1, -1)}</s>);
    last = m.index + t.length;
  }
  if (last < str.length) tokens.push(str.slice(last));
  return tokens;
}

function RichText({ text, values }) {
  const parts = renderTemplateText(text, values);
  return (
    <>
      {parts.map((p, i) =>
        p.type === 'var' ? (
          <span key={i} className="rounded bg-amber-200/70 px-0.5 text-amber-900 dark:bg-amber-400/30 dark:text-amber-100">
            {p.value}
          </span>
        ) : p.type === 'value' ? (
          <span key={i} className="rounded bg-brand-100/70 px-0.5 dark:bg-brand-500/20">{formatInline(p.value, `v${i}`)}</span>
        ) : (
          <span key={i}>{formatInline(p.value, `t${i}`)}</span>
        ),
      )}
    </>
  );
}

function Ticks({ status }) {
  const s = String(status || '').toUpperCase();
  if (s === 'READ') return <CheckCheck className="h-4 w-4 text-sky-500" aria-label="Read" />;
  if (s === 'DELIVERED') return <CheckCheck className="h-4 w-4 text-gray-500" aria-label="Delivered" />;
  if (s === 'SENT' || s === 'ACCEPTED') return <Check className="h-4 w-4 text-gray-500" aria-label="Sent" />;
  if (s === 'FAILED') return <AlertCircle className="h-4 w-4 text-red-500" aria-label="Failed" />;
  if (s === 'QUEUED') return <Clock className="h-3.5 w-3.5 text-gray-500" aria-label="Queued" />;
  return <CheckCheck className="h-4 w-4 text-sky-500" aria-hidden />;
}

function Header({ header, values }) {
  if (!header) return null;
  const format = String(header.format || header.type || 'TEXT').toUpperCase();
  if (format === 'TEXT') {
    return (
      <p className="mb-1 font-semibold">
        <RichText text={header.text} values={values} />
      </p>
    );
  }
  const mediaUrl = header.url || header.previewUrl;
  const box = 'mb-1.5 flex aspect-[1.91/1] w-full items-center justify-center overflow-hidden rounded-md bg-black/10 text-gray-500 dark:bg-white/10 dark:text-gray-300';
  if (format === 'IMAGE') {
    return mediaUrl ? <img src={mediaUrl} alt="Header" className="mb-1.5 w-full rounded-md object-cover" /> : <div className={box}><ImageIcon className="h-10 w-10" /></div>;
  }
  if (format === 'VIDEO') {
    return mediaUrl ? <video src={mediaUrl} className="mb-1.5 w-full rounded-md" controls /> : <div className={box}><Video className="h-10 w-10" /></div>;
  }
  if (format === 'DOCUMENT') {
    return (
      <div className="mb-1.5 flex items-center gap-2 rounded-md bg-black/5 p-2 dark:bg-white/10">
        <FileText className="h-8 w-8 text-red-500" />
        <span className="truncate text-sm">{header.filename || 'Document.pdf'}</span>
      </div>
    );
  }
  if (format === 'LOCATION') {
    return (
      <div className={cn(box, 'flex-col gap-1 text-xs')}>
        <MapPin className="h-8 w-8 text-red-500" />
        {header.name || header.address ? <span className="px-2 text-center">{header.name || header.address}</span> : 'Location'}
      </div>
    );
  }
  return null;
}

function ButtonIcon({ type }) {
  const t = String(type || '').toUpperCase();
  if (t === 'URL') return <ExternalLink className="h-4 w-4" />;
  if (t === 'PHONE_NUMBER') return <Phone className="h-4 w-4" />;
  if (t === 'COPY_CODE' || t === 'OTP') return <Copy className="h-4 w-4" />;
  if (t === 'LIST') return <List className="h-4 w-4" />;
  return <Reply className="h-4 w-4" />;
}

/**
 * Realistic WhatsApp chat bubble preview.
 * Props:
 *  - header: { format: TEXT|IMAGE|VIDEO|DOCUMENT|LOCATION, text?, url?, filename?, name?, address? }
 *  - body: string with {{1}} or {{name}} placeholders
 *  - footer: string
 *  - buttons: [{ type: QUICK_REPLY|URL|PHONE_NUMBER|COPY_CODE|OTP, text }]
 *  - listButton: string (interactive list: shows a single "menu" button)
 *  - values / headerValues: variable values (array for positional, object for named)
 *  - time: ISO date, status: message status for ticks, direction: 'out' | 'in'
 *  - businessName: shown in the chat header bar (set showHeader)
 */
export default function WhatsAppPreview({
  header,
  body,
  footer,
  buttons = [],
  listButton,
  values,
  headerValues,
  time,
  status,
  direction = 'out',
  businessName = 'Your business',
  showHeader = true,
  className,
  children,
}) {
  const out = direction === 'out';
  const visibleButtons = buttons.slice(0, buttons.length > 3 ? 2 : 3);
  const extra = buttons.length > 3 ? buttons.length - 2 : 0;
  return (
    <div className={cn('overflow-hidden rounded-2xl border border-line shadow-card', className)}>
      {showHeader && (
        <div className="flex items-center gap-3 bg-brand-900 px-4 py-2.5 text-white dark:bg-[#202c33]">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-white/20 text-sm font-semibold">
            {businessName.slice(0, 1).toUpperCase()}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">{businessName}</p>
            <p className="text-[11px] text-white/70">Business account</p>
          </div>
        </div>
      )}
      <div className="wa-wallpaper min-h-[180px] p-3 sm:p-4">
        {children || (
          <div className={cn('flex', out ? 'justify-end' : 'justify-start')}>
            <div className="w-full max-w-[320px]">
              <div
                className={cn(
                  'relative rounded-lg px-2.5 pb-1.5 pt-2 text-[14.2px] leading-[19px] text-[#111b21] shadow-sm dark:text-[#e9edef]',
                  out ? 'rounded-tr-none bg-[#d9fdd3] dark:bg-[#005c4b]' : 'rounded-tl-none bg-white dark:bg-[#202c33]',
                )}
              >
                <Header header={header} values={headerValues ?? values} />
                {body ? (
                  <p className="whitespace-pre-wrap break-words">
                    <RichText text={body} values={values} />
                  </p>
                ) : (
                  <p className="italic text-gray-500">Your message will appear here</p>
                )}
                {footer && <p className="mt-1 text-[13px] text-gray-500 dark:text-gray-400">{footer}</p>}
                <div className="mt-0.5 flex items-center justify-end gap-1 text-[11px] text-gray-500 dark:text-gray-400">
                  <span>{time ? formatTime(time) : formatTime(new Date())}</span>
                  {out && <Ticks status={status} />}
                </div>
              </div>
              {(visibleButtons.length > 0 || listButton) && (
                <div className="mt-0.5 space-y-0.5">
                  {listButton && (
                    <div className="flex min-h-[40px] items-center justify-center gap-2 rounded-lg bg-white text-sm font-medium text-[#027eb5] shadow-sm dark:bg-[#202c33] dark:text-[#53bdeb]">
                      <List className="h-4 w-4" />
                      {listButton}
                    </div>
                  )}
                  {visibleButtons.map((b, i) => (
                    <div
                      key={`${b.text}-${i}`}
                      className="flex min-h-[40px] items-center justify-center gap-2 rounded-lg bg-white px-2 text-center text-sm font-medium text-[#027eb5] shadow-sm dark:bg-[#202c33] dark:text-[#53bdeb]"
                    >
                      <ButtonIcon type={b.type} />
                      <span className="truncate">{b.text || (String(b.type).toUpperCase() === 'COPY_CODE' ? 'Copy code' : 'Button')}</span>
                    </div>
                  ))}
                  {extra > 0 && (
                    <div className="flex min-h-[40px] items-center justify-center gap-2 rounded-lg bg-white text-sm font-medium text-[#027eb5] shadow-sm dark:bg-[#202c33] dark:text-[#53bdeb]">
                      <List className="h-4 w-4" />
                      See all options
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export { Ticks as MessageTicks };
