import { useQuery } from '@tanstack/react-query';
import { getBranding } from '../../../api/endpoints/auth';
import { usePageTitle } from '../../../hooks/usePageTitle';
import ThemeToggle from '../../../components/layout/ThemeToggle';

/** Centered card layout for login / password screens. */
export default function AuthShell({ title, subtitle, children, footer }) {
  usePageTitle(title);
  const { data: brand } = useQuery({ queryKey: ['public', 'branding'], queryFn: getBranding, staleTime: Infinity, retry: false });
  return (
    <div className="flex min-h-[100dvh] flex-col bg-canvas">
      <div className="flex justify-end p-2">
        <ThemeToggle />
      </div>
      <div className="flex flex-1 items-start justify-center px-4 pb-10 sm:items-center">
        <div className="w-full max-w-md">
          <div className="mb-6 flex flex-col items-center text-center">
            <img src={brand?.logoUrl || '/favicon.svg'} alt="" className="mb-3 h-14 w-14 rounded-xl object-contain" />
            <p className="text-sm font-medium text-muted">{brand?.companyName || 'CB WhatsApp Gateway'}</p>
            <h1 className="mt-2 text-2xl font-semibold text-ink">{title}</h1>
            {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
          </div>
          <div className="card p-5 sm:p-6">{children}</div>
          {footer && <div className="mt-4 text-center text-sm text-muted">{footer}</div>}
        </div>
      </div>
    </div>
  );
}
