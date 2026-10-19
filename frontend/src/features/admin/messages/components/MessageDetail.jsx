import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useState } from 'react';
import * as messagesApi from '../../../../api/endpoints/messages';
import { formatPhone, humanize } from '../../../../lib/format';
import { Alert, Badge, Button, DateTime, KeyValue, Money, QueryState, StatusBadge } from '../../../../components/ui';

const STEPS = ['QUEUED', 'ACCEPTED', 'SENT', 'DELIVERED', 'READ'];

/** ML2 message detail: summary, status timeline and raw Meta events. */
export default function MessageDetail({ uid }) {
  const q = useQuery({ queryKey: ['admin', 'messages', 'detail', uid], queryFn: () => messagesApi.getMessage(uid), enabled: Boolean(uid) });
  const [showRaw, setShowRaw] = useState(false);
  return (
    <QueryState query={q}>
      {(m) => {
        const history = m.statusHistory || [];
        const events = m.statusEvents || m.rawEvents || m.events || [];
        return (
          <div className="space-y-5">
            {m.errorMessage && (
              <Alert variant="error" title={m.errorMessage}>
                {m.errorCode && <span className="text-xs">Meta error {m.errorCode}</span>}
              </Alert>
            )}
            <KeyValue
              items={[
                { label: 'Direction', value: m.direction === 'INBOUND' ? <Badge tone="blue">Received</Badge> : <Badge tone="brand">Sent</Badge> },
                { label: 'Status', value: <StatusBadge status={m.status} /> },
                { label: m.direction === 'INBOUND' ? 'From' : 'To', value: formatPhone(m.to || m.from || m.waId) },
                { label: 'Sender number', value: m.sender?.displayPhoneNumber },
                { label: 'Partner', value: m.partner ? <Link className="text-brand-700 hover:underline dark:text-brand-300" to={`/admin/partners/${m.partner.id}`}>{m.partner.businessName}</Link> : '—' },
                { label: 'Type', value: humanize(m.type) },
                { label: 'Template', value: m.template ? `${m.template.name} (${m.template.language})` : '—', hidden: !m.template },
                { label: 'Category', value: m.category ? humanize(m.category) : '—' },
                { label: 'Price', value: <span><Money value={m.price} precise /> <StatusBadge status={m.chargeStatus} /></span> },
                { label: 'Billable by Meta', value: m.billable == null ? '—' : m.billable ? 'Yes' : 'No (free)' },
                { label: 'Source', value: humanize(m.source) },
                { label: 'Campaign', value: m.campaign?.name, hidden: !m.campaign },
                { label: 'Client reference', value: m.clientReference, hidden: !m.clientReference },
                { label: 'Message id', value: <span className="font-mono text-xs">{m.uid}</span> },
                { label: 'WhatsApp id (wamid)', value: <span className="break-all font-mono text-xs">{m.wamid || '—'}</span> },
                { label: 'Created', value: <DateTime value={m.createdAt} /> },
              ]}
            />
            {m.preview && (
              <div>
                <h3 className="mb-1 text-sm font-semibold text-ink">Content</h3>
                <p className="whitespace-pre-line rounded-lg bg-surface-2 p-3 text-sm text-ink">{m.preview}</p>
              </div>
            )}
            <div>
              <h3 className="mb-2 text-sm font-semibold text-ink">Status timeline</h3>
              <ol className="relative space-y-3 border-l border-line pl-5">
                {(history.length ? history : STEPS.filter((s) => m[`${s.toLowerCase()}At`]).map((s) => ({ status: s, at: m[`${s.toLowerCase()}At`] }))).map((h, i) => (
                  <li key={`${h.status}-${i}`}>
                    <span className={`absolute -left-[7px] mt-1 h-3 w-3 rounded-full ${h.status === 'FAILED' ? 'bg-red-500' : 'bg-brand-500'}`} />
                    <p className="text-sm font-medium text-ink">{humanize(h.status)}</p>
                    <p className="text-xs text-muted"><DateTime value={h.at} />{h.errorCode ? ` · error ${h.errorCode}` : ''}</p>
                  </li>
                ))}
                {!history.length && !m.sentAt && <li className="text-sm text-muted">Created <DateTime value={m.createdAt} /></li>}
              </ol>
            </div>
            {(events.length > 0 || m.payload) && (
              <div>
                <Button size="sm" variant="secondary" onClick={() => setShowRaw((v) => !v)}>{showRaw ? 'Hide' : 'Show'} raw data</Button>
                {showRaw && (
                  <pre className="mt-2 max-h-96 overflow-auto rounded-lg bg-surface-2 p-3 text-xs text-ink">{JSON.stringify({ payload: m.payload, events }, null, 2)}</pre>
                )}
              </div>
            )}
          </div>
        );
      }}
    </QueryState>
  );
}
