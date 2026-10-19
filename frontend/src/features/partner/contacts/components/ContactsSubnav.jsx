import { NavLink } from 'react-router-dom';
import { cn } from '../../../../lib/cn';

const LINKS = [
  { to: '/partner/contacts', label: 'Contacts', end: true },
  { to: '/partner/contacts/groups', label: 'Groups' },
  { to: '/partner/contacts/import', label: 'Import' },
  { to: '/partner/contacts/opt-outs', label: 'Opt-outs' },
];

/** Secondary navigation shared by the contacts screens. */
export default function ContactsSubnav() {
  return (
    <nav className="-mx-4 mb-4 overflow-x-auto border-b border-line px-4 scrollbar-none sm:mx-0 sm:px-0" aria-label="Contacts sections">
      <div className="flex min-w-max gap-1">
        {LINKS.map((l) => (
          <NavLink
            key={l.to}
            to={l.to}
            end={l.end}
            className={({ isActive }) =>
              cn(
                'inline-flex min-h-tap items-center border-b-2 px-3 text-sm font-medium',
                isActive ? 'border-brand-600 text-brand-700 dark:text-brand-300' : 'border-transparent text-muted hover:text-ink',
              )
            }
          >
            {l.label}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
