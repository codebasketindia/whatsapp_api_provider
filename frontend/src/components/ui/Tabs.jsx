import { useSearchParams } from 'react-router-dom';
import { cn } from '../../lib/cn';

/**
 * Scrollable tab bar. tabs: [{ value, label, icon?, count?, hidden? }]
 * Controlled via value/onChange, or set urlParam="tab" to sync with ?tab=.
 */
export default function Tabs({ tabs, value, onChange, urlParam, className }) {
  const [params, setParams] = useSearchParams();
  const visible = tabs.filter((t) => !t.hidden);
  const current = urlParam ? params.get(urlParam) || visible[0]?.value : value;
  const select = (v) => {
    if (urlParam) {
      const next = new URLSearchParams(params);
      next.set(urlParam, v);
      setParams(next, { replace: true });
    }
    onChange?.(v);
  };
  return (
    <div className={cn('-mx-4 overflow-x-auto border-b border-line px-4 scrollbar-none sm:mx-0 sm:px-0', className)}>
      <div role="tablist" className="flex min-w-max gap-1">
        {visible.map((t) => {
          const active = t.value === current;
          const Icon = t.icon;
          return (
            <button
              key={t.value}
              role="tab"
              type="button"
              aria-selected={active}
              onClick={() => select(t.value)}
              className={cn(
                'inline-flex min-h-tap items-center gap-2 whitespace-nowrap border-b-2 px-3 text-sm font-medium transition-colors',
                active ? 'border-brand-600 text-brand-700 dark:text-brand-300' : 'border-transparent text-muted hover:text-ink',
              )}
            >
              {Icon && <Icon className="h-4 w-4" aria-hidden />}
              {t.label}
              {t.count !== undefined && t.count !== null && (
                <span className={cn('rounded-full px-2 py-0.5 text-xs', active ? 'bg-brand-100 text-brand-800 dark:bg-brand-500/20 dark:text-brand-200' : 'bg-surface-2 text-muted')}>
                  {t.count}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** Returns the active tab value from ?tab= (or the first tab). */
export function useActiveTab(tabs, urlParam = 'tab') {
  const [params] = useSearchParams();
  const visible = tabs.filter((t) => !t.hidden);
  const v = params.get(urlParam);
  return visible.some((t) => t.value === v) ? v : visible[0]?.value;
}
