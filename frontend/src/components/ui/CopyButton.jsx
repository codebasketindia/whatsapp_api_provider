import { useState } from 'react';
import { Check, Copy } from 'lucide-react';
import toast from 'react-hot-toast';
import { cn } from '../../lib/cn';

async function copyText(text) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  document.execCommand('copy');
  ta.remove();
}

/** Copies `value`; shows a tick for 2 s. `label` renders a text button instead of icon-only. */
export default function CopyButton({ value, label, className, toastMessage = 'Copied' }) {
  const [done, setDone] = useState(false);
  const onClick = async (e) => {
    e.stopPropagation();
    try {
      await copyText(String(value ?? ''));
      setDone(true);
      toast.success(toastMessage);
      setTimeout(() => setDone(false), 2000);
    } catch {
      toast.error('Could not copy. Please copy manually.');
    }
  };
  const Icon = done ? Check : Copy;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label || 'Copy'}
      title={label || 'Copy'}
      className={cn(
        'inline-flex min-h-[36px] items-center justify-center gap-1.5 rounded-lg text-sm text-muted transition-colors hover:bg-surface-2 hover:text-ink',
        label ? 'border border-line px-3' : 'h-9 w-9',
        className,
      )}
    >
      <Icon className={cn('h-4 w-4', done && 'text-brand-600')} aria-hidden />
      {label && <span>{done ? 'Copied' : label}</span>}
    </button>
  );
}
