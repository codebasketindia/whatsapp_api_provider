import { Link, useNavigate } from 'react-router-dom';
import { BarChart3, Bell, ChevronRight, Code2, Contact, FileText, LifeBuoy, LogOut, Megaphone, Smartphone, UserCircle } from 'lucide-react';
import { useAuth } from '../../../../auth/useAuth';
import { Avatar, PageHeader } from '../../../../components/ui';
import ThemeToggle from '../../../../components/layout/ThemeToggle';

const GROUPS = [
  {
    title: 'WhatsApp',
    items: [
      { to: '/partner/senders', label: 'My numbers', icon: Smartphone, text: 'Connect and manage WhatsApp numbers' },
      { to: '/partner/templates', label: 'Templates', icon: FileText, text: 'Create and track message templates' },
      { to: '/partner/campaigns', label: 'Campaigns', icon: Megaphone, text: 'Send to many people at once' },
      { to: '/partner/contacts', label: 'Contacts', icon: Contact, text: 'Contacts, groups and opt-outs' },
      { to: '/partner/reports', label: 'Reports', icon: BarChart3, text: 'Message logs and usage' },
    ],
  },
  {
    title: 'Account',
    items: [
      { to: '/partner/developer', label: 'Developer API', icon: Code2, text: 'API keys, webhook and docs' },
      { to: '/partner/notifications', label: 'Notifications', icon: Bell, text: 'Alerts and updates' },
      { to: '/partner/profile', label: 'My profile', icon: UserCircle, text: 'Details and password' },
      { to: '/partner/support', label: 'Help & support', icon: LifeBuoy, text: 'Contact us and FAQs' },
    ],
  },
];

export default function MorePage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  return (
    <>
      <PageHeader title="More" />
      <div className="card mb-5 flex items-center gap-3 p-4">
        <Avatar name={user?.name} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold text-ink">{user?.partner?.businessName || user?.name}</p>
          <p className="truncate text-sm text-muted">{user?.email}</p>
        </div>
        <ThemeToggle />
      </div>
      <div className="space-y-5">
        {GROUPS.map((g) => (
          <section key={g.title}>
            <h2 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wider text-muted">{g.title}</h2>
            <ul className="card divide-y divide-line">
              {g.items.map((i) => (
                <li key={i.to}>
                  <Link to={i.to} className="flex min-h-[60px] items-center gap-3 px-4 py-3 hover:bg-surface-2">
                    <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
                      <i.icon className="h-5 w-5" aria-hidden />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium text-ink">{i.label}</span>
                      <span className="block truncate text-xs text-muted">{i.text}</span>
                    </span>
                    <ChevronRight className="h-5 w-5 text-muted" aria-hidden />
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}
        <button
          type="button"
          onClick={async () => {
            await logout();
            navigate('/login', { replace: true });
          }}
          className="card flex min-h-[56px] w-full items-center justify-center gap-2 text-sm font-medium text-red-600 hover:bg-surface-2"
        >
          <LogOut className="h-5 w-5" /> Log out
        </button>
      </div>
    </>
  );
}
