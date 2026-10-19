import { formatDate, formatDateTime, formatRelative } from '../../lib/format';

/** Displays an ISO instant in IST. mode: datetime | date | relative */
export default function DateTime({ value, mode = 'datetime', className }) {
  if (!value) return <span className={className}>—</span>;
  const text = mode === 'date' ? formatDate(value) : mode === 'relative' ? formatRelative(value) : formatDateTime(value);
  return (
    <time dateTime={String(value)} title={formatDateTime(value) + ' IST'} className={className}>
      {text}
    </time>
  );
}
