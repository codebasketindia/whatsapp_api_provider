import { MessageTicks } from '../ui/WhatsAppPreview';
import { humanize } from '../../lib/format';

/** WhatsApp-style ticks with the status word (for message lists). */
export default function StatusTicks({ status, showLabel = true }) {
  return (
    <span className="inline-flex items-center gap-1 text-sm">
      <MessageTicks status={status} />
      {showLabel && <span className="text-muted">{humanize(status)}</span>}
    </span>
  );
}
