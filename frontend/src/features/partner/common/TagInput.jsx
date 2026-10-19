import { useState } from 'react';
import { X } from 'lucide-react';
import { cn } from '../../../lib/cn';

/** Chips input: Enter or comma adds a value. value: string[]. */
export default function TagInput({ label, value = [], onChange, placeholder = 'Type and press Enter', suggestions = [], max = 30, transform = (s) => s, hint, className }) {
  const [text, setText] = useState('');
  const add = (raw) => {
    const v = transform(raw.trim());
    if (!v || value.includes(v) || value.length >= max) return;
    onChange([...value, v]);
  };
  const remove = (v) => onChange(value.filter((x) => x !== v));
  const listId = `tags-${label || 'input'}`.replace(/\s/g, '-');
  return (
    <div className={className}>
      {label && <span className="label-base">{label}</span>}
      <div className="flex min-h-tap flex-wrap items-center gap-1.5 rounded-lg border border-line bg-surface px-2 py-1.5 focus-within:border-brand-500 focus-within:ring-2 focus-within:ring-brand-500/30">
        {value.map((v) => (
          <span key={v} className="inline-flex items-center gap-1 rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-800 dark:bg-brand-500/15 dark:text-brand-200">
            {v}
            <button type="button" onClick={() => remove(v)} aria-label={`Remove ${v}`} className="rounded-full p-0.5 hover:bg-black/10">
              <X className="h-3 w-3" />
            </button>
          </span>
        ))}
        <input
          value={text}
          list={suggestions.length ? listId : undefined}
          onChange={(e) => {
            const v = e.target.value;
            if (v.endsWith(',')) {
              add(v.slice(0, -1));
              setText('');
            } else setText(v);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              add(text);
              setText('');
            } else if (e.key === 'Backspace' && !text && value.length) {
              remove(value[value.length - 1]);
            }
          }}
          onBlur={() => {
            if (text) {
              add(text);
              setText('');
            }
          }}
          placeholder={value.length ? '' : placeholder}
          className={cn('min-w-[120px] flex-1 bg-transparent px-1 py-1 text-sm text-ink placeholder:text-muted focus:outline-none')}
        />
        {suggestions.length > 0 && (
          <datalist id={listId}>
            {suggestions.filter((s) => !value.includes(s)).map((s) => <option key={s} value={s} />)}
          </datalist>
        )}
      </div>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}
