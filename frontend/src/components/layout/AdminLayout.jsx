import { Suspense, useMemo, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { ChevronsLeft, ChevronsRight, Menu, Search, X } from 'lucide-react';
import { useAuth } from '../../auth/useAuth';
import { local } from '../../lib/storage';
import { cn } from '../../lib/cn';
import { ADMIN_NAV } from './navConfig';
import SidebarNav from './SidebarNav';
import Logo from './Logo';
import ThemeToggle from './ThemeToggle';
import NotificationBell from './NotificationBell';
import ProfileMenu from './ProfileMenu';
import { PageLoader } from '../ui/Spinner';
import { useLockBody } from '../ui/Modal';

const COLLAPSE_KEY = 'cbwa.adminSidebarCollapsed';

/** Top-bar search jumps to the partners list (searches name, email, code, mobile). */
function TopSearch() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  return (
    <form
      role="search"
      className="relative hidden w-full max-w-md md:block"
      onSubmit={(e) => {
        e.preventDefault();
        if (q.trim()) navigate(`/admin/partners?search=${encodeURIComponent(q.trim())}`);
      }}
    >
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
      <input
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search partners by name, email, mobile or code"
        aria-label="Search partners"
        className="field-base pl-9"
      />
    </form>
  );
}

export default function AdminLayout() {
  const { hasPermission } = useAuth();
  const [collapsed, setCollapsed] = useState(() => local.get(COLLAPSE_KEY) === '1');
  const [mobileOpen, setMobileOpen] = useState(false);
  useLockBody(mobileOpen);

  const sections = useMemo(
    () =>
      ADMIN_NAV.map((s) => ({ ...s, items: s.items.filter((i) => !i.permission || hasPermission(i.permission)) })).filter(
        (s) => s.items.length > 0,
      ),
    [hasPermission],
  );

  const toggleCollapse = () => {
    setCollapsed((c) => {
      local.set(COLLAPSE_KEY, c ? '0' : '1');
      return !c;
    });
  };

  return (
    <div className="min-h-[100dvh] bg-canvas">
      {/* Desktop sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-30 hidden flex-col border-r border-line bg-surface transition-[width] md:flex',
          collapsed ? 'w-[72px]' : 'w-64',
        )}
      >
        <div className={cn('flex h-16 items-center border-b border-line', collapsed ? 'justify-center' : 'px-4')}>
          <Logo to="/admin" compact={collapsed} />
        </div>
        <div className="flex-1 overflow-y-auto px-3 py-4">
          <SidebarNav sections={sections} collapsed={collapsed} />
        </div>
        <button
          type="button"
          onClick={toggleCollapse}
          className="flex min-h-tap items-center justify-center gap-2 border-t border-line text-sm text-muted hover:bg-surface-2 hover:text-ink"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <ChevronsRight className="h-5 w-5" /> : (<><ChevronsLeft className="h-5 w-5" /> Collapse</>)}
        </button>
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 md:hidden">
          <div className="absolute inset-0 bg-black/50" onClick={() => setMobileOpen(false)} aria-hidden />
          <aside className="absolute inset-y-0 left-0 flex w-[82%] max-w-[300px] animate-slide-in-left flex-col bg-surface shadow-xl">
            <div className="flex h-16 items-center justify-between border-b border-line px-4">
              <Logo to="/admin" />
              <button type="button" onClick={() => setMobileOpen(false)} className="flex h-11 w-11 items-center justify-center rounded-lg text-muted hover:bg-surface-2" aria-label="Close menu">
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-3 py-4">
              <SidebarNav sections={sections} onNavigate={() => setMobileOpen(false)} />
            </div>
          </aside>
        </div>
      )}

      <div className={cn('flex min-h-[100dvh] min-w-0 flex-col transition-[padding]', collapsed ? 'md:pl-[72px]' : 'md:pl-64')}>
        <header className="sticky top-0 z-20 flex h-16 items-center gap-2 border-b border-line bg-surface/95 px-2 backdrop-blur sm:px-4">
          <button type="button" onClick={() => setMobileOpen(true)} className="flex h-11 w-11 items-center justify-center rounded-lg text-ink hover:bg-surface-2 md:hidden" aria-label="Open menu">
            <Menu className="h-5 w-5" />
          </button>
          <div className="md:hidden">
            <Logo to="/admin" compact />
          </div>
          <TopSearch />
          <div className="ml-auto flex items-center gap-1">
            <ThemeToggle />
            <NotificationBell allPath="/admin/notifications" />
            <ProfileMenu profilePath="/admin/profile" />
          </div>
        </header>
        <main className="mx-auto w-full min-w-0 max-w-7xl flex-1 px-4 py-5 sm:px-6 lg:py-8">
          <Suspense fallback={<PageLoader />}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  );
}
