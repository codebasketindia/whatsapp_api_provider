import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle2 } from 'lucide-react';
import { humanize } from '../../../../../lib/format';
import QualityBadge from '../../../../../components/whatsapp/QualityBadge';
import { Alert, Badge, Card, InfoTip, KeyValue, QueryState, StatusBadge } from '../../../../../components/ui';

/** T10: quality, messaging limit, throughput, Meta health status and alerts. */
export default function HealthTab({ sender, tools }) {
  const q = useQuery({ queryKey: [tools.scope, 'senders', sender.id, 'health'], queryFn: () => tools.getHealth(sender.id) });
  return (
    <QueryState query={q}>
      {(h) => {
        const health = h.health || {};
        const canSend = health.canSendMessage ?? health.can_send_message;
        const errors = health.errors || [];
        return (
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card title="Health">
              <KeyValue
                items={[
                  { label: 'Quality rating', value: <QualityBadge rating={h.quality} withInfo /> },
                  { label: 'Messaging limit', value: <span className="inline-flex items-center gap-1">{h.tier ? humanize(h.tier) : '—'} <InfoTip term="MESSAGING_LIMIT" /></span> },
                  { label: 'Throughput', value: <span className="inline-flex items-center gap-1">{h.throughput ? humanize(typeof h.throughput === 'object' ? h.throughput.level : h.throughput) : '—'} <InfoTip term="THROUGHPUT" /></span> },
                  { label: 'Display name status', value: h.nameStatus ? <StatusBadge status={h.nameStatus} /> : '—' },
                  { label: 'Access token', value: h.tokenStatus ? <StatusBadge status={h.tokenStatus} /> : '—' },
                  {
                    label: 'Can send messages',
                    value: canSend == null ? '—' : String(canSend).toUpperCase() === 'BLOCKED' || canSend === false ? <Badge tone="red">Blocked</Badge> : String(canSend).toUpperCase() === 'LIMITED' ? <Badge tone="amber">Limited</Badge> : <Badge tone="green">Yes</Badge>,
                  },
                ]}
              />
            </Card>
            <Card title="Alerts">
              {h.alerts?.length || errors.length ? (
                <div className="space-y-2">
                  {(h.alerts || []).map((a, i) => (
                    <Alert key={`a${i}`} variant="warning">{typeof a === 'string' ? a : a.message || JSON.stringify(a)}</Alert>
                  ))}
                  {errors.map((e, i) => (
                    <Alert key={`e${i}`} variant="error" title={e.error_description || e.errorDescription || 'Meta reported a problem'}>
                      {e.possible_solution || e.possibleSolution}
                    </Alert>
                  ))}
                </div>
              ) : (
                <p className="flex items-center gap-2 text-sm text-green-700 dark:text-green-300">
                  <CheckCircle2 className="h-5 w-5" /> No problems reported by Meta.
                </p>
              )}
              {String(h.quality).toUpperCase() === 'RED' && (
                <p className="mt-3 flex items-start gap-2 text-sm text-muted">
                  <AlertTriangle className="mt-0.5 h-4 w-4 text-amber-600" />
                  Low quality usually means people blocked or reported recent messages. Send only to customers who opted in and keep messages relevant.
                </p>
              )}
            </Card>
          </div>
        );
      }}
    </QueryState>
  );
}
