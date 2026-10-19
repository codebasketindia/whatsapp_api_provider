import { cn } from '../../lib/cn';

export default function Skeleton({ className }) {
  return <span aria-hidden className={cn('block animate-pulse rounded-md bg-line/70', className)} />;
}

export function SkeletonText({ lines = 3 }) {
  return (
    <div className="space-y-2" aria-hidden>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className={cn('h-4', i === lines - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  );
}
