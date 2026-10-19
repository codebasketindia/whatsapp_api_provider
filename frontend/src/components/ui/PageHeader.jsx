import { ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import { usePageTitle } from '../../hooks/usePageTitle';

/** Page title row with optional back link and actions (wrap under title on phones). */
export default function PageHeader({ title, subtitle, actions, backTo, backLabel = 'Back', documentTitle }) {
  usePageTitle(documentTitle || (typeof title === 'string' ? title : undefined));
  return (
    <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {backTo && (
          <Link to={backTo} className="mb-1 inline-flex min-h-[36px] items-center gap-1 text-sm text-muted hover:text-ink">
            <ArrowLeft className="h-4 w-4" aria-hidden />
            {backLabel}
          </Link>
        )}
        <h1 className="truncate text-xl font-semibold text-ink sm:text-2xl">{title}</h1>
        {subtitle && <p className="mt-0.5 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
