import { Link } from 'react-router-dom';
import { Select } from '../../../components/ui';
import { senderLabel, usableSenders, useMySenders } from './useSenders';

/** Dropdown of the partner's usable senders, with a link to connect one if there are none. */
export default function SenderSelect({ value, onChange, label = 'Send from', error, className, includeAll = false, allLabel = 'All numbers' }) {
  const q = useMySenders();
  const list = includeAll ? q.data || [] : usableSenders(q.data);
  if (!q.isLoading && !includeAll && list.length === 0) {
    return (
      <div className={className}>
        <span className="label-base">{label}</span>
        <p className="rounded-lg border border-dashed border-line p-3 text-sm text-muted">
          No WhatsApp number is ready to send.{' '}
          <Link className="font-medium text-brand-700 underline dark:text-brand-300" to="/partner/senders">
            Go to My numbers
          </Link>
        </p>
      </div>
    );
  }
  return (
    <Select
      label={label}
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value ? Number(e.target.value) : '')}
      options={list.map((s) => ({ value: s.id, label: senderLabel(s) }))}
      placeholder={includeAll ? allLabel : q.isLoading ? 'Loading…' : 'Choose a number'}
      error={error}
      className={className}
      disabled={q.isLoading}
    />
  );
}
