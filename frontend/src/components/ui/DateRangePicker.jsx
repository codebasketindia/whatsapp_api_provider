import { useMemo } from 'react';
import { Calendar } from 'lucide-react';
import { cn } from '../../lib/cn';
import { addDaysToDateParam, toIstDateParam } from '../../lib/format';

/** Builds IST preset ranges (YYYY-MM-DD) relative to today. */
export function dateRangePresets(now = new Date()) {
  const today = toIstDateParam(now);
  const monthStart = `${today.slice(0, 8)}01`;
  return [
    { key: 'today', label: 'Today', from: today, to: today },
    { key: '7d', label: 'Last 7 days', from: addDaysToDateParam(today, -6), to: today },
    { key: '30d', label: 'Last 30 days', from: addDaysToDateParam(today, -29), to: today },
    { key: 'month', label: 'This month', from: monthStart, to: today },
  ];
}

/**
 * value: { from, to } as YYYY-MM-DD (IST). onChange({ from, to }).
 */
export default function DateRangePicker({ value = {}, onChange, label, className, allowClear = false }) {
  const presets = useMemo(() => dateRangePresets(), []);
  const activeKey = presets.find((p) => p.from === value.from && p.to === value.to)?.key || (value.from || value.to ? 'custom' : '');
  return (
    <div className={cn('space-y-2', className)}>
      {label && <span className="label-base">{label}</span>}
      <div className="-mx-1 flex flex-wrap gap-1.5">
        {presets.map((p) => (
          <button
            key={p.key}
            type="button"
            onClick={() => onChange({ from: p.from, to: p.to })}
            className={cn(
              'min-h-[36px] rounded-full border px-3 text-sm',
              activeKey === p.key ? 'border-brand-600 bg-brand-600 text-white' : 'border-line bg-surface text-ink hover:bg-surface-2',
            )}
          >
            {p.label}
          </button>
        ))}
        {allowClear && (value.from || value.to) && (
          <button type="button" onClick={() => onChange({ from: '', to: '' })} className="min-h-[36px] rounded-full px-3 text-sm text-muted hover:text-ink">
            Clear
          </button>
        )}
      </div>
      <div className="flex items-center gap-2">
        <label className="relative flex-1">
          <span className="sr-only">From date</span>
          <Calendar className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
          <input
            type="date"
            value={value.from || ''}
            max={value.to || undefined}
            onChange={(e) => onChange({ ...value, from: e.target.value })}
            className="field-base pl-9"
          />
        </label>
        <span className="text-sm text-muted">to</span>
        <label className="relative flex-1">
          <span className="sr-only">To date</span>
          <Calendar className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
          <input
            type="date"
            value={value.to || ''}
            min={value.from || undefined}
            onChange={(e) => onChange({ ...value, to: e.target.value })}
            className="field-base pl-9"
          />
        </label>
      </div>
    </div>
  );
}
