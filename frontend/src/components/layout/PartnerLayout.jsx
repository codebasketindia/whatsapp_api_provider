import { Suspense } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { cn } from '../../lib/cn';
import { PARTNER_BOTTOM_NAV, PARTNER_NAV } from './navConfig';
import SidebarNav from './SidebarNav';
import Logo from './Logo';
import ThemeToggle from './ThemeToggle';
import NotificationBell from './NotificationBell';
import ProfileMenu from './ProfileMenu';
import ImpersonationBanner from './ImpersonationBanner';
import LowBalanceBanner from './LowBalanceBanner';
import Button from '../ui/Button';
import { PageLoader } from '../ui/Spinner';

/** Routes where the sticky "Add money" button would get in the way. */
const HIDE_ADD_MONEY = ['/partner/wallet/add', '/partner/inbox/', '/partner/send', '/partner/campaigns/new'];

export default function PartnerLayout() {
  const location = useLocation();
  const showAddMoney = !HIDE_ADD_MONEY.some((p) => location.pathname.startsWith(p));
  const inChat = /^\/partner\/inbox\/[^/]+\/[^/]+/.test(location.pathname);
  return (
    <div className="min-h-[100dvh] bg-canvas">
      <ImpersonationBanner />
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-line bg-surface md:flex">
        <div className="flex h-16 items-center border-b border-line px-4">
          <Logo to="/partner" />
        </div>
        <div className="px-3 pt-4">
          <Button to="/partner/wallet/add" leftIcon={Plus} fullWidth>
            Add money
          </Button>
        </div>
        <div className="flex-1 overflow-y-auto px-3 py-4">
          <SidebarNav sections={PARTNER_NAV} />
        </div>
      </aside>

      <div className="flex min-h-[100dvh] min-w-0 flex-col md:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-2 border-b border-line bg-surface/95 px-3 backdrop-blur sm:px-4">
          <div className="md:hidden">
            <Logo to="/partner" />
          </div>
          <div className="ml-auto flex items-center gap-1">
            <ThemeToggle />
            <NotificationBell allPath="/partner/notifications" />
            <ProfileMenu profilePath="/partner/profile" />
          </div>
        </header>
        <LowBalanceBanner />
        <main className={cn('mx-auto w-full min-w-0 max-w-6xl flex-1 px-4 py-5 sm:px-6 lg:py-8', !inChat && 'pb-28 md:pb-8')}>
          <Suspense fallback={<PageLoader />}>
            <Outlet />
          </Suspense>
        </main>
      </div>

      {/* Sticky Add money (phones) */}
      {showAddMoney && !inChat && (
        <div className="fixed bottom-[72px] right-4 z-30 md:hidden">
          <Button to="/partner/wallet/add" leftIcon={Plus} className="rounded-full shadow-lg" size="md">
            Add money
          </Button>
        </div>
      )}

      {/* Bottom navigation (phones) */}
      {!inChat && (
        <nav className="safe-bottom fixed inset-x-0 bottom-0 z-30 border-t border-line bg-surface md:hidden" aria-label="Main">
          <ul className="grid grid-cols-5">
            {PARTNER_BOTTOM_NAV.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    cn(
                      'flex min-h-[60px] flex-col items-center justify-center gap-0.5 text-[11px] font-medium',
                      isActive ? 'text-brand-700 dark:text-brand-300' : 'text-muted',
                    )
                  }
                >
                  <item.icon className="h-6 w-6" aria-hidden />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </div>
  );
}
