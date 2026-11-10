import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getBranding } from '../../api/endpoints/auth';
import { APP_NAME } from '../../lib/constants';

export default function Logo({ to = '/', compact = false }) {
  const { data: brand } = useQuery({ queryKey: ['public', 'branding'], queryFn: getBranding, staleTime: Infinity, retry: false });
  const logoSrc = brand?.logoUrl
    ? `${import.meta.env.VITE_API_BASE_URL || '/api'}${brand.logoUrl.replace(/^\/api/, '')}`
    : '/favicon.svg';
  const companyName = brand?.companyName || 'CB WhatsApp Gateway';
  const nameParts = companyName.split(' ');
  const firstLine = nameParts.slice(0, Math.ceil(nameParts.length / 2)).join(' ');
  const secondLine = nameParts.slice(Math.ceil(nameParts.length / 2)).join(' ');

  return (
    <Link to={to} className="flex min-h-tap items-center gap-2" aria-label={`${APP_NAME} home`}>
      <img src={logoSrc} alt="" className="h-8 w-8 shrink-0 rounded object-contain" />
      {!compact && (
        <span className="leading-tight">
          <span className="block text-sm font-bold text-ink">{firstLine || 'CB WhatsApp'}</span>
          <span className="block text-[11px] text-muted">{secondLine || 'Gateway'}</span>
        </span>
      )}
    </Link>
  );
}
