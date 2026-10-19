import { cn } from '../../lib/cn';

/** Wraps filters: stacked on phones, one wrapping row on larger screens (children share the width). */
export default function FilterBar({ children, className }) {
  return (
    <div className={cn('mb-4 grid grid-cols-1 gap-3 sm:flex sm:flex-wrap sm:items-end sm:[&>*]:min-w-[180px] sm:[&>*]:flex-1', className)}>
      {children}
    </div>
  );
}
