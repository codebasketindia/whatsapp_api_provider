import { cn } from '../../lib/cn';

/** value 0–100. segments: [{ value, className }] for stacked bars. */
export default function ProgressBar({ value = 0, segments, label, showValue, className, tone = 'brand' }) {
  const tones = { brand: 'bg-brand-600', green: 'bg-green-600', amber: 'bg-amber-500', red: 'bg-red-600', blue: 'bg-blue-600' };
  const v = Math.max(0, Math.min(100, Number(value) || 0));
  return (
    <div className={className}>
      {(label || showValue) && (
        <div className="mb-1 flex justify-between text-sm">
          <span className="text-muted">{label}</span>
          {showValue && <span className="font-medium text-ink">{v.toFixed(0)}%</span>}
        </div>
      )}
      <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-line/70" role="progressbar" aria-valuenow={v} aria-valuemin={0} aria-valuemax={100}>
        {segments
          ? segments.map((s, i) => <span key={i} className={cn('h-full', s.className)} style={{ width: `${Math.max(0, Math.min(100, s.value))}%` }} />)
          : <span className={cn('h-full rounded-full transition-all', tones[tone])} style={{ width: `${v}%` }} />}
      </div>
    </div>
  );
}
