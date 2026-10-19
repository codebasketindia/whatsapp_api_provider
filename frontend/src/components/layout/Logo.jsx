import { Link } from 'react-router-dom';
import { APP_NAME } from '../../lib/constants';

export default function Logo({ to = '/', compact = false }) {
  return (
    <Link to={to} className="flex min-h-tap items-center gap-2" aria-label={`${APP_NAME} home`}>
      <img src="/favicon.svg" alt="" className="h-8 w-8 shrink-0" />
      {!compact && (
        <span className="leading-tight">
          <span className="block text-sm font-bold text-ink">CB WhatsApp</span>
          <span className="block text-[11px] text-muted">Gateway</span>
        </span>
      )}
    </Link>
  );
}
