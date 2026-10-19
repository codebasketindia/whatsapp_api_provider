import { cn } from '../../lib/cn';
import { formatMoney, toNumber } from '../../lib/format';

/** ₹ amount; `signed` colours credits green and debits red with +/−. */
export default function Money({ value, precise, signed, negative, className }) {
  const n = toNumber(value);
  if (!signed) return <span className={cn('tabular-nums', className)}>{formatMoney(value, { precise })}</span>;
  const isNeg = negative ?? n < 0;
  return (
    <span className={cn('tabular-nums font-medium', isNeg ? 'text-red-600 dark:text-red-400' : 'text-green-700 dark:text-green-400', className)}>
      {isNeg ? '−' : '+'}
      {formatMoney(Math.abs(n), { precise })}
    </span>
  );
}
