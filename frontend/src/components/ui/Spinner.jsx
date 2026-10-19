import { cn } from '../../lib/cn';

export default function Spinner({ size = 'md', className, label = 'Loading' }) {
  const sizes = { sm: 'h-4 w-4 border-2', md: 'h-6 w-6 border-2', lg: 'h-10 w-10 border-[3px]' };
  return (
    <span role="status" aria-label={label} className={cn('inline-block animate-spin rounded-full border-current border-r-transparent text-brand-600', sizes[size], className)} />
  );
}

export function FullPageLoader({ label = 'Loading…' }) {
  return (
    <div className="flex min-h-[100dvh] flex-col items-center justify-center gap-3 bg-canvas text-muted">
      <Spinner size="lg" />
      <p className="text-sm">{label}</p>
    </div>
  );
}

export function PageLoader() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner size="lg" />
    </div>
  );
}
