import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Download, RefreshCw } from 'lucide-react';
import * as paymentsApi from '../../../../api/endpoints/payments';
import { useAuth } from '../../../../auth/useAuth';
import { humanize } from '../../../../lib/format';
import {
  Badge, Button, Card, ConfirmDialog, DateTime, EmptyState, ErrorState, KeyValue, Money, PageHeader, PageLoader, StatusBadge,
} from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';

export default function PaymentDetailPage() {
  const { uid } = useParams();
  const { hasPermission } = useAuth();
  const [confirm, setConfirm] = useState(false);
  const [openLog, setOpenLog] = useState(null);
  const q = useQuery({ queryKey: ['admin', 'payments', 'detail', uid], queryFn: () => paymentsApi.getPayment(uid) });
  const m = useApiMutation({
    mutationFn: () => paymentsApi.reconcilePayment(uid),
    success: (o) => `Payment status is now ${humanize(o?.status)}`,
    invalidate: [['admin', 'payments']],
    onSuccess: () => setConfirm(false),
  });
  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const o = q.data;
  const canReconcile = hasPermission('PAYMENT_RECONCILE') && ['CREATED', 'ATTEMPTED', 'FAILED', 'EXPIRED'].includes(o.status);
  return (
    <>
      <PageHeader
        title={`Payment ${o.gatewayOrderId || o.uid}`}
        subtitle={<span className="flex items-center gap-2">{o.partner?.businessName} <StatusBadge status={o.status} /></span>}
        backTo="/admin/payments"
        actions={
          <>
            {o.invoice && (
              <Button variant="secondary" leftIcon={Download} onClick={() => paymentsApi.downloadInvoice(o.invoice.id, o.invoice.invoiceNo)}>
                Invoice {o.invoice.invoiceNo}
              </Button>
            )}
            {canReconcile && <Button leftIcon={RefreshCw} onClick={() => setConfirm(true)}>Check with gateway</Button>}
          </>
        }
      />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card title="Amounts" className="lg:col-span-1">
          <KeyValue
            columns={1}
            items={[
              { label: o.creditPack ? `Credit pack: ${o.creditPack.name}` : 'Amount', value: <Money value={o.baseAmount} /> },
              { label: `GST (${Number(o.gstPercent || 0)}%)`, value: <Money value={o.gstAmount} /> },
              { label: 'Total paid', value: <Money value={o.totalAmount} className="text-lg font-semibold" /> },
              { label: 'Bonus credit', value: <Money value={o.bonusAmount} /> },
              { label: 'Credited to wallet', value: <Money value={o.creditAmount} className="font-semibold text-green-700 dark:text-green-300" /> },
            ]}
          />
        </Card>
        <Card title="Details" className="lg:col-span-2">
          <KeyValue
            items={[
              { label: 'Gateway', value: humanize(o.gateway) },
              { label: 'Method', value: o.method ? humanize(o.method) : '—' },
              { label: 'Order id', value: <span className="font-mono text-xs">{o.gatewayOrderId || '—'}</span> },
              { label: 'Payment id', value: <span className="font-mono text-xs">{o.gatewayPaymentId || '—'}</span> },
              { label: 'Our reference', value: <span className="font-mono text-xs">{o.uid}</span> },
              { label: 'Confirmed by', value: o.verifiedVia ? humanize(o.verifiedVia) : '—' },
              { label: 'Created', value: <DateTime value={o.createdAt} /> },
              { label: 'Paid at', value: <DateTime value={o.paidAt} /> },
              { label: 'Failure reason', value: o.failureReason, hidden: !o.failureReason },
            ]}
          />
        </Card>
      </div>
      <Card title="Gateway log" subtitle="Requests, responses and webhooks for this order (secrets removed)." className="mt-4">
        {!o.logs?.length ? (
          <EmptyState compact title="No log entries" />
        ) : (
          <ol className="divide-y divide-line">
            {o.logs.map((l, i) => (
              <li key={l.id ?? i} className="py-3">
                <button type="button" className="flex min-h-tap w-full items-center justify-between gap-3 text-left" onClick={() => setOpenLog(openLog === i ? null : i)}>
                  <span className="flex flex-wrap items-center gap-2 text-sm">
                    <Badge tone="blue">{humanize(l.direction)}</Badge>
                    <span className="font-medium text-ink">{l.event}</span>
                    {l.httpStatus && <Badge tone={l.httpStatus < 300 ? 'green' : 'red'}>HTTP {l.httpStatus}</Badge>}
                  </span>
                  <DateTime value={l.createdAt} className="shrink-0 text-xs text-muted" />
                </button>
                {openLog === i && (
                  <pre className="mt-2 max-h-80 overflow-auto rounded-lg bg-surface-2 p-3 text-xs text-ink">
                    {JSON.stringify(typeof l.payload === 'string' ? safeParse(l.payload) : l.payload, null, 2)}
                  </pre>
                )}
              </li>
            ))}
          </ol>
        )}
      </Card>
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title="Check this payment with the gateway?"
        message="If the gateway reports it as paid, the wallet is credited once. If it was already credited, nothing changes."
        confirmLabel="Check now"
        loading={m.isPending}
        onConfirm={() => m.mutate()}
      />
    </>
  );
}

function safeParse(s) {
  try {
    return JSON.parse(s);
  } catch {
    return s;
  }
}
