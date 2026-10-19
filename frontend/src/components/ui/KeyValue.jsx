import { cn } from '../../lib/cn';

/** Definition list for detail screens. items: [{ label, value, hidden }] */
export default function KeyValue({ items, columns = 2, className }) {
  const cols = { 1: 'sm:grid-cols-1', 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-3' };
  return (
    <dl className={cn('grid grid-cols-1 gap-x-6 gap-y-4', cols[columns], className)}>
      {items
        .filter((i) => !i.hidden)
        .map((i) => (
          <div key={i.label} className="min-w-0">
            <dt className="text-xs font-medium uppercase tracking-wide text-muted">{i.label}</dt>
            <dd className="mt-1 break-words text-sm text-ink">{i.value ?? '—'}</dd>
          </div>
        ))}
    </dl>
  );
}
