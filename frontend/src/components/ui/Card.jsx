import { cn } from '../../lib/cn';

export default function Card({ title, subtitle, actions, children, className, bodyClassName, padded = true }) {
  return (
    <section className={cn('card', className)}>
      {(title || actions) && (
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-4 py-3 sm:px-5">
          <div className="min-w-0">
            {title && <h3 className="text-base font-semibold text-ink">{title}</h3>}
            {subtitle && <p className="text-sm text-muted">{subtitle}</p>}
          </div>
          {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={cn(padded && 'p-4 sm:p-5', bodyClassName)}>{children}</div>
    </section>
  );
}
