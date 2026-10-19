import { cn } from '../../lib/cn';

const COLORS = ['bg-brand-600', 'bg-blue-600', 'bg-purple-600', 'bg-amber-600', 'bg-rose-600', 'bg-teal-600'];

export function initials(name = '') {
  const parts = String(name).trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export default function Avatar({ name, src, size = 'md', className }) {
  const sizes = { sm: 'h-8 w-8 text-xs', md: 'h-10 w-10 text-sm', lg: 'h-14 w-14 text-lg' };
  const color = COLORS[(String(name || '').charCodeAt(0) || 0) % COLORS.length];
  if (src) return <img src={src} alt={name || 'Avatar'} className={cn('shrink-0 rounded-full object-cover', sizes[size], className)} />;
  return (
    <span aria-hidden className={cn('inline-flex shrink-0 items-center justify-center rounded-full font-semibold text-white', color, sizes[size], className)}>
      {initials(name)}
    </span>
  );
}
