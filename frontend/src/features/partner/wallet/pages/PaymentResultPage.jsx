import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import * as paymentsApi from '../../../../api/endpoints/payments';
import { Button, Card, ErrorState, KeyValue, PageHeader, PageLoader, Spinner } from '../../../../components/ui';
import { formatDateTime, formatMoney } from '../../../../lib/format';

const MAX_WAIT_MS = 60_000;

export default function PaymentResultPage() {
  const { uid } = useParams();
  const qc = useQueryClient();
  const started = useRef(Date.now());
  const [timedOut, setTimedOut] = useState(false);
  const q = useQuery({
    queryKey: ['partner', 'payments', 'detail', uid],
    queryFn: () => paymentsApi.getMyPayment(uid),
    refetchInterval: (query) => {
      const s = query.state.data?.status;
      if (s === 'PAID' || s === 'FAILED' || s === 'EXPIRED') return false;
      if (Date.now() - started.current > MAX_WAIT_MS) return false;
      return 3000;
    },
  });
  const status = q.data?.status;
  const done = status === 'PAID' || status === 'FAILED' || status === 'EXPIRED';
  useEffect(() => {
    if (status === 'PAID') qc.invalidateQueries({ queryKey: ['partner', 'wallet'] });
  }, [status, qc]);
  useEffect(() => {
    if (done) return undefined;
    const t = setTimeout(() => setTimedOut(true), MAX_WAIT_MS);
    return () => clearTimeout(t);
  }, [done]);

  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const p = q.data;

  let icon;
  let title;
  let text;
  if (status === 'PAID') {
    icon = <CheckCircle2 className="h-16 w-16 text-green-600" aria-hidden />;
    title = 'Payment successful';
    text = `${formatMoney(p.creditAmount)} has been added to your wallet.`;
  } else if (status === 'FAILED' || status === 'EXPIRED') {
    icon = <XCircle className="h-16 w-16 text-red-600" aria-hidden />;
    title = status === 'EXPIRED' ? 'Payment expired' : 'Payment not completed';
    text = p.failureReason || 'No money was added. If money was taken from your bank, it will be returned automatically by your bank.';
  } else {
    icon = timedOut ? <Clock className="h-16 w-16 text-amber-500" aria-hidden /> : <Spinner size="lg" />;
    title = timedOut ? 'Still confirming your payment' : 'Confirming your payment…';
    text = timedOut
      ? 'This can take a few minutes. We will add the money as soon as the bank confirms. You can safely leave this page.'
      : 'Please wait, do not close this page.';
  }

  return (
    <>
      <PageHeader title="Payment status" backTo="/partner/wallet?tab=payments" backLabel="Wallet" />
      <Card className="mx-auto max-w-lg">
        <div className="flex flex-col items-center py-4 text-center" role="status" aria-live="polite">
          {icon}
          <h2 className="mt-4 text-xl font-semibold text-ink">{title}</h2>
          <p className="mt-2 text-sm text-muted">{text}</p>
        </div>
        <KeyValue
          className="mt-4 border-t border-line pt-4"
          items={[
            { label: 'Paid amount', value: formatMoney(p.totalAmount) },
            { label: 'Wallet credit', value: formatMoney(p.creditAmount) },
            { label: 'Order', value: p.uid },
            { label: 'Payment ID', value: p.gatewayPaymentId, hidden: !p.gatewayPaymentId },
            { label: 'Date', value: formatDateTime(p.paidAt || p.createdAt) },
            { label: 'Invoice', value: p.invoice?.invoiceNo, hidden: !p.invoice },
          ]}
        />
        <div className="mt-6 grid gap-2 sm:grid-cols-2">
          {status === 'PAID' ? (
            <>
              <Button to="/partner" variant="secondary">Go to home</Button>
              <Button to="/partner/send">Send a message</Button>
            </>
          ) : status === 'FAILED' || status === 'EXPIRED' ? (
            <>
              <Button to="/partner/wallet" variant="secondary">Back to wallet</Button>
              <Button to="/partner/wallet/add">Try again</Button>
            </>
          ) : (
            <>
              <Button variant="secondary" onClick={() => q.refetch()} loading={q.isFetching}>Check again</Button>
              <Button to="/partner/wallet">Back to wallet</Button>
            </>
          )}
        </div>
        {status === 'PAID' && p.invoice && (
          <Button className="mt-2" fullWidth variant="link" onClick={() => paymentsApi.downloadMyInvoice(p.invoice.id, p.invoice.invoiceNo)}>
            Download invoice
          </Button>
        )}
      </Card>
    </>
  );
}
