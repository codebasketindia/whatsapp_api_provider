import { AlertTriangle, CheckCircle2, Circle, ExternalLink } from 'lucide-react';
import { Badge } from '../../../../components/ui';

/** ChecklistDto { items: [{ key, status: DONE|PENDING|ACTION_REQUIRED, title, help, actionUrl }] } */
export default function Checklist({ checklist }) {
  const items = checklist?.items || [];
  if (!items.length) return <p className="text-sm text-muted">Nothing to check.</p>;
  return (
    <ul className="divide-y divide-line">
      {items.map((i) => {
        const Icon = i.status === 'DONE' ? CheckCircle2 : i.status === 'ACTION_REQUIRED' ? AlertTriangle : Circle;
        const color = i.status === 'DONE' ? 'text-green-600' : i.status === 'ACTION_REQUIRED' ? 'text-amber-600' : 'text-muted';
        return (
          <li key={i.key} className="flex gap-3 py-3">
            <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${color}`} aria-hidden />
            <div className="min-w-0 flex-1">
              <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-ink">
                {i.title}
                {i.status === 'ACTION_REQUIRED' && <Badge tone="amber">Action needed</Badge>}
                {i.status === 'PENDING' && <Badge tone="gray">Pending</Badge>}
              </p>
              {i.help && <p className="mt-0.5 text-sm text-muted">{i.help}</p>}
              {i.actionUrl && (
                <a href={i.actionUrl} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 text-sm text-brand-700 hover:underline dark:text-brand-300">
                  Open in Meta <ExternalLink className="h-3.5 w-3.5" />
                </a>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}
