import { AlertOctagon, RefreshCw } from 'lucide-react';
import { getErrorMessage } from '../../api/axios';
import { cn } from '../../lib/cn';
import Button from './Button';

/** Shown when a query fails; offers a retry. */
export default function ErrorState({ error, onRetry, title = 'Could not load this', className }) {
  return (
    <div className={cn('card flex flex-col items-center justify-center px-4 py-10 text-center', className)} role="alert">
      <span className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600 dark:bg-red-500/15 dark:text-red-300">
        <AlertOctagon className="h-6 w-6" aria-hidden />
      </span>
      <h3 className="text-base font-semibold text-ink">{title}</h3>
      <p className="mt-1 max-w-sm text-sm text-muted">{getErrorMessage(error)}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-4" leftIcon={RefreshCw} onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
