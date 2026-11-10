import { CheckCircle2, CircleAlert, XCircle } from 'lucide-react';
import { formatDateTime } from '../../../lib/format';

const LOOK = {
  VALID: { cls: 'border-green-300 bg-green-50 text-green-900', Icon: CheckCircle2 },
  ALREADY_REDEEMED: { cls: 'border-amber-300 bg-amber-50 text-amber-900', Icon: CircleAlert },
  NOT_PAID: { cls: 'border-amber-300 bg-amber-50 text-amber-900', Icon: CircleAlert },
  INVALID: { cls: 'border-red-300 bg-red-50 text-red-900', Icon: XCircle },
};

/** Result of a QR pass check (public or partner scanner). */
export default function PassStatusCard({ result, redeemedNow }) {
  const { cls, Icon } = LOOK[result.status] || LOOK.INVALID;
  return (
    <div className={`rounded-2xl border-2 p-5 ${cls}`} role="status">
      <div className="flex items-center gap-3">
        <Icon className="h-9 w-9 shrink-0" aria-hidden />
        <p className="text-xl font-extrabold">{redeemedNow ? 'Entry allowed — pass marked as used' : result.message}</p>
      </div>
      {result.passNo && (
        <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="opacity-70">Pass</dt><dd className="font-semibold">{result.packageName}</dd>
          <dt className="opacity-70">Pass ID</dt><dd className="font-mono">{result.passNo}</dd>
          {result.name && <><dt className="opacity-70">Name</dt><dd>{result.name}</dd></>}
          {result.mobile && <><dt className="opacity-70">Mobile</dt><dd>{result.mobile}</dd></>}
          {result.formTitle && <><dt className="opacity-70">Event</dt><dd>{result.formTitle}</dd></>}
          {result.redeemedAt && <><dt className="opacity-70">Used at</dt><dd>{formatDateTime(result.redeemedAt)}</dd></>}
        </dl>
      )}
    </div>
  );
}
