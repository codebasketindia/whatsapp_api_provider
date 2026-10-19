import { AlertTriangle, CheckCircle2, Circle, ExternalLink, XCircle } from 'lucide-react';
import { Badge, Button } from '../../../../components/ui';
import { cn } from '../../../../lib/cn';

const STEP_ICON = {
  DONE: <CheckCircle2 className="h-5 w-5 text-green-600" aria-hidden />,
  FAILED: <XCircle className="h-5 w-5 text-red-600" aria-hidden />,
  SKIPPED: <Circle className="h-5 w-5 text-gray-400" aria-hidden />,
};

/** Server-side onboarding steps returned by E5/E7. */
export function StepsList({ steps = [] }) {
  if (!steps.length) return null;
  return (
    <ol className="space-y-2">
      {steps.map((s, i) => (
        <li key={`${s.step}-${i}`} className="flex items-start gap-3 rounded-lg bg-surface-2 p-3">
          {STEP_ICON[String(s.status).toUpperCase()] || <Circle className="h-5 w-5 text-gray-400" aria-hidden />}
          <div className="min-w-0 text-sm">
            <p className="font-medium text-ink">{s.title || humanStep(s.step)}</p>
            {s.message && <p className="text-muted">{s.message}</p>}
          </div>
        </li>
      ))}
    </ol>
  );
}

function humanStep(step) {
  const map = {
    EXCHANGE_CODE: 'Securely connected to Meta',
    FETCH_WABA: 'Found your WhatsApp Business Account',
    FETCH_PHONE: 'Found your phone number',
    SUBSCRIBE_WEBHOOKS: 'Turned on message updates',
    REGISTER_NUMBER: 'Registered the number for sending',
    SYNC_APP_DATA: 'Synced your WhatsApp Business App',
    SHARE_CREDIT_LINE: 'Set up billing',
    SAVE: 'Saved your number',
  };
  return map[step] || String(step || '').replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

/** "What to do next" checklist (ChecklistDto). */
export function Checklist({ checklist, compact }) {
  const items = checklist?.items || [];
  if (!items.length) return null;
  const pending = items.filter((i) => i.status !== 'DONE').length;
  return (
    <div>
      {!compact && (
        <p className="mb-3 text-sm text-muted">
          {pending === 0 ? 'Everything is set up.' : `${pending} thing${pending > 1 ? 's' : ''} left to finish in Meta.`}
        </p>
      )}
      <ul className="space-y-2">
        {items.map((i) => {
          const done = i.status === 'DONE';
          const action = i.status === 'ACTION_REQUIRED';
          return (
            <li key={i.key} className={cn('flex items-start gap-3 rounded-lg border p-3', action ? 'border-amber-300 bg-amber-50 dark:border-amber-500/40 dark:bg-amber-500/10' : 'border-line')}>
              {done ? (
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-green-600" aria-hidden />
              ) : action ? (
                <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" aria-hidden />
              ) : (
                <Circle className="mt-0.5 h-5 w-5 shrink-0 text-gray-400" aria-hidden />
              )}
              <div className="min-w-0 flex-1 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-ink">{i.title}</p>
                  {!done && <Badge tone={action ? 'amber' : 'gray'}>{action ? 'Action needed' : 'Pending'}</Badge>}
                </div>
                {i.help && !done && <p className="mt-0.5 text-muted">{i.help}</p>}
                {i.actionUrl && !done && (
                  <Button size="sm" variant="link" href={i.actionUrl} rightIcon={ExternalLink} className="mt-1">
                    Open in Meta
                  </Button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
